-- ============================================================================
-- Edição de publicações (pessoais e de comunidade): só o texto muda e o
-- instante da última alteração fica em `edited_at` (nulo = nunca editada).
-- O feed da aba Início (posts de quem sigo + posts das comunidades em que
-- sou membro) reaproveita `idx_posts_feed` (fk_community, created_at desc,
-- id desc) e `ix_posts_personal_author_created` (V13); nenhum índice novo.
-- ============================================================================
alter table posts
    add column if not exists edited_at timestamp(6) with time zone;
