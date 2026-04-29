create table if not exists users (
    id uuid primary key,
    username varchar(64) not null unique,
    email varchar(128) not null unique,
    password_hash varchar(255) not null,
    role varchar(32) not null default 'USER',
    enabled boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

-- Populate default users for existing data (backward compat)
insert into users (id, username, email, password_hash, role)
values ('00000000-0000-0000-0000-000000000001', 'demo', 'demo@healthplus.local',
        '$2a$10$dummy', 'USER')
on conflict (id) do nothing;
