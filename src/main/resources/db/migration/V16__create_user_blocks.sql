-- ============================================================================
-- Semana 04 — bloqueio entre usuários.
--
-- A linha é direcional (quem bloqueou quem, para listar "Pessoas bloqueadas"),
-- mas o efeito é simétrico: discovery, perfil público, feed, follow e chat
-- consultam o par nas duas direções — daí os dois índices.
-- ============================================================================

create table if not exists user_blocks (
    id          uuid                        not null,
    fk_blocker  uuid                        not null,
    fk_blocked  uuid                        not null,
    created_at  timestamp(6) with time zone not null default now(),
    constraint pk_user_blocks primary key (id),
    constraint uq_user_blocks_blocker_blocked unique (fk_blocker, fk_blocked),
    constraint fk_user_blocks_blocker
        foreign key (fk_blocker) references users (id) on delete cascade,
    constraint fk_user_blocks_blocked
        foreign key (fk_blocked) references users (id) on delete cascade,
    constraint ck_user_blocks_not_self check (fk_blocker <> fk_blocked)
);

-- (fk_blocker, fk_blocked) já é coberto pela unique; o sentido inverso precisa do seu.
create index if not exists idx_user_blocks_blocked on user_blocks (fk_blocked, fk_blocker);
