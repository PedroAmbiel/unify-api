-- Additional demo users with completed profiles and match preferences

drop view if exists seed_generated_users;

create view seed_generated_users as
with name_catalog as (
    select
        array[
            'Ana', 'Bruno', 'Camila', 'Daniel', 'Elisa',
            'Felipe', 'Gabriela', 'Henrique', 'Isabela', 'Joao',
            'Karen', 'Lucas', 'Marina', 'Nicolas', 'Olivia',
            'Paulo', 'Quezia', 'Rafaela', 'Sergio', 'Talita'
        ]::text[] as first_names,
        array[
            'Silva', 'Souza', 'Costa', 'Oliveira', 'Pereira',
            'Rodrigues', 'Almeida', 'Gomes', 'Barbosa', 'Ribeiro',
            'Cardoso', 'Moura', 'Lima', 'Araujo', 'Ferreira'
        ]::text[] as last_names
)
select
    gs.i as seed_index,
    ('10000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid as user_id,
    ('20000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid as profile_id,
    ('30000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid as preference_id,
    ('40000000-0000-4000-8000-' || lpad(to_hex(gs.i), 12, '0'))::uuid as coordinate_id,
    first_names[((gs.i - 1) % array_length(first_names, 1)) + 1] as first_name,
    last_names[((gs.i - 1) % array_length(last_names, 1)) + 1] as last_name,
    ('seed.user' || lpad(gs.i::text, 2, '0') || '@unify.dev') as email,
    ('5511998' || lpad(gs.i::text, 6, '0')) as cellphone,
    (date '1984-01-01' + ((gs.i - 1) * interval '120 days'))::date as birthdate,
    (gs.i % 2 = 0) as verified,
    (timestamp with time zone '2025-01-01 12:00:00+00' + ((gs.i - 1) * interval '1 day')) as last_updated_at,
    (
        first_names[((gs.i - 1) % array_length(first_names, 1)) + 1]
        || ' gosta de conversas leves, lugares acessiveis e experiencias autenticas.'
    ) as bio,
    ((gs.i - 1) % 4) + 1 as gender_id,
    case
        when ((gs.i - 1) % 4) + 1 in (1, 2, 3) then ((gs.i - 1) % 4) + 1
        else 4
    end as pronouns_id,
    ((gs.i - 1) % 4) + 1 as disability_primary_id,
    (gs.i % 4) + 1 as disability_secondary_id,
    ((gs.i - 1) % 4) + 1 as accessibility_need_primary_id,
    (gs.i % 4) + 1 as accessibility_need_secondary_id,
    ((gs.i - 1) % 3) + 1 as autonomy_level_id,
    ((gs.i - 1) % 5) + 1 as communication_form_primary_id,
    (gs.i % 5) + 1 as communication_form_secondary_id,
    ((gs.i - 1) % 4) + 1 as lifestyle_type_primary_id,
    (gs.i % 4) + 1 as lifestyle_type_secondary_id,
    ((gs.i - 1) % 5) + 1 as love_language_primary_id,
    (gs.i % 5) + 1 as love_language_secondary_id,
    ((gs.i - 1) % 3) + 1 as energy_level_id,
    ((gs.i - 1) % 6) + 1 as interest_type_primary_id,
    (gs.i % 6) + 1 as interest_type_secondary_id,
    ((gs.i - 1) % 4) + 1 as connection_type_id,
    case gs.i % 3
        when 1 then 'SIMILAR'
        when 2 then 'ANY'
        else 'DIFFERENT'
    end as accessibility_need_similarity,
    case (gs.i + 1) % 3
        when 1 then 'SIMILAR'
        when 2 then 'ANY'
        else 'DIFFERENT'
    end as autonomy_compatibility,
    case (gs.i + 2) % 3
        when 1 then 'SIMILAR'
        when 2 then 'ANY'
        else 'DIFFERENT'
    end as lifestyle_similarity,
    case gs.i % 2
        when 0 then 'SIMILAR'
        else 'ANY'
    end as love_language_similarity,
    case (gs.i + 1) % 2
        when 0 then 'SIMILAR'
        else 'ANY'
    end as energy_level_similarity,
    21 + ((gs.i - 1) % 7) as min_age,
    29 + ((gs.i - 1) % 9) as max_age,
    10 + (((gs.i - 1) % 8) * 5) as max_match_distance_km,
    (gs.i % 4) + 1 as desired_gender_primary_id,
    ((gs.i + 1) % 4) + 1 as desired_gender_secondary_id,
    round((37.421998 + ((((gs.i - 1) % 10)::numeric - 4.5) * 0.000120)), 6)::numeric(9, 6) as latitude,
    round((-122.084000 + ((floor((gs.i - 1) / 10.0) - 2.0) * 0.000160)), 6)::numeric(9, 6) as longitude
from generate_series(1, 50) as gs(i)
cross join name_catalog;

insert into users (
    id,
    verified,
    last_updated_at,
    birthdate,
    email,
    cellphone,
    last_name,
    name,
    password
)
select
    user_id,
    verified,
    last_updated_at,
    birthdate,
    email,
    cellphone,
    last_name,
    first_name,
    -- Hashes bcrypt DISTINTOS rotacionados por seed_index: uma senha vazada
    -- nao abre todas as contas de demo. Senhas: SeedDemo01! .. SeedDemo10!,
    -- conforme ((seed_index - 1) % 10) + 1. Documentado em import-dev.sql.
    (array[
        '$2a$10$4FnGlJ3BIOViptDsSL0.2eIAWhVBFpxdw2TrtDPux6Zn9mmGm8dh.',
        '$2a$10$2B1GQh1Y6Vm3eSrMM/EF7.oxsTa.b6kPVaEoshS9nEWTbZCkVtemC',
        '$2a$10$B5btltWQfk/sRutZbNxyPuP8tG3tBXJ7bN6.SDMJe176vqjdoiqcy',
        '$2a$10$zB8RAve5bvn.PteNyWnyruIH5ZomsZ8TlsY5tYIXvohiuIXvBA5Dy',
        '$2a$10$ykxRcxkR.QlCGiPuAE9s7.zpj/VCeHVy6bqyGjqPMlqZczdOLZ4PO',
        '$2a$10$P5/la.nW/a6ysXQEyB.m0uNljRdhd9/MqEUmDNaj52mWtNxtrdrkm',
        '$2a$10$zhbAiCKYma/AYDdqYf79e.k98Zsdc8YF6DbOkLTEPJNKDOnIXfbsu',
        '$2a$10$c87tKGDlQCnd1voBL3QQ1uPe/lQMh68FVdUuokbFTK5disVj7SW/O',
        '$2a$10$hniJlCg7rYI0WvpU2z3IGeuxI/vjYpIPNIoOJgBvYv/5ng53Oe2E6',
        '$2a$10$GveuYFAraO86f9sjFa.7yeRPZfdTUEPCUnMV1r3DGfLMO99/N6xj2'
    ]::text[])[((seed_index - 1) % 10) + 1]
from seed_generated_users;

insert into user_profiles (
    id,
    fk_user,
    bio,
    fk_gender,
    fk_pronouns,
    fk_autonomy_level,
    fk_energy_level
)
select
    profile_id,
    user_id,
    bio,
    gender_id,
    pronouns_id,
    autonomy_level_id,
    energy_level_id
from seed_generated_users;

insert into user_coordinates (
    id,
    fk_user_profile,
    latitude,
    longitude,
    active
)
select
    coordinate_id,
    profile_id,
    latitude,
    longitude,
    true
from seed_generated_users;

insert into user_match_preferences (
    id,
    fk_user_profile,
    fk_connection_type,
    accessibility_need_similarity,
    autonomy_compatibility,
    lifestyle_similarity,
    love_language_similarity,
    energy_level_similarity,
    min_age,
    max_age,
    max_match_distance_km
)
select
    preference_id,
    profile_id,
    connection_type_id,
    accessibility_need_similarity,
    autonomy_compatibility,
    lifestyle_similarity,
    love_language_similarity,
    energy_level_similarity,
    min_age,
    max_age,
    max_match_distance_km
from seed_generated_users;

insert into user_profile_disabilities (fk_user_profile, fk_disability)
select
    profile_id,
    disability_primary_id
from seed_generated_users;

insert into user_profile_disabilities (fk_user_profile, fk_disability)
select
    profile_id,
    disability_secondary_id
from seed_generated_users
where seed_index % 5 = 0;

insert into user_profile_accessibility_needs (fk_user_profile, fk_accessibility_need)
select
    profile_id,
    accessibility_need_primary_id
from seed_generated_users;

insert into user_profile_accessibility_needs (fk_user_profile, fk_accessibility_need)
select
    profile_id,
    accessibility_need_secondary_id
from seed_generated_users
where seed_index % 4 = 0;

insert into user_profile_communication_forms (fk_user_profile, fk_communication_form)
select
    profile_id,
    communication_form_primary_id
from seed_generated_users;

insert into user_profile_communication_forms (fk_user_profile, fk_communication_form)
select
    profile_id,
    communication_form_secondary_id
from seed_generated_users;

insert into user_profile_lifestyle_types (fk_user_profile, fk_lifestyle_type)
select
    profile_id,
    lifestyle_type_primary_id
from seed_generated_users;

insert into user_profile_lifestyle_types (fk_user_profile, fk_lifestyle_type)
select
    profile_id,
    lifestyle_type_secondary_id
from seed_generated_users;

insert into user_profile_love_languages (fk_user_profile, fk_love_language)
select
    profile_id,
    love_language_primary_id
from seed_generated_users;

insert into user_profile_love_languages (fk_user_profile, fk_love_language)
select
    profile_id,
    love_language_secondary_id
from seed_generated_users;

insert into user_profile_interest_types (fk_user_profile, fk_interest_type)
select
    profile_id,
    interest_type_primary_id
from seed_generated_users;

insert into user_profile_interest_types (fk_user_profile, fk_interest_type)
select
    profile_id,
    interest_type_secondary_id
from seed_generated_users;

insert into user_match_preference_desired_genders (fk_user_match_preference, fk_gender)
select
    preference_id,
    desired_gender_primary_id
from seed_generated_users;

insert into user_match_preference_desired_genders (fk_user_match_preference, fk_gender)
select
    preference_id,
    desired_gender_secondary_id
from seed_generated_users;

drop view if exists seed_generated_users;