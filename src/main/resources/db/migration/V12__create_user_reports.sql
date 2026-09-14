-- ============================================================================
-- Semana 03 — denúncias de perfil e de publicação de comunidade.
--
-- Decisão de modelagem: um único conceito `user_reports` com `fk_reported_post`
-- opcional (denúncia de post) em vez de uma tabela `content_reports` separada.
-- Motivo/status/resolução são idênticos nos dois casos e o backoffice da
-- semana 06 lista tudo de uma tabela só, filtrando por `fk_reported_post`.
-- `fk_reported_user` é sempre preenchido (autor do post, na denúncia de post).
-- ============================================================================

create table if not exists user_reports (
    id               uuid                        not null,
    fk_reporter      uuid                        not null,
    fk_reported_user uuid                        not null,
    fk_reported_post uuid,
    reason           varchar(40)                 not null,
    description      text,
    status           varchar(20)                 not null default 'OPEN',
    created_at       timestamp(6) with time zone not null,
    resolved_at      timestamp(6) with time zone,
    constraint pk_user_reports primary key (id),
    constraint fk_user_reports_reporter
        foreign key (fk_reporter) references users (id),
    constraint fk_user_reports_reported_user
        foreign key (fk_reported_user) references users (id),
    constraint fk_user_reports_reported_post
        -- community_posts vira `posts` na V13; a FK acompanha a renomeação.
        foreign key (fk_reported_post) references community_posts (id) on delete set null,
    constraint ck_user_reports_no_self_report check (fk_reporter <> fk_reported_user)
);

-- Evita denúncia duplicada ABERTA do mesmo denunciante para o mesmo alvo.
-- COALESCE trata "denúncia de perfil" (post nulo) como um valor fixo, já que
-- NULL nunca é igual a NULL em índices únicos do Postgres.
create unique index if not exists ux_user_reports_open_unique
    on user_reports (
        fk_reporter,
        fk_reported_user,
        coalesce(fk_reported_post, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    where status = 'OPEN';

create index if not exists ix_user_reports_reported_user on user_reports (fk_reported_user);
create index if not exists ix_user_reports_status        on user_reports (status);
create index if not exists ix_user_reports_reported_post on user_reports (fk_reported_post);
