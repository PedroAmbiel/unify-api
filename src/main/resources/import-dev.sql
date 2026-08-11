-- Unify API - seed de DEMONSTRACAO. Carregado APENAS no perfil %dev
-- (`%dev.quarkus.hibernate-orm.sql-load-script`). NUNCA em prod/homolog.
--
-- Estes dados existem para dar ao desenvolvedor uma conta utilizavel logo
-- apos o boot. As senhas abaixo sao intencionalmente publicas, e por isso
-- este arquivo nao pode ser carregado fora de dev. O DemoSeedGuard aborta o
-- boot se detectar estes registros em qualquer outro perfil.
--
-- SENHAS DE DEMONSTRACAO (bcrypt cost 10, um hash DISTINTO por usuario):
--
--   teste@gmail.com      -> SenhaDemo1!    (conta verificada)
--   verificar@gmail.com  -> SenhaDemo2!    (conta com verificacao pendente)
--
-- Os ~50 usuarios gerados por import-users.sql (seed.userNN@unify.dev) usam
-- 10 hashes distintos rotacionados: SeedDemo01! .. SeedDemo10!, conforme
-- ((seed_index - 1) % 10) + 1.

insert into
    users(id, verified, last_updated_at, birthdate, email, last_name, name, password)
    values ('019dbf9a-5a8e-72de-85cb-8426b424c6fe', true, '2024-06-01T00:00:00Z', '1995-04-15', 'teste@gmail.com', 'Ambiel', 'Pedro', '$2a$10$otdEB.aKD2bMn78DdXgL1eSx7RAqc6IZCU/U3HPZH6okSTJCl22eS'),
    ('019dcfdc-fa72-722b-89ac-e331eb4f119a', false, '2024-06-01T00:00:00Z', '1998-11-08', 'verificar@gmail.com', 'Email', 'Verificar', '$2a$10$gOmBcf2Kagv5xARLx0laAe8QbA9XP1Au1goobfw9OlUHaehl2Lme.');

insert into user_profiles(id, fk_user)
values ('01972a85-e1fd-7309-8f49-7d2168c18c11', '019dbf9a-5a8e-72de-85cb-8426b424c6fe');

insert into communities(id, active, featured, description, name, fk_owner_user)
values ('01972a85-e1fd-7309-8f49-7d2168c18a11', true, true,
    'Espaço da comunidade Unify para compartilhar experiências, apoio e novidades sobre acessibilidade e conexão.',
    'Comunidade Unify',
    '019dbf9a-5a8e-72de-85cb-8426b424c6fe');

insert into communities(id, active, featured, description, name, fk_owner_user)
values ('01972a85-e1fc-7309-8f49-7d2168c18a11', true, true,
        'Espaço da comunidade Unify para compartilhar experiências, apoio e novidades sobre acessibilidade e conexão.',
        'Teste Unify',
        '019dbf9a-5a8e-72de-85cb-8426b424c6fe');

insert into community_memberships(id, fk_community, fk_user_profile, role, joined_at)
values ('01972a85-e1fd-7309-8f49-7d2168c18b11', '01972a85-e1fd-7309-8f49-7d2168c18a11', '01972a85-e1fd-7309-8f49-7d2168c18c11', 'ADMIN', '2024-06-01T00:00:00Z'),
       ('01972a85-e1fc-7309-8f49-7d2168c18b11', '01972a85-e1fc-7309-8f49-7d2168c18a11', '01972a85-e1fd-7309-8f49-7d2168c18c11', 'ADMIN', '2024-06-01T00:00:00Z');
