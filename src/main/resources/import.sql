-- Unify API - DDL complementar + dados de referencia (catalogos).
--
-- Este arquivo NAO contem dados de demonstracao. Usuarios, perfis, comunidades
-- e memberships de demo vivem em import-dev.sql (carregado apenas em %dev).
--
-- Em prod/homolog este arquivo NAO e executado: o schema vem de
-- db/migration/V1__baseline.sql e os catalogos de V2__reference_data.sql.
-- Qualquer mudanca aqui precisa ser espelhada nas migracoes Flyway.

create unique index if not exists uq_user_coordinates_active_profile
    on user_coordinates (fk_user_profile)
    where active = true;

create unique index if not exists uq_user_profile_images_active_profile_pic
    on user_profile_images (fk_user_profile)
    where active = true and is_profile_pic = true;

create index if not exists idx_user_profile_images_active_gallery
    on user_profile_images (fk_user_profile, id desc)
    where active = true and is_profile_pic = false;

create index if not exists idx_posts_feed
    on posts (fk_community, created_at desc, id desc);

create index if not exists idx_post_comments_post
    on post_comments (fk_post, created_at asc, id asc)
    where active = true;

create index if not exists idx_post_likes_post
    on post_likes (fk_post);

create index if not exists idx_community_memberships_community
    on community_memberships (fk_community);

create index if not exists idx_community_join_requests_community
    on community_join_requests (fk_community, requested_at asc, id asc);

create index if not exists idx_communities_owner
    on communities (fk_owner_user);

create index if not exists idx_community_memberships_role
    on community_memberships (fk_community, role);

-- Semana 03 (espelha V12/V13): unicidade parcial de denúncia ABERTA por alvo
-- e índice do feed pessoal. Hibernate não gera índices parciais.
create unique index if not exists ux_user_reports_open_unique
    on user_reports (
        fk_reporter,
        fk_reported_user,
        coalesce(fk_reported_post, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    where status = 'OPEN';

create index if not exists ix_posts_personal_author_created
    on posts (fk_user, created_at desc, id desc)
    where origin = 'PERSONAL' and active = true;

-- Semana 05 (espelha V21): badge de não lidas do sino.
create index if not exists idx_notifications_recipient_unread
    on notifications (fk_recipient_user)
    where read_at is null;

insert into genders(id, description)
values (1, 'Mulher'),
       (2, 'Homem'),
       (3, 'Não binário'),
       (4, 'Prefiro não informar');

insert into pronouns(id, description)
values (1, 'Ela/Dela'),
       (2, 'Ele/Dele'),
       (3, 'Elu/Delu'),
       (4, 'Prefiro não informar');

insert into disabilities(id, description, ionic_icon)
values (1, 'Física', 'walk-outline'),
       (2, 'Visual', 'eye-outline'),
       (3, 'Auditiva', 'ear-outline'),
       (4, 'Intelectual', 'accessibility-outline');

insert into accessibility_needs(id, description)
values (1, 'Cadeira de rodas'),
       (2, 'Libras'),
       (3, 'Leitor de tela'),
       (4, 'Comunicação assistiva');

insert into autonomy_levels(id, description)
values (1, 'Independente'),
       (2, 'Parcialmente independente'),
       (3, 'Precisa de apoio');

insert into communication_forms(id, description)
values (1, 'Texto'),
       (2, 'Áudio'),
       (3, 'Vídeo'),
       (4, 'Libras'),
       (5, 'Comunicação assistiva');

insert into lifestyle_types(id, description)
values (1, 'Caseiro'),
       (2, 'Social'),
       (3, 'Gosta de viajar'),
       (4, 'Atividades acessíveis');

insert into love_languages(id, description)
values (1, 'Palavras de afirmação'),
       (2, 'Tempo de qualidade'),
       (3, 'Presentes'),
       (4, 'Atos de serviço'),
       (5, 'Toque físico');

insert into energy_levels(id, description)
values (1, 'Baixa'),
       (2, 'Moderada'),
       (3, 'Alta');

insert into interest_types(id, description)
values (1, 'Esportes adaptados'),
       (2, 'Cultura'),
       (3, 'Tecnologia'),
       (4, 'Jogos'),
       (5, 'Música'),
       (6, 'Filmes e séries');

insert into connection_types(id, description)
values (1, 'Amizade'),
       (2, 'Relacionamento'),
       (3, 'Networking'),
       (4, 'Comunidade');

insert into community_categories(id, description, ionic_icon)
values (1, 'Esportes Adaptados', 'basketball-outline'),
       (2, 'Arte e Cultura', 'color-palette-outline'),
       (3, 'Tecnologia Assistiva', 'hardware-chip-outline'),
       (4, 'Apoio e Bem-estar', 'heart-outline'),
       (5, 'Educação e Estudos', 'school-outline'),
       (6, 'Trabalho e Empreendedorismo', 'briefcase-outline'),
       (7, 'Jogos e Games Acessíveis', 'game-controller-outline'),
       (8, 'Música e Podcasts', 'musical-notes-outline'),
       (9, 'Viagem e Mobilidade Urbana', 'airplane-outline'),
       (10, 'Relacionamentos e Amizade', 'people-outline');


-- ---------------------------------------------------------------------------
-- Semana 06 (espelha V27..V32). O Hibernate já cria as colunas role/banned/
-- active (com default) e o check ck_users_ban_consistency a partir das
-- entidades; aqui ficam só o que ele não gera: índices parciais e as
-- sequências de id dos catálogos administráveis pelo backoffice.
-- ---------------------------------------------------------------------------

create index if not exists idx_users_role_admin
    on users (role)
    where role = 'ADMIN';

create index if not exists idx_users_banned
    on users (id)
    where banned = true;

create index if not exists idx_user_reports_status_created
    on user_reports (status, created_at desc);

create index if not exists idx_user_reports_reported_created
    on user_reports (fk_reported_user, created_at desc);

create index if not exists idx_admin_audit_log_admin
    on admin_audit_log (fk_admin_user, created_at desc);

create index if not exists idx_admin_audit_log_target
    on admin_audit_log (target_type, target_id, created_at desc);

create index if not exists idx_admin_audit_log_created
    on admin_audit_log (created_at desc);

-- Próximo id de item criado pelo backoffice = maior id semeado + 1 (V30).
-- setval com is_called = false: o primeiro nextval devolve exatamente esse valor.
-- O drop-and-create do Hibernate não conhece estas sequências; o
-- "if not exists" + setval as reposiciona a cada boot.
create sequence if not exists seq_genders_id;
select setval('seq_genders_id', (select coalesce(max(id), 0) + 1 from genders), false);
create sequence if not exists seq_pronouns_id;
select setval('seq_pronouns_id', (select coalesce(max(id), 0) + 1 from pronouns), false);
create sequence if not exists seq_disabilities_id;
select setval('seq_disabilities_id', (select coalesce(max(id), 0) + 1 from disabilities), false);
create sequence if not exists seq_accessibility_needs_id;
select setval('seq_accessibility_needs_id', (select coalesce(max(id), 0) + 1 from accessibility_needs), false);
create sequence if not exists seq_autonomy_levels_id;
select setval('seq_autonomy_levels_id', (select coalesce(max(id), 0) + 1 from autonomy_levels), false);
create sequence if not exists seq_communication_forms_id;
select setval('seq_communication_forms_id', (select coalesce(max(id), 0) + 1 from communication_forms), false);
create sequence if not exists seq_lifestyle_types_id;
select setval('seq_lifestyle_types_id', (select coalesce(max(id), 0) + 1 from lifestyle_types), false);
create sequence if not exists seq_love_languages_id;
select setval('seq_love_languages_id', (select coalesce(max(id), 0) + 1 from love_languages), false);
create sequence if not exists seq_energy_levels_id;
select setval('seq_energy_levels_id', (select coalesce(max(id), 0) + 1 from energy_levels), false);
create sequence if not exists seq_interest_types_id;
select setval('seq_interest_types_id', (select coalesce(max(id), 0) + 1 from interest_types), false);
create sequence if not exists seq_connection_types_id;
select setval('seq_connection_types_id', (select coalesce(max(id), 0) + 1 from connection_types), false);
create sequence if not exists seq_community_categories_id;
select setval('seq_community_categories_id', (select coalesce(max(id), 0) + 1 from community_categories), false);

-- Pesos de pontuação (V33): mesmos seeds da migration = padrões do código
-- (ScoringDimension). A API lê estas linhas no boot (ScoringWeightRegistry).
insert into scoring_group_revisions (scoring_group, revision) values
    ('MATCH', 0),
    ('FEED', 0),
    ('COMMUNITY_DISCOVERY', 0)
on conflict (scoring_group) do nothing;

insert into scoring_weights (dimension, weight) values
    ('MATCH_COMMUNICATION', 22),
    ('MATCH_INTERESTS', 18),
    ('MATCH_CONNECTION_TYPE', 12),
    ('MATCH_ACCESSIBILITY_NEEDS', 12),
    ('MATCH_LIFESTYLE', 10),
    ('MATCH_AUTONOMY', 8),
    ('MATCH_LOVE_LANGUAGE', 6),
    ('MATCH_ENERGY', 5),
    ('MATCH_DISTANCE', 4),
    ('MATCH_AGE_DIFFERENCE', 3),
    ('MATCH_PENALTY_GENDER_NOT_RECIPROCAL', 8),
    ('MATCH_PENALTY_AGE_NOT_RECIPROCAL', 5),
    ('MATCH_PENALTY_RESHOWN_AFTER_COOLDOWN', 10),
    ('FEED_FOLLOWING', 60),
    ('FEED_MEMBER_COMMUNITY', 50),
    ('FEED_FOLLOWED_AUTHOR_IN_COMMUNITY', 25),
    ('FEED_SHARED_INTEREST', 15),
    ('FEED_SHARED_INTERESTS_CAP', 45),
    ('FEED_SAME_CATEGORY', 20),
    ('FEED_FEATURED_COMMUNITY', 5),
    ('FEED_FOLLOWED_MEMBER', 10),
    ('FEED_FOLLOWED_MEMBERS_CAP', 30),
    ('FEED_LIKE', 2),
    ('FEED_COMMENT', 4),
    ('FEED_ENGAGEMENT_CAP', 30),
    ('FEED_RECENCY', 40),
    ('FEED_RECENCY_HALF_LIFE_HOURS', 48),
    ('COMMUNITY_DISCOVERY_MEMBER', 3),
    ('COMMUNITY_DISCOVERY_POST', 2),
    ('COMMUNITY_DISCOVERY_LIKE', 1)
on conflict (dimension) do nothing;


-- ---------------------------------------------------------------------------
-- Semana 07 (espelha V34/V35). O Hibernate cria users.created_at (default
-- now()), as colunas de moderação de posts e o check
-- ck_posts_moderation_inactive a partir das entidades; aqui ficam os índices.
-- ---------------------------------------------------------------------------

create index if not exists idx_users_created_at
    on users (created_at);

create index if not exists idx_user_possible_matches_matched_at
    on user_possible_matches (matched_at)
    where matched_at is not null;

create index if not exists idx_chat_messages_created_at
    on chat_messages (created_at);

create index if not exists idx_posts_created_at
    on posts (created_at);

create index if not exists idx_user_reports_created_at
    on user_reports (created_at);

create index if not exists idx_posts_moderated_at
    on posts (moderated_at desc)
    where moderated_at is not null;

-- Semana 07 (ajuste, espelha V36): user_activity_days e communities.created_at
-- saem das entidades; aqui só os índices.
create index if not exists idx_user_activity_days_date
    on user_activity_days (activity_date);

create index if not exists idx_communities_created_at
    on communities (created_at);

create index if not exists idx_community_memberships_joined_at
    on community_memberships (joined_at);
