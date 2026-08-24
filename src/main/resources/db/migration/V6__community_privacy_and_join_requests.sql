-- ARQUIVO: src/main/resources/db/migration/V6__community_privacy_and_join_requests.sql
--
-- Comunidades publicas/privadas:
--  * `privacy` em communities (PUBLIC entra direto; PRIVATE gera solicitacao pendente).
--  * `community_join_requests` guarda a fila de solicitacoes pendentes de
--    comunidades privadas; aprovar vira membership e remove a linha.
alter table communities
    add column if not exists privacy varchar(20) not null default 'PUBLIC'
        check (privacy in ('PUBLIC', 'PRIVATE'));

create table if not exists community_join_requests (
    requested_at timestamp(6) with time zone not null,
    fk_community uuid not null,
    fk_user_profile uuid not null,
    id uuid not null,
    primary key (id),
    constraint uq_community_join_request_community_profile unique (fk_community, fk_user_profile)
);

alter table community_join_requests
    add constraint fk_community_join_requests_community
        foreign key (fk_community)
        references communities;

alter table community_join_requests
    add constraint fk_community_join_requests_user_profile
        foreign key (fk_user_profile)
        references user_profiles;

create index if not exists idx_community_join_requests_community
    on community_join_requests (fk_community, requested_at asc, id asc);
