-- ============================================================================
-- Índices para as queries nativas do discovery
-- Referência: UserMatchServiceImplementation.executeCandidateQuery,
--             executeNoLocationModeQuery e executeNoCoordinateCandidateQuery
-- ============================================================================

-- 1) Filtro de gênero do candidato (CTE gender_filtered)
create index if not exists idx_user_profiles_gender
    on user_profiles (fk_gender);

-- 2) Faixa etária: só é indexável depois da reescrita do predicado para range de datas.
--    Índice parcial porque a query sempre exige u.verified = true.
create index if not exists idx_users_verified_birthdate
    on users (birthdate)
    where verified = true;

-- 3) Bounding box de coordenadas (pré-filtro antes do haversine)
create index if not exists idx_user_coordinates_active_lat_lon
    on user_coordinates (latitude, longitude)
    where active = true;

-- 4) Lookup de coordenada ativa por perfil
create index if not exists idx_user_coordinates_profile_active
    on user_coordinates (fk_user_profile)
    where active = true;

-- 5) Exclusão de perfis já decididos (NOT EXISTS do discovery)
create index if not exists idx_user_possible_matches_starter_pending_state
    on user_possible_matches (fk_starter_user_profile, fk_pending_user_profile, pending_accepted);

create index if not exists idx_user_possible_matches_pending_starter_state
    on user_possible_matches (fk_pending_user_profile, fk_starter_user_profile, pending_accepted);

-- 6) Estatísticas: o planner precisa saber a distribuição de birthdate/coordenadas
analyze users;
analyze user_coordinates;
analyze user_profiles;
analyze user_possible_matches;
