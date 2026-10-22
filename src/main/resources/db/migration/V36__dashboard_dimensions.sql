-- ============================================================================
-- Semana 07 (ajuste) — dashboards por dimensão.
--
--  user_activity_days : um registro por usuário por dia (America/Sao_Paulo) em
--                       que ele usou o app autenticado. Base de "usuários
--                       ativos", "inativos", frequência de uso e retenção.
--                       Gravado pela API na primeira requisição autenticada do
--                       dia (UserActivityTracker). As sessões (active_connections)
--                       não servem para isso: as revogadas são apagadas em 7 dias.
--                       O backfill abaixo reconstrói o histórico a partir dos
--                       eventos que já têm data (cadastro, sessões, mensagens,
--                       publicações, curtidas, comentários, decisões de match e
--                       follows).
--
--  communities.created_at : data de criação da comunidade (série "comunidades
--                       criadas"). Comunidades anteriores herdam a entrada mais
--                       antiga entre os membros (o dono entra na criação).
--
-- Em %dev/%test (Hibernate drop-and-create) a tabela e a coluna saem das
-- entidades UserActivityDay e Community; os índices estão no import.sql.
-- ============================================================================

create table if not exists user_activity_days (
    fk_user       uuid not null,
    activity_date date not null,
    constraint pk_user_activity_days primary key (fk_user, activity_date),
    constraint fk_user_activity_days_user foreign key (fk_user) references users (id) on delete cascade
);

create index if not exists idx_user_activity_days_date
    on user_activity_days (activity_date);

insert into user_activity_days (fk_user, activity_date)
select user_id, day
from (
    select u.id as user_id, (u.created_at at time zone 'America/Sao_Paulo')::date as day
      from users u
    union
    select ac.fk_user, (ac.created_at at time zone 'America/Sao_Paulo')::date
      from active_connections ac
    union
    select sp.fk_user, (cm.created_at at time zone 'America/Sao_Paulo')::date
      from chat_messages cm
      join user_profiles sp on sp.id = cm.fk_sender_user_profile
    union
    select p.fk_user, (p.created_at at time zone 'America/Sao_Paulo')::date
      from posts p
    union
    select l.fk_user, (l.created_at at time zone 'America/Sao_Paulo')::date
      from post_likes l
    union
    select c.fk_user, (c.created_at at time zone 'America/Sao_Paulo')::date
      from post_comments c
    union
    select sp.fk_user, (m.created_at at time zone 'America/Sao_Paulo')::date
      from user_possible_matches m
      join user_profiles sp on sp.id = m.fk_starter_user_profile
    union
    select pp.fk_user, (coalesce(m.matched_at, m.declined_at) at time zone 'America/Sao_Paulo')::date
      from user_possible_matches m
      join user_profiles pp on pp.id = m.fk_pending_user_profile
     where coalesce(m.matched_at, m.declined_at) is not null
    union
    select fp.fk_user, (f.created_at at time zone 'America/Sao_Paulo')::date
      from user_follows f
      join user_profiles fp on fp.id = f.fk_follower
) as events
on conflict do nothing;

alter table communities add column if not exists created_at timestamp(6) with time zone;

update communities c
   set created_at = coalesce(
           (select min(m.joined_at) from community_memberships m where m.fk_community = c.id),
           now())
 where c.created_at is null;

alter table communities alter column created_at set default now();
alter table communities alter column created_at set not null;

create index if not exists idx_communities_created_at
    on communities (created_at);

create index if not exists idx_community_memberships_joined_at
    on community_memberships (joined_at);
