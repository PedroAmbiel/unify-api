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


-- ---------------------------------------------------------------------------
-- Semana 07 — dashboard administrativo e moderação de publicações.
--
--   * Cadastros espalhados pelos últimos 80 dias (users.created_at nasce com
--     now() no boot): a série "Novos usuários" do painel ganha forma.
--   * 13 matches mútuos entre contas de seed (user20..user33 x user34..user47,
--     sem a Quezia, que está banida) ao longo de ~4 semanas, já vistos pelos
--     dois lados (não disparam a tela "Deu match"), cada um com conversa e
--     algumas mensagens: alimentam "Matches mútuos" e "Mensagens enviadas".
--   * A publicação 50..26 (comunidade Games Acessiveis) recebe mais 2
--     denúncias: a tela "Publicações" mostra 3 denúncias pendentes no mesmo
--     post, e "Remover publicação" encerra as 3 de uma vez.
--   * 4 denúncias antigas já encerradas para a série "Denúncias abertas".
-- Convenção de ids: c0 = matches, c1 = conversas, c2 = mensagens.
-- ---------------------------------------------------------------------------

update users
   set created_at = now()
                    - ((abs(hashtext(email)::bigint) % 80) * interval '1 day')
                    - ((abs(hashtext(email)::bigint) % 23) * interval '1 hour')
 where role = 'USER';

insert into user_possible_matches(id, fk_starter_user_profile, fk_pending_user_profile, created_at,
                                  starter_accepted, pending_accepted, matched_at,
                                  starter_match_seen_at, pending_match_seen_at)
select ('c0000000-0000-4000-8000-' || lpad(to_hex(k), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(20 + k), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(34 + k), 12, '0'))::uuid,
       matched - interval '1 day',
       true, true, matched, matched, matched
from (
    select k, now() - ((k * 2 + k % 3) * interval '1 day') - interval '6 hours' as matched
    from generate_series(0, 13) as gs(k)
    where 34 + k <> 37
) as seed_matches;

insert into conversations(id, fk_user_possible_match, fk_participant_one_user_profile,
                          fk_participant_two_user_profile, created_at, last_message_at)
select ('c1000000-0000-4000-8000-' || lpad(to_hex(k), 12, '0'))::uuid,
       ('c0000000-0000-4000-8000-' || lpad(to_hex(k), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(20 + k), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(34 + k), 12, '0'))::uuid,
       now() - ((k * 2 + k % 3) * interval '1 day') - interval '6 hours',
       null
from generate_series(0, 13) as gs(k)
where 34 + k <> 37;

insert into chat_messages(id, fk_conversation, fk_sender_user_profile, type, body,
                          created_at, updated_at, delivered_at, read_at)
select ('c2000000-0000-4000-8000-' || lpad(to_hex(k * 100 + m), 12, '0'))::uuid,
       ('c1000000-0000-4000-8000-' || lpad(to_hex(k), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(case when m % 2 = 1 then 20 + k else 34 + k end), 12, '0'))::uuid,
       'TEXT',
       (array['Oi! Vi que a gente deu match :)',
              'Oi! Tudo bem? Gostei muito da sua bio.',
              'Voce tambem participa da comunidade de acessibilidade?',
              'Sim! Entrei semana passada.',
              'Bora marcar um cafe no centro?',
              'Bora! Sabado de manha fica bom?',
              'Fechado, ate la!'])[m],
       sent, sent, sent, sent + interval '10 minutes'
from (
    select k, m,
           least(now() - interval '20 minutes',
                 now() - ((k * 2 + k % 3) * interval '1 day') - interval '6 hours' + (m * interval '9 hours')) as sent
    from generate_series(0, 13) as gs(k)
    cross join generate_series(1, 7) as gm(m)
    where 34 + k <> 37
      and m <= 3 + (k % 5)
) as seed_messages;

update conversations c
   set last_message_at = (select max(cm.created_at) from chat_messages cm where cm.fk_conversation = c.id)
 where c.id::text like 'c1000000-%';

insert into user_reports(id, fk_reporter, fk_reported_user, fk_reported_post, reason, description, status, created_at)
values ('b0000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000026',
        'HATE_SPEECH', 'Fala de forma depreciativa sobre jogadores com deficiencia visual.',
        'OPEN', now() - interval '2 hours'),
       ('b0000000-0000-4000-8000-000000000008',
        '10000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000026',
        'INAPPROPRIATE_CONTENT', null,
        'OPEN', now() - interval '40 minutes');

insert into user_reports(id, fk_reporter, fk_reported_user, fk_reported_post, reason, description, status,
                         created_at, admin_note, reviewed_at, resolved_at)
values ('b0000000-0000-4000-8000-000000000009',
        '10000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000011', null,
        'FAKE_PROFILE', 'Perfil com fotos de banco de imagens.',
        'DISMISSED', now() - interval '12 days',
        'Fotos conferidas: sao da propria pessoa (seed de demonstracao).', now() - interval '11 days', now() - interval '11 days'),
       ('b0000000-0000-4000-8000-00000000000a',
        '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000012', null,
        'HARASSMENT', 'Insistiu em conversar depois de eu pedir para parar.',
        'RESOLVED', now() - interval '9 days',
        'Conta advertida por mensagem (seed de demonstracao).', now() - interval '8 days', now() - interval '8 days'),
       ('b0000000-0000-4000-8000-00000000000b',
        '10000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000013', null,
        'SCAM', null,
        'DISMISSED', now() - interval '20 days',
        'Sem indicio de golpe na conversa analisada (seed de demonstracao).', now() - interval '19 days', now() - interval '19 days'),
       ('b0000000-0000-4000-8000-00000000000c',
        '10000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000016', null,
        'OTHER', 'Divulgando servico pago no perfil.',
        'RESOLVED', now() - interval '4 days',
        'Bio ajustada pela pessoa apos contato (seed de demonstracao).', now() - interval '3 days', now() - interval '3 days');

-- ---------------------------------------------------------------------------
-- Semana 07 (ajuste) — dashboards por dimensão.
--   * Comunidades criadas entre 10 e 80 dias atrás; o dono entra na criação
--     e os demais membros em datas espalhadas desde então.
--   * Dias de uso (user_activity_days): o mesmo backfill da V36 a partir dos
--     eventos dos seeds + uso sintético das contas seed.userNN (15% a 65% dos
--     dias). Uma em cada cinco contas para de usar há 40 dias: aparece como
--     inativa na janela padrão de 30 dias.
-- ---------------------------------------------------------------------------

update communities c
   set created_at = now() - ((abs(hashtext(c.name)::bigint) % 70 + 10) * interval '1 day');

update community_memberships m
   set joined_at = case
           when m.role = 'ADMIN' then c.created_at
           else c.created_at + ((abs(hashtext(m.id::text)::bigint) % 1000) / 1000.0) * (now() - c.created_at)
       end
  from communities c
 where c.id = m.fk_community;

insert into user_activity_days (fk_user, activity_date)
select user_id, day
from (
    select u.id as user_id, (u.created_at at time zone 'America/Sao_Paulo')::date as day from users u
    union select sp.fk_user, (cm.created_at at time zone 'America/Sao_Paulo')::date
      from chat_messages cm join user_profiles sp on sp.id = cm.fk_sender_user_profile
    union select p.fk_user, (p.created_at at time zone 'America/Sao_Paulo')::date from posts p
    union select l.fk_user, (l.created_at at time zone 'America/Sao_Paulo')::date from post_likes l
    union select c.fk_user, (c.created_at at time zone 'America/Sao_Paulo')::date from post_comments c
    union select sp.fk_user, (m.created_at at time zone 'America/Sao_Paulo')::date
      from user_possible_matches m join user_profiles sp on sp.id = m.fk_starter_user_profile
    union select pp.fk_user, (coalesce(m.matched_at, m.declined_at) at time zone 'America/Sao_Paulo')::date
      from user_possible_matches m join user_profiles pp on pp.id = m.fk_pending_user_profile
     where coalesce(m.matched_at, m.declined_at) is not null
    union select fp.fk_user, (f.created_at at time zone 'America/Sao_Paulo')::date
      from user_follows f join user_profiles fp on fp.id = f.fk_follower
) as events
on conflict do nothing;

insert into user_activity_days (fk_user, activity_date)
select u.id, d.day
  from users u
 cross join lateral (
       select ((now() at time zone 'America/Sao_Paulo')::date - g) as day
         from generate_series(0, 79) as g
       ) d
 where u.role = 'USER'
   and u.email like 'seed.user%'
   and not u.banned
   and d.day >= (u.created_at at time zone 'America/Sao_Paulo')::date
   and abs(hashtext(u.email || d.day::text)::bigint) % 100 < 15 + abs(hashtext(u.email)::bigint) % 50
   and (abs(hashtext(u.email)::bigint) % 5 <> 0
        or d.day < (now() at time zone 'America/Sao_Paulo')::date - 40)
on conflict do nothing;

-- Matches que não viraram conversa, curtidas aguardando resposta e recusas
-- (starters seed.user01..20, fora dos pares acima): funil e desfecho com forma.
--   j = 0..5   match mútuo sem mensagem
--   j = 6..13  curtida aguardando resposta
--   j = 14..19 convite recusado
insert into user_possible_matches(id, fk_starter_user_profile, fk_pending_user_profile, created_at,
                                  starter_accepted, pending_accepted, matched_at, declined_at,
                                  fk_declined_by_user_profile, starter_match_seen_at, pending_match_seen_at)
select ('c3000000-0000-4000-8000-' || lpad(to_hex(j), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(1 + j), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(21 + (j % 12)), 12, '0'))::uuid,
       liked,
       true,
       case when j < 6 then true when j >= 14 then false end,
       case when j < 6 then liked + ((j + 1) * interval '7 hours') end,
       case when j >= 14 then liked + ((j - 13) * interval '5 hours') end,
       case when j >= 14 then ('20000000-0000-4000-8000-' || lpad(to_hex(21 + (j % 12)), 12, '0'))::uuid end,
       case when j < 6 then liked + ((j + 1) * interval '7 hours') end,
       case when j < 6 then liked + ((j + 1) * interval '7 hours') end
from (
    select j, now() - ((j * 3 % 26 + 2) * interval '1 day') as liked
    from generate_series(0, 19) as gs(j)
) as seed_likes
where 21 + (j % 12) <> 37;

-- Contas adormecidas (demonstração da janela de inatividade): cadastro há
-- 50+ dias e nenhum dia de uso nos últimos 35.
update users
   set created_at = now() - ((50 + abs(hashtext(email)::bigint) % 20) * interval '1 day')
 where email in ('seed.user05@unify.dev', 'seed.user08@unify.dev', 'seed.user11@unify.dev',
                 'seed.user15@unify.dev', 'seed.user18@unify.dev', 'seed.user48@unify.dev');

delete from user_activity_days a
 using users u
 where a.fk_user = u.id
   and u.email in ('seed.user05@unify.dev', 'seed.user08@unify.dev', 'seed.user11@unify.dev',
                   'seed.user15@unify.dev', 'seed.user18@unify.dev', 'seed.user48@unify.dev')
   and a.activity_date > (now() at time zone 'America/Sao_Paulo')::date - 35;

insert into user_activity_days (fk_user, activity_date)
select u.id, (u.created_at at time zone 'America/Sao_Paulo')::date
  from users u
 where u.email in ('seed.user05@unify.dev', 'seed.user08@unify.dev', 'seed.user11@unify.dev',
                   'seed.user15@unify.dev', 'seed.user18@unify.dev', 'seed.user48@unify.dev')
on conflict do nothing;

-- Curtidas dos seeds espalhadas entre a publicação do post e agora (no
-- import-posts.sql quase todas nascem com now()): a série "Curtidas" do painel
-- de publicações deixa de ser um pico só no último dia.
update post_likes l
   set created_at = p.created_at + ((abs(hashtext(l.id::text)::bigint) % 1000) / 1000.0) * (now() - p.created_at)
  from posts p
 where p.id = l.fk_post
   and l.id::text not like '90000000-0000-4000-8000-0000000000f%';
