-- ============================================================================
-- Semana 04 (extensão 2026-09-28) — seguir com aprovação.
--
-- Quem liga `follow_approval_required` passa a receber PEDIDOS: seguir essa
-- conta cria uma linha em user_follow_requests em vez de user_follows. O
-- destinatário aceita (vira user_follows e o pedido some) ou recusa; quem
-- pediu pode cancelar. Pedido não é seguidor: nenhuma regra de visibilidade
-- (feed FOLLOWERS_ONLY, partes do perfil) considera pedidos pendentes.
-- ============================================================================

alter table user_privacy_settings
    add column if not exists follow_approval_required boolean not null default false;

create table if not exists user_follow_requests (
    id            uuid                        not null,
    fk_requester  uuid                        not null,
    fk_target     uuid                        not null,
    created_at    timestamp(6) with time zone not null,
    constraint pk_user_follow_requests primary key (id),
    constraint uq_user_follow_requests_pair unique (fk_requester, fk_target),
    constraint fk_user_follow_requests_requester
        foreign key (fk_requester) references user_profiles (id) on delete cascade,
    constraint fk_user_follow_requests_target
        foreign key (fk_target) references user_profiles (id) on delete cascade,
    constraint ck_user_follow_requests_not_self check (fk_requester <> fk_target)
);

-- Caixa de entrada do destinatário (mais recentes primeiro).
create index if not exists idx_user_follow_requests_target on user_follow_requests (fk_target, created_at desc);
