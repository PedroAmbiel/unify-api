-- ARQUIVO: src/main/resources/db/migration/V4__create_community_categories.sql
--
-- Entrega B (plano 01-SEMANA-12-08-a-26-08.md, secao 4.B):
-- catalogo de categorias de comunidade + vinculo opcional em `communities`.

create table community_categories (
    id integer not null,
    description varchar(100) not null,
    ionic_icon varchar(60),
    constraint pk_community_categories primary key (id),
    constraint uq_community_categories_description unique (description)
);

alter table communities add column fk_category integer;
alter table communities add constraint fk_communities_category foreign key (fk_category) references community_categories (id);
create index idx_communities_category on communities (fk_category);

insert into community_categories (id, description, ionic_icon) values
    (1, 'Esportes Adaptados', 'basketball-outline'),
    (2, 'Arte e Cultura', 'color-palette-outline'),
    (3, 'Tecnologia Assistiva', 'hardware-chip-outline'),
    (4, 'Apoio e Bem-estar', 'heart-outline'),
    (5, 'Educação e Estudos', 'school-outline'),
    (6, 'Trabalho e Empreendedorismo', 'briefcase-outline'),
    (7, 'Jogos e Games Acessíveis', 'game-controller-outline'),
    (8, 'Música e Podcasts', 'musical-notes-outline'),
    (9, 'Viagem e Mobilidade Urbana', 'airplane-outline'),
    (10, 'Relacionamentos e Amizade', 'people-outline');
