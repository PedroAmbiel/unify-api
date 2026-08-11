-- ARQUIVO: src/main/resources/db/migration/V1__baseline.sql
--
-- Baseline do schema. Gerado a partir das entidades JPA com
--   ./mvnw quarkus:dev -Dquarkus.hibernate-orm.scripts.generation=create \
--                      -Dquarkus.hibernate-orm.scripts.generation.create-target=target/schema-baseline.sql
-- e complementado com os indices que o Hibernate NAO gera (ver fim do arquivo).
--
-- NAO EDITAR. Migracoes ja aplicadas nunca sao alteradas: crie um novo
-- V<n>__descricao.sql para qualquer mudanca de schema.


    create table accessibility_needs (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table active_connections (
        revoked boolean not null,
        created_at timestamp(6) with time zone not null,
        expires_at timestamp(6) with time zone not null,
        fk_user uuid not null,
        id uuid not null,
        device_info varchar(255),
        ip_address varchar(255),
        refresh_token varchar(255) not null unique,
        primary key (id)
    );

    create table autonomy_levels (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table communication_forms (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table communities (
        active boolean not null,
        featured boolean not null,
        fk_owner_user uuid not null,
        id uuid not null,
        description varchar(2000),
        name varchar(255) not null,
        icon_oid oid,
        primary key (id)
    );

    create table community_memberships (
        joined_at timestamp(6) with time zone not null,
        fk_community uuid not null,
        fk_user_profile uuid not null,
        id uuid not null,
        role varchar(20) not null check ((role in ('ADMIN','MODERATOR','MEMBER'))),
        primary key (id),
        constraint uq_community_membership_community_profile unique (fk_community, fk_user_profile)
    );

    create table community_post_comments (
        created_at timestamp(6) with time zone not null,
        fk_post uuid not null,
        fk_user uuid not null,
        id uuid not null,
        body varchar(2000) not null,
        primary key (id)
    );

    create table community_post_likes (
        created_at timestamp(6) with time zone not null,
        fk_post uuid not null,
        fk_user uuid not null,
        id uuid not null,
        primary key (id),
        constraint uq_community_post_like_post_user unique (fk_post, fk_user)
    );

    create table community_posts (
        created_at timestamp(6) with time zone not null,
        fk_community uuid not null,
        fk_user uuid not null,
        id uuid not null,
        body varchar(4000) not null,
        media_oid oid,
        primary key (id)
    );

    create table connection_types (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table disabilities (
        id integer not null,
        description varchar(255) not null unique,
        ionic_icon varchar(255) not null,
        primary key (id)
    );

    create table email_verification_codes (
        consumed_at timestamp(6) with time zone,
        created_at timestamp(6) with time zone not null,
        disabled_at timestamp(6) with time zone,
        expires_at timestamp(6) with time zone not null,
        fk_user uuid not null,
        id uuid not null,
        code_hash varchar(255) not null,
        primary key (id)
    );

    create table energy_levels (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table genders (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table interest_types (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table lifestyle_types (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table love_languages (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table password_reset_tokens (
        consumed_at timestamp(6) with time zone,
        created_at timestamp(6) with time zone not null,
        disabled_at timestamp(6) with time zone,
        expires_at timestamp(6) with time zone not null,
        fk_user uuid not null,
        id uuid not null,
        token_hash varchar(255) not null,
        primary key (id)
    );

    create table pronouns (
        id integer not null,
        description varchar(255) not null unique,
        primary key (id)
    );

    create table user_coordinates (
        active boolean not null,
        latitude numeric(9,6) not null,
        longitude numeric(9,6) not null,
        fk_user_profile uuid not null,
        id uuid not null,
        primary key (id)
    );

    create table user_match_preference_desired_genders (
        fk_gender integer not null,
        fk_user_match_preference uuid not null,
        primary key (fk_gender, fk_user_match_preference)
    );

    create table user_match_preferences (
        fk_connection_type integer,
        max_age integer,
        max_match_distance_km integer,
        min_age integer,
        fk_user_profile uuid not null unique,
        id uuid not null,
        accessibility_need_similarity varchar(255) check ((accessibility_need_similarity in ('ANY','SIMILAR','DIFFERENT'))),
        autonomy_compatibility varchar(255) check ((autonomy_compatibility in ('ANY','SIMILAR','DIFFERENT'))),
        energy_level_similarity varchar(255) check ((energy_level_similarity in ('ANY','SIMILAR','DIFFERENT'))),
        lifestyle_similarity varchar(255) check ((lifestyle_similarity in ('ANY','SIMILAR','DIFFERENT'))),
        love_language_similarity varchar(255) check ((love_language_similarity in ('ANY','SIMILAR','DIFFERENT'))),
        primary key (id)
    );

    create table user_possible_matches (
        pending_accepted boolean,
        starter_accepted boolean not null,
        created_at timestamp(6) with time zone not null,
        fk_pending_user_profile uuid not null,
        fk_starter_user_profile uuid not null,
        id uuid not null,
        primary key (id),
        constraint uq_user_possible_matches_starter_pending unique (fk_starter_user_profile, fk_pending_user_profile)
    );

    create table user_profile_accessibility_needs (
        fk_accessibility_need integer not null,
        fk_user_profile uuid not null,
        primary key (fk_accessibility_need, fk_user_profile)
    );

    create table user_profile_communication_forms (
        fk_communication_form integer not null,
        fk_user_profile uuid not null,
        primary key (fk_communication_form, fk_user_profile)
    );

    create table user_profile_disabilities (
        fk_disability integer not null,
        fk_user_profile uuid not null,
        primary key (fk_disability, fk_user_profile)
    );

    create table user_profile_images (
        active boolean not null,
        is_profile_pic boolean not null,
        fk_user_profile uuid not null,
        id uuid not null,
        oid oid not null,
        primary key (id)
    );

    create table user_profile_interest_types (
        fk_interest_type integer not null,
        fk_user_profile uuid not null,
        primary key (fk_interest_type, fk_user_profile)
    );

    create table user_profile_lifestyle_types (
        fk_lifestyle_type integer not null,
        fk_user_profile uuid not null,
        primary key (fk_lifestyle_type, fk_user_profile)
    );

    create table user_profile_love_languages (
        fk_love_language integer not null,
        fk_user_profile uuid not null,
        primary key (fk_love_language, fk_user_profile)
    );

    create table user_profiles (
        fk_autonomy_level integer,
        fk_energy_level integer,
        fk_gender integer,
        fk_pronouns integer,
        fk_user uuid not null unique,
        id uuid not null,
        bio varchar(255),
        primary key (id)
    );

    create table users (
        birthdate date not null,
        verified boolean not null,
        last_updated_at timestamp(6) with time zone not null,
        id uuid not null,
        cellphone varchar(255) unique,
        email varchar(255) not null unique,
        last_name varchar(255) not null,
        name varchar(255) not null,
        password varchar(255) not null,
        primary key (id)
    );

    create index idx_email_verification_user_code 
       on email_verification_codes (fk_user, code_hash);

    create index idx_email_verification_expires_at 
       on email_verification_codes (expires_at);

    create index idx_password_reset_token_hash 
       on password_reset_tokens (token_hash);

    create index idx_password_reset_expires_at 
       on password_reset_tokens (expires_at);

    create index idx_password_reset_user 
       on password_reset_tokens (fk_user);

    create index idx_user_coordinates_profile 
       on user_coordinates (fk_user_profile);

    create index idx_user_coordinates_active 
       on user_coordinates (active);

    create index idx_user_possible_matches_starter 
       on user_possible_matches (fk_starter_user_profile);

    create index idx_user_possible_matches_pending 
       on user_possible_matches (fk_pending_user_profile);

    create index idx_user_possible_matches_pending_answer 
       on user_possible_matches (pending_accepted);

    alter table if exists active_connections 
       add constraint fk_active_connections_user 
       foreign key (fk_user) 
       references users;

    alter table if exists communities 
       add constraint fk_communities_owner_user 
       foreign key (fk_owner_user) 
       references users;

    alter table if exists community_memberships 
       add constraint fk_community_memberships_community 
       foreign key (fk_community) 
       references communities;

    alter table if exists community_memberships 
       add constraint fk_community_memberships_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists community_post_comments 
       add constraint fk_community_post_comments_user 
       foreign key (fk_user) 
       references users;

    alter table if exists community_post_comments 
       add constraint fk_community_post_comments_post 
       foreign key (fk_post) 
       references community_posts;

    alter table if exists community_post_likes 
       add constraint fk_community_post_likes_post 
       foreign key (fk_post) 
       references community_posts;

    alter table if exists community_post_likes 
       add constraint fk_community_post_likes_user 
       foreign key (fk_user) 
       references users;

    alter table if exists community_posts 
       add constraint fk_community_posts_user 
       foreign key (fk_user) 
       references users;

    alter table if exists community_posts 
       add constraint fk_community_posts_community 
       foreign key (fk_community) 
       references communities;

    alter table if exists email_verification_codes 
       add constraint fk_email_verification_codes_user 
       foreign key (fk_user) 
       references users;

    alter table if exists password_reset_tokens 
       add constraint fk_password_reset_tokens_user 
       foreign key (fk_user) 
       references users;

    alter table if exists user_coordinates 
       add constraint fk_user_coordinates_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_match_preference_desired_genders 
       add constraint fk_user_match_preference_desired_genders_gender 
       foreign key (fk_gender) 
       references genders;

    alter table if exists user_match_preference_desired_genders 
       add constraint fk_user_match_preference_desired_genders_user_match_preference 
       foreign key (fk_user_match_preference) 
       references user_match_preferences;

    alter table if exists user_match_preferences 
       add constraint fk_user_match_preferences_connection_type 
       foreign key (fk_connection_type) 
       references connection_types;

    alter table if exists user_match_preferences 
       add constraint fk_user_match_preferences_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_possible_matches 
       add constraint fk_user_possible_matches_pending_user_profile 
       foreign key (fk_pending_user_profile) 
       references user_profiles;

    alter table if exists user_possible_matches 
       add constraint fk_user_possible_matches_starter_user_profile 
       foreign key (fk_starter_user_profile) 
       references user_profiles;

    alter table if exists user_profile_accessibility_needs 
       add constraint fk_user_profile_accessibility_needs_accessibility_need 
       foreign key (fk_accessibility_need) 
       references accessibility_needs;

    alter table if exists user_profile_accessibility_needs 
       add constraint fk_user_profile_accessibility_needs_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_communication_forms 
       add constraint fk_user_profile_communication_forms_communication_form 
       foreign key (fk_communication_form) 
       references communication_forms;

    alter table if exists user_profile_communication_forms 
       add constraint fk_user_profile_communication_forms_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_disabilities 
       add constraint fk_user_profile_disabilities_disability 
       foreign key (fk_disability) 
       references disabilities;

    alter table if exists user_profile_disabilities 
       add constraint fk_user_profile_disabilities_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_images 
       add constraint fk_user_profile_images_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_interest_types 
       add constraint fk_user_profile_interest_types_interest_type 
       foreign key (fk_interest_type) 
       references interest_types;

    alter table if exists user_profile_interest_types 
       add constraint fk_user_profile_interest_types_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_lifestyle_types 
       add constraint fk_user_profile_lifestyle_types_lifestyle_type 
       foreign key (fk_lifestyle_type) 
       references lifestyle_types;

    alter table if exists user_profile_lifestyle_types 
       add constraint fk_user_profile_lifestyle_types_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profile_love_languages 
       add constraint fk_user_profile_love_languages_love_language 
       foreign key (fk_love_language) 
       references love_languages;

    alter table if exists user_profile_love_languages 
       add constraint fk_user_profile_love_languages_user_profile 
       foreign key (fk_user_profile) 
       references user_profiles;

    alter table if exists user_profiles 
       add constraint fk_user_profiles_autonomy_level 
       foreign key (fk_autonomy_level) 
       references autonomy_levels;

    alter table if exists user_profiles 
       add constraint fk_user_profiles_energy_level 
       foreign key (fk_energy_level) 
       references energy_levels;

    alter table if exists user_profiles 
       add constraint fk_user_profiles_gender 
       foreign key (fk_gender) 
       references genders;

    alter table if exists user_profiles 
       add constraint fk_user_profiles_pronouns 
       foreign key (fk_pronouns) 
       references pronouns;

    alter table if exists user_profiles 
       add constraint fk_user_profiles_user 
       foreign key (fk_user) 
       references users;


-- ---------------------------------------------------------------------------
-- Indices que o Hibernate nao gera. Migrados literalmente de import.sql.
-- Os dois primeiros sao indices PARCIAIS que sustentam regras de negocio:
-- no maximo uma coordenada ativa e uma foto de perfil ativa por usuario.
-- Sem eles o banco aceita duplicatas silenciosamente.
-- ---------------------------------------------------------------------------

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
