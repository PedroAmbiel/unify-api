-- Unify API - seed de DEMONSTRACAO do feed da aba Inicio. Carregado APENAS
-- no perfil %dev, DEPOIS de import-dev.sql e import-users.sql (depende dos
-- usuarios, perfis e comunidades criados la). NUNCA em prod/homolog.
--
-- Objetivo: dar ao desenvolvedor uma base com publicacoes pessoais e de
-- comunidade, curtidas, comentarios, seguidores e comunidades publicas/
-- privadas suficiente para ver o ranking do feed (HomeFeedRankingPolicy)
-- funcionando logo apos o boot, logando com teste@gmail.com / Abc123!@.
--
-- O que a conta teste@gmail.com (Pedro) enxerga no Inicio:
--   * FOLLOWING: posts pessoais de Ana (ana@unify.com) e de seed.user01/02.
--   * MEMBER_COMMUNITY: posts das 3 comunidades de import-dev.sql (ele e ADMIN),
--     inclusive da "Unify Privada" (privada, mas ele e membro).
--   * SUGGESTED_PROFILE: posts de seed.userNN com interesses em comum
--     (Tecnologia/Jogos/Musica), ordenados por afinidade + curtidas/comentarios.
--   * SUGGESTED_COMMUNITY: posts de "Games Acessiveis", "Rolês Acessíveis" e
--     "Musica para Todos" (publicas, ele NAO e membro).
--   * NUNCA: posts da "Rede Privada de Devs" (privada, ele nao e membro) e os
--     proprios posts pessoais (ficam na aba Perfil).
--
-- Convencao de ids (todos UUID v4 fixos, faceis de reconhecer no banco):
--   6xxxxxxx = comunidades   7xxxxxxx = memberships   8xxxxxxx = follows
--   5xxxxxxx = posts         9xxxxxxx = curtidas      a0xxxxxx = comentarios
-- Usuarios de import-users.sql: user 10000000-0000-4000-8000-0000000000NN,
-- perfil 20000000-0000-4000-8000-0000000000NN (NN = indice em hex).

-- ---------------------------------------------------------------------------
-- Comunidades adicionais (com categoria: afinidade de categoria no ranking)
-- ---------------------------------------------------------------------------
insert into communities(id, active, featured, description, name, fk_owner_user, fk_category, privacy)
values ('60000000-0000-4000-8000-000000000001', true, true,
        'Jogos com acessibilidade de verdade: legendas, remapeamento, audiodescricao e dicas de quem joga.',
        'Games Acessíveis', '10000000-0000-4000-8000-000000000004', 7, 'PUBLIC'),
       ('60000000-0000-4000-8000-000000000002', true, false,
        'Roteiros, trilhas e passeios urbanos com rota acessivel, banheiro adaptado e transporte.',
        'Rolês Acessíveis', '10000000-0000-4000-8000-000000000009', 9, 'PUBLIC'),
       ('60000000-0000-4000-8000-000000000003', true, true,
        'Shows com interprete de Libras, playlists, podcasts e instrumentos adaptados.',
        'Música para Todos', '10000000-0000-4000-8000-000000000005', 8, 'PUBLIC'),
       ('60000000-0000-4000-8000-000000000004', true, false,
        'Grupo fechado de pessoas desenvolvedoras com deficiencia. Entrada aprovada pela moderacao.',
        'Rede Privada de Devs', '10000000-0000-4000-8000-000000000003', 3, 'PRIVATE');

insert into community_memberships(id, fk_community, fk_user_profile, role, joined_at)
values ('70000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004', 'ADMIN',  now() - interval '30 days'),
       ('70000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-00000000000a', 'MEMBER', now() - interval '20 days'),
       ('70000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000010', 'MEMBER', now() - interval '12 days'),
       ('70000000-0000-4000-8000-000000000004', '60000000-0000-4000-8000-000000000001', '019dd001-0000-4000-8000-00000000000a', 'MEMBER', now() - interval '5 days'),
       ('70000000-0000-4000-8000-000000000005', '60000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000009', 'ADMIN',  now() - interval '30 days'),
       ('70000000-0000-4000-8000-000000000006', '60000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003', 'MEMBER', now() - interval '9 days'),
       ('70000000-0000-4000-8000-000000000007', '60000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-00000000000f', 'MEMBER', now() - interval '3 days'),
       ('70000000-0000-4000-8000-000000000008', '60000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000005', 'ADMIN',  now() - interval '30 days'),
       ('70000000-0000-4000-8000-000000000009', '60000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-00000000000b', 'MEMBER', now() - interval '15 days'),
       ('70000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000011', 'MEMBER', now() - interval '6 days'),
       ('70000000-0000-4000-8000-00000000000b', '60000000-0000-4000-8000-000000000003', '019dd001-0000-4000-8000-00000000000b', 'MEMBER', now() - interval '2 days'),
       ('70000000-0000-4000-8000-00000000000c', '60000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003', 'ADMIN',  now() - interval '30 days'),
       ('70000000-0000-4000-8000-00000000000d', '60000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000015', 'MEMBER', now() - interval '10 days'),
       -- seed users tambem entram nas comunidades de import-dev.sql
       ('70000000-0000-4000-8000-00000000000e', '01972a85-e1fd-7309-8f49-7d2168c18a11', '20000000-0000-4000-8000-000000000001', 'MEMBER', now() - interval '25 days'),
       ('70000000-0000-4000-8000-00000000000f', '01972a85-e1fd-7309-8f49-7d2168c18a11', '20000000-0000-4000-8000-000000000002', 'MEMBER', now() - interval '18 days'),
       ('70000000-0000-4000-8000-000000000010', '01972a85-e1fd-7309-8f49-7d2168c18a11', '019dd001-0000-4000-8000-00000000000a', 'MEMBER', now() - interval '8 days'),
       ('70000000-0000-4000-8000-000000000011', '01972a85-e1fc-7309-8f49-7d2168c18a11', '20000000-0000-4000-8000-000000000007', 'MEMBER', now() - interval '4 days'),
       ('70000000-0000-4000-8000-000000000012', '01972a85-e1fb-7309-8f49-7d2168c18a11', '20000000-0000-4000-8000-000000000008', 'MEMBER', now() - interval '4 days');

-- ---------------------------------------------------------------------------
-- Seguidores (user_follows referencia user_profiles)
-- ---------------------------------------------------------------------------
insert into user_follows(id, fk_follower, fk_followed, created_at)
values -- Pedro (teste@gmail.com) segue Ana, seed.user01 e seed.user02
       ('80000000-0000-4000-8000-000000000001', '01972a85-e1fd-7309-8f49-7d2168c18c11', '019dd001-0000-4000-8000-00000000000a', now() - interval '10 days'),
       ('80000000-0000-4000-8000-000000000002', '01972a85-e1fd-7309-8f49-7d2168c18c11', '20000000-0000-4000-8000-000000000001', now() - interval '7 days'),
       ('80000000-0000-4000-8000-000000000003', '01972a85-e1fd-7309-8f49-7d2168c18c11', '20000000-0000-4000-8000-000000000002', now() - interval '3 days'),
       -- quem segue Pedro
       ('80000000-0000-4000-8000-000000000004', '019dd001-0000-4000-8000-00000000000a', '01972a85-e1fd-7309-8f49-7d2168c18c11', now() - interval '9 days'),
       ('80000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', '01972a85-e1fd-7309-8f49-7d2168c18c11', now() - interval '6 days'),
       -- rede entre seed users e Ana/Bruno
       ('80000000-0000-4000-8000-000000000006', '019dd001-0000-4000-8000-00000000000a', '019dd001-0000-4000-8000-00000000000b', now() - interval '20 days'),
       ('80000000-0000-4000-8000-000000000007', '019dd001-0000-4000-8000-00000000000b', '019dd001-0000-4000-8000-00000000000a', now() - interval '20 days'),
       ('80000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004', now() - interval '14 days'),
       ('80000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000004', now() - interval '13 days'),
       ('80000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', now() - interval '11 days'),
       ('80000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', now() - interval '2 days');

-- ---------------------------------------------------------------------------
-- Publicacoes PESSOAIS (origin = PERSONAL, fk_community nulo)
-- created_at espalhado nos ultimos dias para a recencia fazer diferenca.
-- ---------------------------------------------------------------------------
insert into posts(id, fk_community, fk_user, body, created_at, origin, active)
values -- Ana (Pedro segue): FOLLOWING
       ('50000000-0000-4000-8000-000000000001', null, '019dd000-0000-4000-8000-00000000000a',
        'Testei hoje um leitor de tela novo no celular e a navegacao por titulos ficou muito melhor. Quem mais usa?', now() - interval '3 hours', 'PERSONAL', true),
       ('50000000-0000-4000-8000-000000000002', null, '019dd000-0000-4000-8000-00000000000a',
        'Show com interprete de Libras no sabado! Vou com o Bruno, alguem mais topa?', now() - interval '2 days', 'PERSONAL', true),
       -- seed.user01 (Pedro segue): FOLLOWING
       ('50000000-0000-4000-8000-000000000003', null, '10000000-0000-4000-8000-000000000001',
        'Primeira semana com a cadeira motorizada nova. Autonomia total no centro da cidade.', now() - interval '1 day', 'PERSONAL', true),
       -- seed.user02 (Pedro segue): FOLLOWING, post antigo (recencia baixa)
       ('50000000-0000-4000-8000-000000000004', null, '10000000-0000-4000-8000-000000000002',
        'Dica: o app de transporte agora mostra se o onibus tem elevador. Mudou minha rotina.', now() - interval '9 days', 'PERSONAL', true),
       -- Bruno (Pedro NAO segue, interesses 3 e 5 em comum): SUGGESTED_PROFILE forte
       ('50000000-0000-4000-8000-000000000005', null, '019dd000-0000-4000-8000-00000000000b',
        'Montei uma playlist de podcasts sobre tecnologia assistiva. Quem quiser o link, comenta aqui!', now() - interval '6 hours', 'PERSONAL', true),
       -- seed.user03 (interesses 3 e 4): SUGGESTED_PROFILE, muito engajamento
       ('50000000-0000-4000-8000-000000000006', null, '10000000-0000-4000-8000-000000000003',
        'Terminei meu primeiro jogo com audiodescricao completa. Foi emocionante do inicio ao fim.', now() - interval '20 hours', 'PERSONAL', true),
       -- seed.user04 (interesses 4 e 5): SUGGESTED_PROFILE
       ('50000000-0000-4000-8000-000000000007', null, '10000000-0000-4000-8000-000000000004',
        'Controle adaptado chegou! Remapeei tudo em dez minutos e ja estou jogando.', now() - interval '30 hours', 'PERSONAL', true),
       -- seed.user05 (interesses 5 e 6): SUGGESTED_PROFILE
       ('50000000-0000-4000-8000-000000000008', null, '10000000-0000-4000-8000-000000000005',
        'Descobri uma serie com audiodescricao caprichada. Recomendo demais para o fim de semana.', now() - interval '2 days', 'PERSONAL', true),
       -- seed.user06 (interesses 6 e 1): sem interesse em comum, so recencia/engajamento
       ('50000000-0000-4000-8000-000000000009', null, '10000000-0000-4000-8000-000000000006',
        'Treino de basquete em cadeira de rodas hoje as 19h. Quadra coberta e acessivel.', now() - interval '5 hours', 'PERSONAL', true),
       -- seed.user07 (interesses 1 e 2): sem interesse em comum
       ('50000000-0000-4000-8000-00000000000a', null, '10000000-0000-4000-8000-000000000007',
        'Exposicao com pecas taeteis no museu da cidade. Entrada gratuita ate domingo.', now() - interval '4 days', 'PERSONAL', true),
       -- seed.user09 (interesses 3 e 4): SUGGESTED_PROFILE
       ('50000000-0000-4000-8000-00000000000b', null, '10000000-0000-4000-8000-000000000009',
        'Alguem ja testou o modo de alto contraste do novo sistema? Quero comparar impressoes.', now() - interval '12 hours', 'PERSONAL', true),
       -- seed.user0a (interesses 4 e 5): SUGGESTED_PROFILE, antigo
       ('50000000-0000-4000-8000-00000000000c', null, '10000000-0000-4000-8000-00000000000a',
        'Festival de jogos indie com sessao acessivel. Guardem a data: proximo mes!', now() - interval '6 days', 'PERSONAL', true),
       -- seed.user10 (0x10 = 16: interesses 4 e 5): SUGGESTED_PROFILE
       ('50000000-0000-4000-8000-00000000000d', null, '10000000-0000-4000-8000-000000000010',
        'Comecei a aprender violao com partitura em braile. Devagar, mas indo!', now() - interval '8 hours', 'PERSONAL', true),
       -- seed.user15 (0x15 = 21: interesses 3 e 4): SUGGESTED_PROFILE, bem antigo
       ('50000000-0000-4000-8000-00000000000e', null, '10000000-0000-4000-8000-000000000015',
        'Curso gratuito de programacao para pessoas com deficiencia abriu inscricoes.', now() - interval '15 days', 'PERSONAL', true),
       -- Pedro (teste@gmail.com): fica so na aba Perfil, NUNCA no Inicio
       ('50000000-0000-4000-8000-00000000000f', null, '019dbf9a-5a8e-72de-85cb-8426b424c6fe',
        'Minha primeira publicacao no Unify. Bora conectar!', now() - interval '1 day', 'PERSONAL', true),
       ('50000000-0000-4000-8000-000000000010', null, '019dbf9a-5a8e-72de-85cb-8426b424c6fe',
        'Publicacao editada de teste para o menu de opcoes.', now() - interval '3 days', 'PERSONAL', true),
       -- post pessoal excluido (soft delete): nunca aparece
       ('50000000-0000-4000-8000-000000000011', null, '10000000-0000-4000-8000-000000000003',
        'Esse post foi apagado pelo autor e nao deve aparecer em lugar nenhum.', now() - interval '1 hour', 'PERSONAL', false);

-- ---------------------------------------------------------------------------
-- Publicacoes de COMUNIDADE (origin = COMMUNITY)
-- ---------------------------------------------------------------------------
insert into posts(id, fk_community, fk_user, body, created_at, origin, active)
values -- Comunidade Unify (Pedro e ADMIN): MEMBER_COMMUNITY
       ('50000000-0000-4000-8000-000000000021', '01972a85-e1fd-7309-8f49-7d2168c18a11', '019dd000-0000-4000-8000-00000000000a',
        'Bem-vindas e bem-vindos! Contem aqui o que voces esperam da comunidade.', now() - interval '7 days', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-000000000022', '01972a85-e1fd-7309-8f49-7d2168c18a11', '10000000-0000-4000-8000-000000000001',
        'Sugestao: encontro presencial mensal em local com acessibilidade verificada.', now() - interval '10 hours', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-000000000023', '01972a85-e1fd-7309-8f49-7d2168c18a11', '10000000-0000-4000-8000-000000000002',
        'Compartilhando o guia de acessibilidade digital que usamos no trabalho.', now() - interval '2 days', 'COMMUNITY', true),
       -- Teste Unify (Pedro e ADMIN): MEMBER_COMMUNITY
       ('50000000-0000-4000-8000-000000000024', '01972a85-e1fc-7309-8f49-7d2168c18a11', '10000000-0000-4000-8000-000000000007',
        'Post de teste na comunidade Teste Unify para validar o feed.', now() - interval '1 day', 'COMMUNITY', true),
       -- Unify Privada (privada, Pedro e ADMIN): MEMBER_COMMUNITY para ele; invisivel para nao membros
       ('50000000-0000-4000-8000-000000000025', '01972a85-e1fb-7309-8f49-7d2168c18a11', '10000000-0000-4000-8000-000000000008',
        'Conteudo restrito aos membros da Unify Privada.', now() - interval '4 hours', 'COMMUNITY', true),
       -- Games Acessiveis (publica, Pedro nao e membro): SUGGESTED_COMMUNITY
       ('50000000-0000-4000-8000-000000000026', '60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000004',
        'Lista atualizada de jogos com legendas grandes e remapeamento total de controles.', now() - interval '5 hours', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-000000000027', '60000000-0000-4000-8000-000000000001', '019dd000-0000-4000-8000-00000000000a',
        'Alguem joga com controle de um botao so? Quero trocar ideias sobre configuracao.', now() - interval '1 day', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-000000000028', '60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000010',
        'Torneio online acessivel no proximo sabado, inscricoes abertas.', now() - interval '3 days', 'COMMUNITY', true),
       -- Roles Acessiveis (publica, Pedro nao e membro): SUGGESTED_COMMUNITY
       ('50000000-0000-4000-8000-000000000029', '60000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000009',
        'Roteiro de domingo: parque com trilha pavimentada e banheiro adaptado a cada 500 m.', now() - interval '2 days', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-00000000002a', '60000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-00000000000f',
        'Mapeei as estacoes de metro com elevador funcionando esta semana.', now() - interval '16 hours', 'COMMUNITY', true),
       -- Musica para Todos (publica, destaque, Pedro nao e membro): SUGGESTED_COMMUNITY
       ('50000000-0000-4000-8000-00000000002b', '60000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000005',
        'Playlist colaborativa da comunidade esta no ar. Mandem as sugestoes!', now() - interval '9 hours', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-00000000002c', '60000000-0000-4000-8000-000000000003', '019dd000-0000-4000-8000-00000000000b',
        'Show com interprete de Libras confirmado para o mes que vem. Detalhes nos comentarios.', now() - interval '5 days', 'COMMUNITY', true),
       -- Rede Privada de Devs (privada, Pedro nao e membro): NUNCA aparece para ele
       ('50000000-0000-4000-8000-00000000002d', '60000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000003',
        'Vaga interna: dev acessibilidade, remoto. So para membros.', now() - interval '2 hours', 'COMMUNITY', true),
       ('50000000-0000-4000-8000-00000000002e', '60000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000015',
        'Revisao de codigo em grupo quinta-feira. Privado.', now() - interval '1 day', 'COMMUNITY', true);

-- ---------------------------------------------------------------------------
-- Curtidas (engajamento no ranking). generate_series evita listar uma a uma:
-- o post NN recebe curtidas dos primeiros K seed users.
-- ---------------------------------------------------------------------------
insert into post_likes(id, fk_post, fk_user, created_at)
select ('90000000-0000-4000-8000-' || lpad(to_hex(row_number() over ()), 12, '0'))::uuid,
       target.post_id,
       ('10000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid,
       now() - (gs.i * interval '20 minutes')
from (values
        ('50000000-0000-4000-8000-000000000006'::uuid, 14), -- seed.user03: mais curtido
        ('50000000-0000-4000-8000-000000000005'::uuid, 9),  -- Bruno
        ('50000000-0000-4000-8000-000000000001'::uuid, 6),  -- Ana
        ('50000000-0000-4000-8000-000000000007'::uuid, 5),  -- seed.user04
        ('50000000-0000-4000-8000-000000000009'::uuid, 4),  -- seed.user06
        ('50000000-0000-4000-8000-00000000000b'::uuid, 3),  -- seed.user09
        ('50000000-0000-4000-8000-000000000026'::uuid, 8),  -- Games Acessiveis
        ('50000000-0000-4000-8000-00000000002b'::uuid, 7),  -- Musica para Todos
        ('50000000-0000-4000-8000-000000000029'::uuid, 3),  -- Roles Acessiveis
        ('50000000-0000-4000-8000-000000000022'::uuid, 5),  -- Comunidade Unify
        ('50000000-0000-4000-8000-00000000002d'::uuid, 6)   -- Rede Privada (nao vaza pelo engajamento)
     ) as target(post_id, like_count)
cross join lateral generate_series(1, target.like_count) as gs(i)
where ('10000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid <> (
    -- o autor nao curte o proprio post no seed
    select p.fk_user from posts p where p.id = target.post_id
);

-- Pedro curtiu dois posts (likedByCurrentUser = true no feed dele)
insert into post_likes(id, fk_post, fk_user, created_at)
values ('90000000-0000-4000-8000-0000000000f1', '50000000-0000-4000-8000-000000000001', '019dbf9a-5a8e-72de-85cb-8426b424c6fe', now() - interval '2 hours'),
       ('90000000-0000-4000-8000-0000000000f2', '50000000-0000-4000-8000-000000000026', '019dbf9a-5a8e-72de-85cb-8426b424c6fe', now() - interval '4 hours');

-- ---------------------------------------------------------------------------
-- Comentarios (pesam mais que curtidas no ranking)
-- ---------------------------------------------------------------------------
insert into post_comments(id, fk_post, fk_user, body, created_at)
values ('a0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'Qual jogo? Quero jogar tambem!', now() - interval '18 hours'),
       ('a0000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000004', 'A audiodescricao desse e referencia.', now() - interval '17 hours'),
       ('a0000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000006', '019dd000-0000-4000-8000-00000000000b', 'Parabens! Me conta como foi o final (sem spoiler).', now() - interval '15 hours'),
       ('a0000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000003', 'Manda o link, por favor!', now() - interval '5 hours'),
       ('a0000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000009', 'Tambem quero.', now() - interval '4 hours'),
       ('a0000000-0000-4000-8000-000000000006', '50000000-0000-4000-8000-000000000001', '019dbf9a-5a8e-72de-85cb-8426b424c6fe', 'Uso o mesmo! A navegacao por titulos salvou meu dia.', now() - interval '2 hours'),
       ('a0000000-0000-4000-8000-000000000007', '50000000-0000-4000-8000-000000000001', '019dd000-0000-4000-8000-00000000000b', 'Vou testar hoje.', now() - interval '1 hour'),
       ('a0000000-0000-4000-8000-000000000008', '50000000-0000-4000-8000-000000000026', '10000000-0000-4000-8000-000000000010', 'Faltou um jogo de corrida na lista!', now() - interval '3 hours'),
       ('a0000000-0000-4000-8000-000000000009', '50000000-0000-4000-8000-000000000026', '019dd000-0000-4000-8000-00000000000a', 'Salvei, obrigada!', now() - interval '2 hours'),
       ('a0000000-0000-4000-8000-00000000000a', '50000000-0000-4000-8000-00000000002b', '10000000-0000-4000-8000-00000000000b', 'Adicionei tres musicas.', now() - interval '6 hours'),
       ('a0000000-0000-4000-8000-00000000000b', '50000000-0000-4000-8000-000000000022', '019dd000-0000-4000-8000-00000000000a', 'Apoio! Posso ajudar a verificar os locais.', now() - interval '8 hours'),
       ('a0000000-0000-4000-8000-00000000000c', '50000000-0000-4000-8000-00000000002d', '10000000-0000-4000-8000-000000000015', 'Interessado, mando o curriculo.', now() - interval '1 hour');
