-- ============================================================================
-- Semana 04 — privacidade do usuário.
--
-- Criação preguiçosa: a linha só nasce quando o usuário abre/salva a tela de
-- privacidade. Quem não tem linha vale como os defaults abaixo, por isso as
-- consultas de discovery/feed usam `not exists (...)`/`left join + coalesce`,
-- nunca inner join (que excluiria todo mundo por padrão).
-- ============================================================================

create table if not exists user_privacy_settings (
    id              uuid                        not null,
    fk_user         uuid                        not null,
    discoverable    boolean                     not null default true,
    feed_visibility varchar(20)                 not null default 'PUBLIC',
    show_distance   boolean                     not null default true,
    show_age        boolean                     not null default true,
    updated_at      timestamp(6) with time zone not null default now(),
    constraint pk_user_privacy_settings primary key (id),
    constraint uq_user_privacy_settings_user unique (fk_user),
    constraint fk_user_privacy_settings_user
        foreign key (fk_user) references users (id) on delete cascade,
    constraint ck_user_privacy_settings_feed_visibility
        check (feed_visibility in ('PUBLIC', 'FOLLOWERS_ONLY'))
);
