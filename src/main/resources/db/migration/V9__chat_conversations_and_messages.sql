-- ============================================================================
-- Chat 1:1 entre matches mútuos
-- Uma conversa por UserPossibleMatch confirmado. Mídia em coluna oid (mesmo
-- padrão de user_profile_images.oid e community_posts.media_oid).
-- ============================================================================

create table if not exists conversations (
    id                                uuid        not null,
    fk_user_possible_match            uuid        not null,
    fk_participant_one_user_profile   uuid        not null,
    fk_participant_two_user_profile   uuid        not null,
    created_at                        timestamp(6) with time zone not null,
    last_message_at                   timestamp(6) with time zone,
    constraint pk_conversations primary key (id),
    constraint uq_conversations_user_possible_match unique (fk_user_possible_match),
    constraint fk_conversations_user_possible_match
        foreign key (fk_user_possible_match) references user_possible_matches (id) on delete cascade,
    constraint fk_conversations_participant_one_user_profile
        foreign key (fk_participant_one_user_profile) references user_profiles (id),
    constraint fk_conversations_participant_two_user_profile
        foreign key (fk_participant_two_user_profile) references user_profiles (id),
    constraint ck_conversations_distinct_participants
        check (fk_participant_one_user_profile <> fk_participant_two_user_profile)
);

create index if not exists idx_conversations_participant_one
    on conversations (fk_participant_one_user_profile);

create index if not exists idx_conversations_participant_two
    on conversations (fk_participant_two_user_profile);

create index if not exists idx_conversations_last_message_at
    on conversations (last_message_at desc nulls last);

create table if not exists chat_messages (
    id                      uuid          not null,
    fk_conversation         uuid          not null,
    fk_sender_user_profile  uuid          not null,
    type                    varchar(10)   not null,
    body                    varchar(4000),
    media_oid               oid,
    media_content_type      varchar(100),
    media_size_bytes        bigint,
    media_duration_seconds  integer,
    created_at              timestamp(6) with time zone not null,
    read_at                 timestamp(6) with time zone,
    constraint pk_chat_messages primary key (id),
    constraint fk_chat_messages_conversation
        foreign key (fk_conversation) references conversations (id) on delete cascade,
    constraint fk_chat_messages_sender_user_profile
        foreign key (fk_sender_user_profile) references user_profiles (id),
    constraint ck_chat_messages_type
        check (type in ('TEXT', 'IMAGE', 'AUDIO', 'VIDEO')),
    -- TEXT precisa de body; mídia precisa de media_oid + content type
    constraint ck_chat_messages_payload check (
        (type = 'TEXT'  and body is not null and media_oid is null)
        or
        (type <> 'TEXT' and media_oid is not null and media_content_type is not null)
    )
);

create index if not exists idx_chat_messages_conversation_created_at
    on chat_messages (fk_conversation, created_at desc);

create index if not exists idx_chat_messages_sender
    on chat_messages (fk_sender_user_profile);

-- Índice parcial: a contagem de não lidas é a query mais frequente do polling.
create index if not exists idx_chat_messages_unread
    on chat_messages (fk_conversation, fk_sender_user_profile)
    where read_at is null;

-- ============================================================================
-- Limpeza de large objects órfãos
-- Colunas `oid` no PostgreSQL guardam apenas a referência: apagar a linha NÃO
-- apaga o large object em pg_largeobject. Sem isso o banco cresce para sempre.
-- ============================================================================
create or replace function delete_chat_message_large_object()
returns trigger as $$
begin
    if old.media_oid is not null then
        perform lo_unlink(old.media_oid);
    end if;
    return old;
exception
    when undefined_object then
        return old;   -- o large object já foi removido
end;
$$ language plpgsql;

drop trigger if exists trg_chat_messages_delete_large_object on chat_messages;

create trigger trg_chat_messages_delete_large_object
    before delete on chat_messages
    for each row execute function delete_chat_message_large_object();
