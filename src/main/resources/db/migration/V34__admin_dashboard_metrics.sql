-- ============================================================================
-- Semana 07 — dashboard administrativo (métricas agregadas).
--
--  users.created_at : instante do cadastro (série "novos usuários"). Contas
--                     anteriores a esta migration herdam last_updated_at, a
--                     melhor aproximação disponível.
--
-- "Novos matches mútuos" usa user_possible_matches.matched_at, que já existe
-- desde a V23 (o plano previa uma coluna mutual_confirmed_at; não foi criada
-- para não duplicar o mesmo instante).
--
-- Índices: as séries diárias filtram por intervalo de created_at/matched_at.
-- Em %dev/%test (Hibernate drop-and-create) a coluna sai da entidade User com
-- default now() e os índices estão repetidos no import.sql.
-- ============================================================================

alter table users add column if not exists created_at timestamp(6) with time zone;

update users set created_at = last_updated_at where created_at is null;

alter table users alter column created_at set default now();
alter table users alter column created_at set not null;

create index if not exists idx_users_created_at
    on users (created_at);

create index if not exists idx_user_possible_matches_matched_at
    on user_possible_matches (matched_at)
    where matched_at is not null;

create index if not exists idx_chat_messages_created_at
    on chat_messages (created_at);

create index if not exists idx_posts_created_at
    on posts (created_at);

create index if not exists idx_user_reports_created_at
    on user_reports (created_at);
