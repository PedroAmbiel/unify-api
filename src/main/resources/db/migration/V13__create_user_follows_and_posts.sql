-- ============================================================================
-- Semana 03 — seguir usuários e publicações pessoais (feed por follow).
--
-- DECISÃO (2026-09-14): posts pessoais NÃO ganham tabela própria. A tabela
-- `community_posts` passa a se chamar `posts` (e `community_post_likes` /
-- `community_post_comments` viram `post_likes` / `post_comments`), porque a
-- partir daqui ela guarda posts de comunidade E posts pessoais, diferenciados
-- por `origin` ('COMMUNITY' | 'PERSONAL'; `fk_community` nulo quando pessoal).
-- Likes, comentários e denúncias (user_reports.fk_reported_post) servem aos
-- dois tipos sem duplicar tabelas. `active = false` é o soft delete dos posts
-- pessoais; posts de comunidade continuam sendo apagados fisicamente.
-- ============================================================================

-- ---------------------------------------------------------- renomeação
alter table community_posts         rename to posts;
alter table community_post_likes    rename to post_likes;
alter table community_post_comments rename to post_comments;

alter table posts         rename constraint community_posts_pkey         to posts_pkey;
alter table post_likes    rename constraint community_post_likes_pkey    to post_likes_pkey;
alter table post_comments rename constraint community_post_comments_pkey to post_comments_pkey;

alter table posts rename constraint fk_community_posts_user      to fk_posts_user;
alter table posts rename constraint fk_community_posts_community to fk_posts_community;

alter table post_likes rename constraint fk_community_post_likes_post      to fk_post_likes_post;
alter table post_likes rename constraint fk_community_post_likes_user      to fk_post_likes_user;
alter table post_likes rename constraint uq_community_post_like_post_user  to uq_post_likes_post_user;

alter table post_comments rename constraint fk_community_post_comments_post to fk_post_comments_post;
alter table post_comments rename constraint fk_community_post_comments_user to fk_post_comments_user;

alter index if exists idx_community_posts_feed          rename to idx_posts_feed;
alter index if exists idx_community_post_comments_post  rename to idx_post_comments_post;
alter index if exists idx_community_post_likes_post     rename to idx_post_likes_post;

-- Triggers de lo_unlink (V11) recriados com o novo nome; as funções antigas saem.
drop trigger if exists trg_community_posts_update_large_object on posts;
drop trigger if exists trg_community_posts_delete_large_object on posts;
drop function if exists unlink_community_post_media_on_update();
drop function if exists delete_community_post_media();

create or replace function unlink_post_media_on_update()
returns trigger as $$
begin
    if old.media_oid is not null
       and (new.media_oid is null or new.media_oid <> old.media_oid) then
        perform lo_unlink(old.media_oid);
    end if;
    return new;
exception
    when undefined_object then
        return new;
end;
$$ language plpgsql;

create or replace function delete_post_media()
returns trigger as $$
begin
    if old.media_oid is not null then
        perform lo_unlink(old.media_oid);
    end if;
    return old;
exception
    when undefined_object then
        return old;
end;
$$ language plpgsql;

create trigger trg_posts_update_large_object
    before update of media_oid on posts
    for each row execute function unlink_post_media_on_update();

create trigger trg_posts_delete_large_object
    before delete on posts
    for each row execute function delete_post_media();

-- ---------------------------------------------------------- origem / soft delete
alter table posts
    alter column fk_community drop not null;

alter table posts
    add column if not exists origin varchar(20) not null default 'COMMUNITY';

alter table posts
    add column if not exists active boolean not null default true;

alter table posts
    drop constraint if exists ck_posts_origin_community;

alter table posts
    add constraint ck_posts_origin_community
        check (
            (origin = 'COMMUNITY' and fk_community is not null)
            or (origin = 'PERSONAL' and fk_community is null)
        );

-- Feed pessoal: "posts de quem eu sigo + os meus", mais recentes primeiro.
create index if not exists ix_posts_personal_author_created
    on posts (fk_user, created_at desc, id desc)
    where origin = 'PERSONAL' and active = true;

-- ---------------------------------------------------------------- follows
-- follower/followed referenciam user_profiles (entidade "social" do usuário).
-- Seguir é idempotente na API; a UNIQUE é defesa contra toques concorrentes.
create table if not exists user_follows (
    id          uuid                        not null,
    fk_follower uuid                        not null,
    fk_followed uuid                        not null,
    created_at  timestamp(6) with time zone not null,
    constraint pk_user_follows primary key (id),
    constraint fk_user_follows_follower
        foreign key (fk_follower) references user_profiles (id) on delete cascade,
    constraint fk_user_follows_followed
        foreign key (fk_followed) references user_profiles (id) on delete cascade,
    constraint uq_user_follows_pair unique (fk_follower, fk_followed),
    constraint ck_user_follows_no_self check (fk_follower <> fk_followed)
);

create index if not exists ix_user_follows_follower on user_follows (fk_follower);
create index if not exists ix_user_follows_followed on user_follows (fk_followed);
