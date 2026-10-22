-- ============================================================================
-- Semana 07 — moderação de publicações (comunidade e pessoais).
--
-- Uma publicação denunciada pode ser REMOVIDA pelo backoffice de forma
-- definitiva: active = false (toda leitura do app já filtra active = true, então
-- ela some para todos, inclusive para o autor) e os campos abaixo registram
-- quem removeu, quando e por quê. Não existe endpoint para desfazer.
--
--  moderated_at      : instante da remoção (nulo = não moderada).
--  fk_moderated_by   : administrador que removeu.
--  moderation_reason : motivo interno (10..500 caracteres, validado no
--                      serviço). Não é enviado ao autor.
--
-- O conteúdo (texto e imagem) continua guardado como evidência para a
-- moderação; o app não tem nenhum caminho de leitura para post inativo.
-- Auditoria em admin_audit_log (V32): POST_REMOVE / POST_KEEP, target POST.
-- ============================================================================

alter table posts
    add column if not exists moderated_at timestamp(6) with time zone,
    add column if not exists fk_moderated_by uuid,
    add column if not exists moderation_reason varchar(500);

alter table posts
    drop constraint if exists fk_posts_moderated_by;

alter table posts
    add constraint fk_posts_moderated_by foreign key (fk_moderated_by)
        references users (id) on delete set null;

alter table posts
    drop constraint if exists ck_posts_moderation_inactive;

alter table posts
    add constraint ck_posts_moderation_inactive check (moderated_at is null or active = false);

-- Fila "Publicações removidas" do backoffice.
create index if not exists idx_posts_moderated_at
    on posts (moderated_at desc)
    where moderated_at is not null;
