-- Unify API - seed de DEMONSTRACAO da moderacao (semana 06). Carregado APENAS
-- no perfil %dev, DEPOIS de import-dev.sql, import-users.sql e import-posts.sql
-- (usa usuarios e posts criados la). NUNCA em prod/homolog.
--
-- Objetivo: o backoffice (unify-backoffice) abrir com uma fila de denuncias
-- real para triar, e o "Banir" mostrar o efeito em cascata:
--
--   * Nicolas (seed.user14@unify.dev) tem 3 denuncias ABERTAS de 3 pessoas
--     diferentes, inclusive de Pedro (teste@gmail.com). Banir o Nicolas
--     encerra as 3 como RESOLVED e cada denunciante recebe 1 aviso
--     REPORT_UPDATE (logue no app como teste@gmail.com para ver o sino).
--   * 2 denuncias de PUBLICACAO (uma de comunidade, uma pessoal): o detalhe
--     mostra o texto do post denunciado.
--   * Quezia (seed.user37@unify.dev) ja entra BANIDA, para o filtro "Banidos"
--     da busca de usuarios ter conteudo e para conferir que ela some do app.
--
-- Todas as contas de seed usam a senha Abc123!@ (ver import-dev.sql).
-- Convencao de ids: b0xxxxxx = denuncias. Denuncia de post e sempre mais nova
-- que o post (ver created_at em import-posts.sql).

insert into user_reports(id, fk_reporter, fk_reported_user, fk_reported_post, reason, description, status, created_at)
values ('b0000000-0000-4000-8000-000000000001',
        '019dbf9a-5a8e-72de-85cb-8426b424c6fe', '10000000-0000-4000-8000-00000000000e', null,
        'HARASSMENT', 'Mandou mensagens ofensivas repetidas depois que eu recusei o match.',
        'OPEN', now() - interval '2 days'),
       ('b0000000-0000-4000-8000-000000000002',
        '10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-00000000000e', null,
        'FAKE_PROFILE', 'As fotos sao de uma pessoa famosa e a bio foi copiada de outro perfil.',
        'OPEN', now() - interval '30 hours'),
       ('b0000000-0000-4000-8000-000000000003',
        '019dd000-0000-4000-8000-00000000000a', '10000000-0000-4000-8000-00000000000e', null,
        'SCAM', 'Pediu dinheiro emprestado na conversa logo depois do match.',
        'OPEN', now() - interval '5 hours'),
       ('b0000000-0000-4000-8000-000000000004',
        '019dd000-0000-4000-8000-00000000000b', '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000026',
        'INAPPROPRIATE_CONTENT', 'A publicacao usa termos pejorativos para falar de pessoas com deficiencia.',
        'OPEN', now() - interval '3 hours'),
       ('b0000000-0000-4000-8000-000000000005',
        '10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000009', '50000000-0000-4000-8000-00000000000b',
        'OTHER', 'Divulgacao de rifa paga, sem relacao com o perfil.',
        'OPEN', now() - interval '10 hours'),
       ('b0000000-0000-4000-8000-000000000006',
        '019dbf9a-5a8e-72de-85cb-8426b424c6fe', '10000000-0000-4000-8000-000000000015', null,
        'FAKE_PROFILE', null,
        'OPEN', now() - interval '6 days');

update users
   set banned = true,
       banned_at = now() - interval '1 day',
       ban_reason = 'Envio de spam com links de golpe para varios perfis (seed de demonstracao).'
 where id = '10000000-0000-4000-8000-000000000025';
