-- ============================================================================
-- Cooldown de recusa de match
-- Antes: UserMatchCleanupService deletava as recusas a cada 10h, fazendo perfis
-- recusados reaparecerem no feed. Agora a recusa é persistida com data e só é
-- removida depois da carência (unify.match.decline-cooldown-days).
-- Tipo de declined_at igual ao de user_possible_matches.created_at (V1__baseline).
-- ============================================================================

alter table user_possible_matches
    add column if not exists declined_at timestamp(6) with time zone;

alter table user_possible_matches
    add column if not exists fk_declined_by_user_profile uuid;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_user_possible_matches_declined_by_user_profile'
    ) then
        alter table user_possible_matches
            add constraint fk_user_possible_matches_declined_by_user_profile
                foreign key (fk_declined_by_user_profile)
                references user_profiles (id)
                on delete set null;
    end if;
end
$$;

create index if not exists idx_user_possible_matches_declined_at
    on user_possible_matches (declined_at)
    where declined_at is not null;

-- Backfill: recusas já existentes (pending_accepted = false) recebem a carência
-- a partir da data de criação do registro.
update user_possible_matches
   set declined_at = created_at,
       fk_declined_by_user_profile = fk_pending_user_profile
 where pending_accepted = false
   and declined_at is null;
