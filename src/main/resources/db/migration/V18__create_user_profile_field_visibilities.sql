-- ============================================================================
-- Semana 04 (extensão 2026-09-28) — visibilidade granular das partes do perfil.
--
-- Uma linha por (usuário, parte) que NÃO é pública. Voltar para PUBLIC apaga a
-- linha: sem linha = público, então quem nunca configurou nada continua
-- mostrando tudo, sem migração de dados.
--   FOLLOWERS_ONLY = escondido para quem não me segue
--   FOLLOWING_ONLY = escondido para quem eu não sigo
--   HIDDEN         = escondido para todos
-- ============================================================================

create table if not exists user_profile_field_visibilities (
    id          uuid                        not null,
    fk_user     uuid                        not null,
    field       varchar(40)                 not null,
    visibility  varchar(20)                 not null,
    updated_at  timestamp(6) with time zone not null default now(),
    constraint pk_user_profile_field_visibilities primary key (id),
    constraint uq_user_profile_field_visibilities_user_field unique (fk_user, field),
    constraint fk_user_profile_field_visibilities_user
        foreign key (fk_user) references users (id) on delete cascade,
    constraint ck_user_profile_field_visibilities_field check (field in (
        'BIO', 'GENDER', 'PRONOUNS', 'DISABILITIES', 'ACCESSIBILITY_NEEDS', 'AUTONOMY_LEVEL',
        'COMMUNICATION_FORMS', 'LIFESTYLE_TYPES', 'LOVE_LANGUAGES', 'ENERGY_LEVEL',
        'INTEREST_TYPES', 'GALLERY', 'PRESENTATION_AUDIO'
    )),
    constraint ck_user_profile_field_visibilities_visibility
        check (visibility in ('FOLLOWERS_ONLY', 'FOLLOWING_ONLY', 'HIDDEN'))
);
