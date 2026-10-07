-- ============================================================================
-- Semana 04 — comentário removido vira soft delete (critério de aceite do
-- plano 04): a linha continua em post_comments com active = false, some das
-- listagens/contagens e fica disponível como evidência para a moderação
-- (denúncias da semana 03, backoffice da semana 06). Vale para comentários de
-- posts pessoais e de comunidade (tabela única). Post de comunidade apagado
-- fisicamente continua levando os comentários junto.
-- ============================================================================
alter table post_comments
    add column if not exists active boolean not null default true;

drop index if exists idx_post_comments_post;

create index if not exists idx_post_comments_post
    on post_comments (fk_post, created_at asc, id asc)
    where active = true;
