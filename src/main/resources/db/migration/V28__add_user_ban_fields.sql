-- ============================================================================
-- Semana 06 (B) — banimento de usuários.
--
--  banned     : conta suspensa pelo backoffice. Não loga, não renova sessão,
--               access token já emitido é recusado (BannedUserFilter) e o
--               perfil/posts/comentários somem para os outros usuários.
--  banned_at  : instante do banimento.
--  ban_reason : motivo registrado pelo administrador (10..500 caracteres,
--               validado no serviço; aqui só o teto).
--
-- ck_users_ban_consistency garante que banido sempre tem data e motivo e que
-- o desbanimento limpa os dois campos. Scripts manuais precisam respeitar isso.
-- ============================================================================

alter table users
    add column if not exists banned boolean not null default false,
    add column if not exists banned_at timestamp(6) with time zone,
    add column if not exists ban_reason varchar(500);

alter table users
    drop constraint if exists ck_users_ban_consistency;

alter table users
    add constraint ck_users_ban_consistency check (
        (banned = false and banned_at is null and ban_reason is null)
        or
        (banned = true and banned_at is not null and ban_reason is not null)
    );

-- Banidos são exceção: o índice parcial atende a listagem do backoffice
-- e as subconsultas "not exists ... banned = true" do feed.
create index if not exists idx_users_banned
    on users (id)
    where banned = true;
