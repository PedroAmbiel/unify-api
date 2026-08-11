create table user_accessibility_settings (
    id uuid not null,
    fk_user uuid not null,
    font_scale varchar(20) not null default 'MEDIUM',
    high_contrast boolean not null default false,
    screen_reader_optimized boolean not null default false,
    reduce_motion boolean not null default false,
    last_updated_at timestamp not null default now(),
    constraint pk_user_accessibility_settings primary key (id),
    constraint uq_user_accessibility_settings_user unique (fk_user),
    constraint fk_user_accessibility_settings_user foreign key (fk_user) references users (id)
);

create index idx_user_accessibility_settings_user on user_accessibility_settings (fk_user);
