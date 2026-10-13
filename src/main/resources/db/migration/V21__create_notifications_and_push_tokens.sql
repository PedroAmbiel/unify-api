-- ============================================================================
-- Semana 05 — sistema de notificações (in-app + push via Firebase Cloud
-- Messaging).
--
-- notifications: caixa de entrada do usuário. `reference_id` aponta para
-- entidades diferentes conforme `type` (match, conversa, perfil, post,
-- denúncia), por isso não tem FK — o app trata destino que sumiu ("conteúdo
-- não está mais disponível"). `context_id` complementa o destino quando uma
-- chave só não basta (comunidade do post, perfil de quem mandou a mensagem).
--
-- `fk_actor_user` é quem causou o evento: alimenta o avatar da lista, a
-- deduplicação (curtir/descurtir/curtir não gera três avisos) e o filtro de
-- bloqueio na leitura (bloqueei alguém: os avisos dessa pessoa somem).
--
-- `event_count` + `created_at` móvel: mensagens de chat da mesma conversa
-- ainda não lidas se acumulam numa notificação só ("3 novas mensagens"),
-- que volta para o topo a cada mensagem nova.
--
-- user_push_tokens: token FCM do aparelho. O token é único no mundo; se o
-- mesmo aparelho logar com outra conta, a linha muda de dono (upsert no
-- serviço) em vez de duplicar.
-- ============================================================================

create table if not exists notifications (
    id                 uuid                        not null,
    fk_recipient_user  uuid                        not null,
    fk_actor_user      uuid,
    type               varchar(40)                 not null,
    reference_id       uuid,
    context_id         uuid,
    title              varchar(140)                not null,
    body               varchar(500)                not null,
    event_count        integer                     not null default 1,
    read_at            timestamp(6) with time zone,
    created_at         timestamp(6) with time zone not null,
    constraint pk_notifications primary key (id),
    constraint fk_notifications_recipient_user
        foreign key (fk_recipient_user) references users (id) on delete cascade,
    constraint fk_notifications_actor_user
        foreign key (fk_actor_user) references users (id) on delete set null,
    constraint ck_notifications_event_count check (event_count >= 1)
);

-- Caixa de entrada paginada (mais recentes primeiro).
create index if not exists idx_notifications_recipient_created
    on notifications (fk_recipient_user, created_at desc, id desc);

-- Badge do sino: só as não lidas.
create index if not exists idx_notifications_recipient_unread
    on notifications (fk_recipient_user)
    where read_at is null;

-- Deduplicação/agrupamento por destino.
create index if not exists idx_notifications_recipient_type_reference
    on notifications (fk_recipient_user, type, reference_id);

create table if not exists user_push_tokens (
    id          uuid                        not null,
    fk_user     uuid                        not null,
    token       varchar(512)                not null,
    platform    varchar(20)                 not null,
    created_at  timestamp(6) with time zone not null,
    updated_at  timestamp(6) with time zone not null,
    constraint pk_user_push_tokens primary key (id),
    constraint uq_user_push_tokens_token unique (token),
    constraint fk_user_push_tokens_user
        foreign key (fk_user) references users (id) on delete cascade,
    constraint ck_user_push_tokens_platform check (platform in ('ANDROID', 'IOS'))
);

create index if not exists idx_user_push_tokens_user on user_push_tokens (fk_user);
