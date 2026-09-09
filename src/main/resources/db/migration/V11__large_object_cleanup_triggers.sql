-- ============================================================================
-- Large objects órfãos: communities.icon_oid, community_posts.media_oid e
-- user_profile_images.oid apontam para pg_largeobject. Apagar (ou trocar) a
-- linha NÃO remove o large object — sem lo_unlink o banco cresce para sempre.
--
-- Mesmo padrão dos triggers de chat_messages (V9/V10):
--   UPDATE : desvincula o oid ANTIGO quando ele muda ou vira nulo;
--   DELETE : desvincula o oid da linha removida.
-- `undefined_object` é tolerado: o large object já pode ter sido removido.
-- ============================================================================

-- ---------------------------------------------------------------- communities
create or replace function unlink_community_icon_on_update()
returns trigger as $$
begin
    if old.icon_oid is not null
       and (new.icon_oid is null or new.icon_oid <> old.icon_oid) then
        perform lo_unlink(old.icon_oid);
    end if;
    return new;
exception
    when undefined_object then
        return new;
end;
$$ language plpgsql;

create or replace function delete_community_icon()
returns trigger as $$
begin
    if old.icon_oid is not null then
        perform lo_unlink(old.icon_oid);
    end if;
    return old;
exception
    when undefined_object then
        return old;
end;
$$ language plpgsql;

drop trigger if exists trg_communities_update_large_object on communities;

create trigger trg_communities_update_large_object
    before update of icon_oid on communities
    for each row execute function unlink_community_icon_on_update();

drop trigger if exists trg_communities_delete_large_object on communities;

create trigger trg_communities_delete_large_object
    before delete on communities
    for each row execute function delete_community_icon();

-- ------------------------------------------------------------ community_posts
create or replace function unlink_community_post_media_on_update()
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

create or replace function delete_community_post_media()
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

drop trigger if exists trg_community_posts_update_large_object on community_posts;

create trigger trg_community_posts_update_large_object
    before update of media_oid on community_posts
    for each row execute function unlink_community_post_media_on_update();

drop trigger if exists trg_community_posts_delete_large_object on community_posts;

create trigger trg_community_posts_delete_large_object
    before delete on community_posts
    for each row execute function delete_community_post_media();

-- -------------------------------------------------------- user_profile_images
-- oid é NOT NULL aqui: só o caso "trocou de imagem" precisa da guarda de mudança.
create or replace function unlink_user_profile_image_on_update()
returns trigger as $$
begin
    if old.oid is not null and (new.oid is null or new.oid <> old.oid) then
        perform lo_unlink(old.oid);
    end if;
    return new;
exception
    when undefined_object then
        return new;
end;
$$ language plpgsql;

create or replace function delete_user_profile_image()
returns trigger as $$
begin
    if old.oid is not null then
        perform lo_unlink(old.oid);
    end if;
    return old;
exception
    when undefined_object then
        return old;
end;
$$ language plpgsql;

drop trigger if exists trg_user_profile_images_update_large_object on user_profile_images;

create trigger trg_user_profile_images_update_large_object
    before update of oid on user_profile_images
    for each row execute function unlink_user_profile_image_on_update();

drop trigger if exists trg_user_profile_images_delete_large_object on user_profile_images;

create trigger trg_user_profile_images_delete_large_object
    before delete on user_profile_images
    for each row execute function delete_user_profile_image();
