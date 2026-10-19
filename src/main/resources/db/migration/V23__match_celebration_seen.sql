-- ============================================================================
-- Tela "Deu match" para os dois lados do par
-- Antes só quem respondia o convite via a comemoração (na resposta do POST).
-- Agora o app pergunta ao backend quais matches mútuos a pessoa ainda não viu
-- (ao voltar para o app, em intervalos curtos com ele aberto e ao chegar o
-- push NEW_MATCH) e confirma quando a tela foi exibida, para nunca repetir.
-- Tipos iguais ao de user_possible_matches.created_at (V1__baseline).
-- ============================================================================

alter table user_possible_matches
    add column if not exists matched_at timestamp(6) with time zone;

alter table user_possible_matches
    add column if not exists starter_match_seen_at timestamp(6) with time zone;

alter table user_possible_matches
    add column if not exists pending_match_seen_at timestamp(6) with time zone;

-- Backfill: matches mútuos que já existiam contam como vistos pelos dois
-- lados. Sem isso, todo mundo receberia de uma vez a comemoração de matches
-- antigos (que quem respondeu já viu e quem curtiu primeiro já descobriu pela
-- notificação ou pela lista de conexões).
update user_possible_matches
   set matched_at = created_at,
       starter_match_seen_at = coalesce(starter_match_seen_at, now()),
       pending_match_seen_at = coalesce(pending_match_seen_at, now())
 where starter_accepted = true
   and pending_accepted = true
   and matched_at is null;

-- Consulta frequente (polling do app): só as linhas ainda não vistas entram
-- nos índices, que ficam minúsculos.
create index if not exists idx_user_possible_matches_unseen_starter
    on user_possible_matches (fk_starter_user_profile)
    where matched_at is not null and starter_match_seen_at is null;

create index if not exists idx_user_possible_matches_unseen_pending
    on user_possible_matches (fk_pending_user_profile)
    where matched_at is not null and pending_match_seen_at is null;
