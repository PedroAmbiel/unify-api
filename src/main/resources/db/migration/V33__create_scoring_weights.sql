-- ============================================================================
-- Pesos de pontuação administráveis pelo backoffice.
--
-- A regra de cada dimensão é código (enum ScoringDimension); o banco guarda só
-- o PESO. A API carrega tudo em memória no boot (ScoringWeightRegistry) e o
-- backoffice altera por PUT /admin/scoring-weights/{grupo}: grava aqui e troca
-- a memória logo depois do commit. Dimensão sem linha usa o padrão do código.
--
--  scoring_weights         : peso por dimensão (nome da constante do enum).
--  scoring_group_revisions : revisão por grupo (MATCH, FEED,
--                            COMMUNITY_DISCOVERY). O save trava a linha do
--                            grupo e recusa (409) quem editava uma revisão
--                            antiga; também guarda quem alterou e quando.
--
-- Auditoria em admin_audit_log (V32): action SCORING_WEIGHTS_UPDATE,
-- target_type SCORING_WEIGHTS, target_id = grupo (ex.: "MATCH").
--
-- Seeds = padrões do código em 2026-10 (MatchScoringPolicy, HomeFeedRankingPolicy
-- e CommunityServiceImplementation.discoverCommunities). Em %dev/%test
-- (Hibernate drop-and-create) os mesmos inserts estão no fim do import.sql.
-- ============================================================================

create table if not exists scoring_weights (
    dimension varchar(60)  not null,
    weight    numeric(7,2) not null,
    constraint pk_scoring_weights primary key (dimension),
    constraint ck_scoring_weights_weight check (weight >= 0)
);

create table if not exists scoring_group_revisions (
    scoring_group varchar(40)                 not null,
    revision      bigint                      not null default 0,
    updated_at    timestamp(6) with time zone,
    fk_updated_by uuid,
    constraint pk_scoring_group_revisions primary key (scoring_group),
    constraint fk_scoring_group_revisions_updated_by
        foreign key (fk_updated_by) references users (id)
);

insert into scoring_group_revisions (scoring_group, revision) values
    ('MATCH', 0),
    ('FEED', 0),
    ('COMMUNITY_DISCOVERY', 0)
on conflict (scoring_group) do nothing;

insert into scoring_weights (dimension, weight) values
    -- Encontros: compatibilidade (somam 100)
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
    -- Encontros: penalidades (pontos)
    ('MATCH_PENALTY_GENDER_NOT_RECIPROCAL', 8),
    ('MATCH_PENALTY_AGE_NOT_RECIPROCAL', 5),
    ('MATCH_PENALTY_RESHOWN_AFTER_COOLDOWN', 10),
    -- Feed da aba Início (pontos; meia-vida em horas)
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
    -- Descoberta de comunidades (pontos de engajamento)
    ('COMMUNITY_DISCOVERY_MEMBER', 3),
    ('COMMUNITY_DISCOVERY_POST', 2),
    ('COMMUNITY_DISCOVERY_LIKE', 1)
on conflict (dimension) do nothing;
