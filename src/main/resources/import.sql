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

create index if not exists idx_community_posts_feed
    on community_posts (fk_community, created_at desc, id desc);

create index if not exists idx_community_post_comments_post
    on community_post_comments (fk_post, created_at asc, id asc);

create index if not exists idx_community_post_likes_post
    on community_post_likes (fk_post);

create index if not exists idx_community_memberships_community
    on community_memberships (fk_community);

create index if not exists idx_communities_owner
    on communities (fk_owner_user);

create index if not exists idx_community_memberships_role
    on community_memberships (fk_community, role);

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
