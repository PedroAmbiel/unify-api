-- ============================================================================
-- Áudio de apresentação do perfil (frente D, semana 04).
-- Uma linha por perfil (unique fk_user_profile). Substituir = apagar a linha
-- antiga e inserir outra com id novo, para que a URL /audio/{audioId} seja
-- imutável e cacheável (mesmo raciocínio de user_profile_images).
-- Bytes em oid (pg_largeobject), mesmo padrão de user_profile_images.oid e
-- chat_messages.media_oid; triggers lo_unlink idênticos aos da V11.
-- O check de duração é o teto de produto: unify.profile.audio.max-duration-seconds
-- nunca pode passar de 60 sem uma migration que altere este check.
-- ============================================================================

create table if not exists user_profile_audios (
    id                 uuid                        not null,
    fk_user_profile    uuid                        not null,
    oid                oid                         not null,
    content_type       varchar(100)                not null,
    size_bytes         bigint                      not null,
    duration_seconds   integer                     not null,
    created_at         timestamp(6) with time zone not null,
    constraint pk_user_profile_audios primary key (id),
    constraint uq_user_profile_audios_user_profile unique (fk_user_profile),
    constraint fk_user_profile_audios_user_profile
        foreign key (fk_user_profile) references user_profiles (id) on delete cascade,
    constraint ck_user_profile_audios_duration check (duration_seconds between 1 and 60),
    constraint ck_user_profile_audios_size check (size_bytes > 0)
);

create or replace function unlink_user_profile_audio_on_update()
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

create or replace function delete_user_profile_audio()
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

drop trigger if exists trg_user_profile_audios_update_large_object on user_profile_audios;

create trigger trg_user_profile_audios_update_large_object
    before update of oid on user_profile_audios
    for each row execute function unlink_user_profile_audio_on_update();

drop trigger if exists trg_user_profile_audios_delete_large_object on user_profile_audios;

create trigger trg_user_profile_audios_delete_large_object
    before delete on user_profile_audios
    for each row execute function delete_user_profile_audio();
