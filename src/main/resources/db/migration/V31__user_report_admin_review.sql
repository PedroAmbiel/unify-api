-- ============================================================================
-- Semana 06 (B) — triagem administrativa de denúncias.
--
--  admin_note     : nota do moderador (obrigatória para RESOLVED/DISMISSED,
--                   validado no serviço).
--  reviewed_at    : última mudança de status feita pelo backoffice.
--  fk_reviewed_by : administrador que fez essa mudança.
--
-- resolved_at (V12) passa a ser preenchido quando a denúncia é encerrada
-- (RESOLVED ou DISMISSED).
-- ============================================================================

alter table user_reports
    add column if not exists admin_note varchar(1000),
    add column if not exists reviewed_at timestamp(6) with time zone,
    add column if not exists fk_reviewed_by uuid;

alter table user_reports
    drop constraint if exists fk_user_reports_reviewed_by;

alter table user_reports
    add constraint fk_user_reports_reviewed_by
        foreign key (fk_reviewed_by) references users (id);

-- Fila de moderação: filtra por status e ordena por data.
create index if not exists idx_user_reports_status_created
    on user_reports (status, created_at desc);

-- Histórico do denunciado no detalhe da denúncia.
create index if not exists idx_user_reports_reported_created
    on user_reports (fk_reported_user, created_at desc);
