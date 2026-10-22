-- ============================================================================
-- Semana 06 — trilha de auditoria de toda ação administrativa.
--
--  action      : BAN_USER, UNBAN_USER, REPORT_STATUS_CHANGE, LOOKUP_CREATE,
--                LOOKUP_UPDATE, LOOKUP_TOGGLE_ACTIVE, LOOKUP_DELETE.
--  target_type : USER | REPORT | LOOKUP.
--  target_id   : uuid do alvo ou "<catálogo>:<id>" (ex.: "genders:7").
-- ============================================================================

create table if not exists admin_audit_log (
    id            uuid                        not null,
    fk_admin_user uuid                        not null,
    action        varchar(60)                 not null,
    target_type   varchar(40)                 not null,
    target_id     varchar(80)                 not null,
    details       varchar(1000),
    ip_address    varchar(64),
    created_at    timestamp(6) with time zone not null,
    constraint pk_admin_audit_log primary key (id),
    constraint fk_admin_audit_log_admin_user
        foreign key (fk_admin_user) references users (id)
);

create index if not exists idx_admin_audit_log_admin
    on admin_audit_log (fk_admin_user, created_at desc);

create index if not exists idx_admin_audit_log_target
    on admin_audit_log (target_type, target_id, created_at desc);

create index if not exists idx_admin_audit_log_created
    on admin_audit_log (created_at desc);
