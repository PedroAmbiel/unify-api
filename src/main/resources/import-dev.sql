-- Unify API - seed de DEMONSTRACAO. Carregado APENAS no perfil %dev
-- (`%dev.quarkus.hibernate-orm.sql-load-script`). NUNCA em prod/homolog.
--
-- Estes dados existem para dar ao desenvolvedor uma conta utilizavel logo
-- apos o boot. As senhas abaixo sao intencionalmente publicas, e por isso
-- este arquivo nao pode ser carregado fora de dev. O DemoSeedGuard aborta o
-- boot se detectar estes registros em qualquer outro perfil.
--
-- SENHA DE DEMONSTRACAO (bcrypt cost 10): TODOS os usuarios de seed usam
-- a mesma senha, para simplificar a demonstracao:
--
--   Abc123!@
--
-- Vale para teste@gmail.com, verificar@gmail.com, o par de match abaixo
-- (seed.user.ana@unify.dev / seed.user.bruno@unify.dev) e os ~50 usuarios
-- gerados por import-users.sql (seed.userNN@unify.dev).

insert into
    users(id, verified, last_updated_at, birthdate, email, last_name, name, password)
    values ('019dbf9a-5a8e-72de-85cb-8426b424c6fe', true, '2024-06-01T00:00:00Z', '1995-04-15', 'teste@gmail.com', 'Ambiel', 'Pedro', '$2a$10$XN.aKcLxdVXB6GVy/MSrveH8.d0OGP.qpbi94SF2yZr0QT5iIWGOO'),
    ('019dcfdc-fa72-722b-89ac-e331eb4f119a', false, '2024-06-01T00:00:00Z', '1998-11-08', 'verificar@gmail.com', 'Email', 'Verificar', '$2a$10$XN.aKcLxdVXB6GVy/MSrveH8.d0OGP.qpbi94SF2yZr0QT5iIWGOO');

insert into user_profiles(id, fk_user)
values ('01972a85-e1fd-7309-8f49-7d2168c18c11', '019dbf9a-5a8e-72de-85cb-8426b424c6fe');

insert into communities(id, active, featured, description, name, fk_owner_user, privacy)
values ('01972a85-e1fd-7309-8f49-7d2168c18a11', true, true,
    'Espaço da comunidade Unify para compartilhar experiências, apoio e novidades sobre acessibilidade e conexão.',
    'Comunidade Unify',
    '019dbf9a-5a8e-72de-85cb-8426b424c6fe', 'PUBLIC');

insert into communities(id, active, featured, description, name, fk_owner_user, privacy)
values ('01972a85-e1fc-7309-8f49-7d2168c18a11', true, true,
        'Espaço da comunidade Unify para compartilhar experiências, apoio e novidades sobre acessibilidade e conexão.',
        'Teste Unify',
        '019dbf9a-5a8e-72de-85cb-8426b424c6fe', 'PUBLIC');

insert into communities(id, active, featured, description, name, fk_owner_user, privacy)
values ('01972a85-e1fb-7309-8f49-7d2168c18a11', true, false,
        'Comunidade privada de teste: entrada precisa ser aprovada por moderação.',
        'Unify Privada',
        '019dbf9a-5a8e-72de-85cb-8426b424c6fe', 'PRIVATE');

insert into community_memberships(id, fk_community, fk_user_profile, role, joined_at)
values ('01972a85-e1fd-7309-8f49-7d2168c18b11', '01972a85-e1fd-7309-8f49-7d2168c18a11', '01972a85-e1fd-7309-8f49-7d2168c18c11', 'ADMIN', '2024-06-01T00:00:00Z'),
       ('01972a85-e1fc-7309-8f49-7d2168c18b11', '01972a85-e1fc-7309-8f49-7d2168c18a11', '01972a85-e1fd-7309-8f49-7d2168c18c11', 'ADMIN', '2024-06-01T00:00:00Z'),
       ('01972a85-e1fb-7309-8f49-7d2168c18b11', '01972a85-e1fb-7309-8f49-7d2168c18a11', '01972a85-e1fd-7309-8f49-7d2168c18c11', 'ADMIN', '2024-06-01T00:00:00Z');

-- ---------------------------------------------------------------------------
-- PAR DE DEMONSTRACAO DO ALGORITMO DE MATCH (tela "Encontros")
--
--   seed.user.ana@unify.dev    -> Abc123!@   (Ana Ribeiro, Mulher, 1996)
--   seed.user.bruno@unify.dev  -> Abc123!@   (Bruno Martins, Homem, 1994)
--
-- Os dois foram montados para se encontrarem no topo do feed um do outro:
-- ambos verificados, mesma coordenada ativa (distancia 0 km), diferenca de
-- idade de 2 anos, faixas etarias amplas, genero desejado reciproco e todos
-- os atributos de perfil identicos com as preferencias de similaridade em
-- SIMILAR (comunicacao, interesses, acessibilidade, estilo de vida, autonomia,
-- linguagem do amor, energia e tipo de conexao).
--
-- Nenhum registro em user_possible_matches e criado de proposito: o match
-- mutuo e a conversa devem ser gerados pela propria UI durante a demo.
-- ---------------------------------------------------------------------------

insert into
    users(id, verified, last_updated_at, birthdate, email, cellphone, last_name, name, password)
    values ('019dd000-0000-4000-8000-00000000000a', true, '2025-01-01T00:00:00Z', '1996-03-10', 'seed.user.ana@unify.dev', '5511990000001', 'Ribeiro', 'Ana', '$2a$10$XN.aKcLxdVXB6GVy/MSrveH8.d0OGP.qpbi94SF2yZr0QT5iIWGOO'),
           ('019dd000-0000-4000-8000-00000000000b', true, '2025-01-01T00:00:00Z', '1994-07-22', 'seed.user.bruno@unify.dev', '5511990000002', 'Martins', 'Bruno', '$2a$10$XN.aKcLxdVXB6GVy/MSrveH8.d0OGP.qpbi94SF2yZr0QT5iIWGOO');

insert into user_profiles(id, fk_user, bio, fk_gender, fk_pronouns, fk_autonomy_level, fk_energy_level)
values ('019dd001-0000-4000-8000-00000000000a', '019dd000-0000-4000-8000-00000000000a',
        'Ana curte tecnologia assistiva, shows acessiveis e boas conversas por texto.', 1, 1, 1, 2),
       ('019dd001-0000-4000-8000-00000000000b', '019dd000-0000-4000-8000-00000000000b',
        'Bruno curte tecnologia assistiva, shows acessiveis e boas conversas por texto.', 2, 2, 1, 2);

insert into user_coordinates(id, fk_user_profile, latitude, longitude, active)
values ('019dd002-0000-4000-8000-00000000000a', '019dd001-0000-4000-8000-00000000000a', 37.421998, -122.084000, true),
       ('019dd002-0000-4000-8000-00000000000b', '019dd001-0000-4000-8000-00000000000b', 37.421998, -122.084000, true);

insert into user_match_preferences(
    id, fk_user_profile, fk_connection_type,
    accessibility_need_similarity, autonomy_compatibility, lifestyle_similarity,
    love_language_similarity, energy_level_similarity,
    min_age, max_age, max_match_distance_km)
values ('019dd003-0000-4000-8000-00000000000a', '019dd001-0000-4000-8000-00000000000a', 2,
        'SIMILAR', 'SIMILAR', 'SIMILAR', 'SIMILAR', 'SIMILAR', 18, 60, 50),
       ('019dd003-0000-4000-8000-00000000000b', '019dd001-0000-4000-8000-00000000000b', 2,
        'SIMILAR', 'SIMILAR', 'SIMILAR', 'SIMILAR', 'SIMILAR', 18, 60, 50);

-- Genero desejado reciproco: Ana procura Homem (2), Bruno procura Mulher (1).
insert into user_match_preference_desired_genders(fk_user_match_preference, fk_gender)
values ('019dd003-0000-4000-8000-00000000000a', 2),
       ('019dd003-0000-4000-8000-00000000000b', 1);

-- Atributos identicos nos dois perfis: com similaridade SIMILAR, cada fator pontua cheio.
insert into user_profile_disabilities(fk_user_profile, fk_disability)
values ('019dd001-0000-4000-8000-00000000000a', 2),
       ('019dd001-0000-4000-8000-00000000000b', 2);

insert into user_profile_accessibility_needs(fk_user_profile, fk_accessibility_need)
values ('019dd001-0000-4000-8000-00000000000a', 3),
       ('019dd001-0000-4000-8000-00000000000b', 3);

insert into user_profile_communication_forms(fk_user_profile, fk_communication_form)
values ('019dd001-0000-4000-8000-00000000000a', 1),
       ('019dd001-0000-4000-8000-00000000000a', 2),
       ('019dd001-0000-4000-8000-00000000000b', 1),
       ('019dd001-0000-4000-8000-00000000000b', 2);

insert into user_profile_lifestyle_types(fk_user_profile, fk_lifestyle_type)
values ('019dd001-0000-4000-8000-00000000000a', 2),
       ('019dd001-0000-4000-8000-00000000000a', 3),
       ('019dd001-0000-4000-8000-00000000000b', 2),
       ('019dd001-0000-4000-8000-00000000000b', 3);

insert into user_profile_love_languages(fk_user_profile, fk_love_language)
values ('019dd001-0000-4000-8000-00000000000a', 1),
       ('019dd001-0000-4000-8000-00000000000a', 2),
       ('019dd001-0000-4000-8000-00000000000b', 1),
       ('019dd001-0000-4000-8000-00000000000b', 2);

insert into user_profile_interest_types(fk_user_profile, fk_interest_type)
values ('019dd001-0000-4000-8000-00000000000a', 3),
       ('019dd001-0000-4000-8000-00000000000a', 5),
       ('019dd001-0000-4000-8000-00000000000b', 3),
       ('019dd001-0000-4000-8000-00000000000b', 5);
