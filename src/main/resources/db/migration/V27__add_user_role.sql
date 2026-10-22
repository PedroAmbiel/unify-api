-- ============================================================================
-- Semana 06 (A) — papel do usuário.
--
--  role : USER | ADMIN. Cardinalidade fechada de propósito (enum UserRole):
--         um usuário tem exatamente um papel e ADMIN acumula as permissões de
--         USER no JWT (groups = {"user", "admin"}). O default backfilla todas
--         as linhas existentes como USER.
--
-- Não existe endpoint que altere esta coluna. A promoção a ADMIN é feita por
-- configuração (ADMIN_BOOTSTRAP_EMAILS -> AdminBootstrapService) ou por SQL
-- direto no banco.
-- ============================================================================

alter table users
    add column if not exists role varchar(20) not null default 'USER';

alter table users
    drop constraint if exists ck_users_role;

alter table users
    add constraint ck_users_role check (role in ('USER', 'ADMIN'));

-- A consulta útil é sempre "quais são os admins": índice parcial, quase sem custo.
create index if not exists idx_users_role_admin
    on users (role)
    where role = 'ADMIN';
