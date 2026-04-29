create table if not exists health_report_summaries (
    id uuid primary key,
    upload_id uuid not null references uploads(id) on delete cascade,
    intent varchar(32) not null,
    conclusion text not null,
    evidence jsonb not null default '[]',
    advice jsonb not null default '[]',
    disclaimer text,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_health_report_summaries_upload
    on health_report_summaries (upload_id);

create table if not exists chat_sessions (
    id uuid primary key,
    upload_id uuid references uploads(id) on delete set null,
    title varchar(256),
    created_at timestamp with time zone not null default now()
);

create table if not exists chat_messages (
    id uuid primary key,
    session_id uuid not null references chat_sessions(id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    intent varchar(32),
    evidence jsonb,
    advice jsonb,
    disclaimer text,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_chat_messages_session
    on chat_messages (session_id, created_at);
