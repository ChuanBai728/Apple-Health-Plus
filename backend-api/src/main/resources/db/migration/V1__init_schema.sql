create table if not exists uploads (
    id uuid primary key,
    user_id uuid not null,
    file_name varchar(256) not null,
    storage_key varchar(512) not null,
    file_size bigint not null,
    status varchar(32) not null,
    message_id varchar(128),
    retry_count integer not null default 0,
    last_error varchar(1000),
    next_retry_at timestamp with time zone,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    record_count bigint not null default 0,
    source_count integer not null default 0,
    coverage_start_at timestamp with time zone,
    coverage_end_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create table if not exists parse_jobs (
    id uuid primary key,
    upload_id uuid not null references uploads(id) on delete cascade,
    job_type varchar(32) not null,
    status varchar(32) not null,
    message_id varchar(128),
    retry_count integer not null default 0,
    last_error varchar(1000),
    next_retry_at timestamp with time zone,
    progress_percent integer not null default 0,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create table if not exists health_records (
    id bigserial primary key,
    user_id uuid not null,
    upload_id uuid not null references uploads(id) on delete cascade,
    record_type varchar(32) not null,
    metric_key varchar(128) not null,
    category_key varchar(64) not null,
    source_name varchar(256),
    value_numeric double precision,
    value_text varchar(255),
    unit varchar(64),
    start_at timestamp with time zone,
    end_at timestamp with time zone,
    raw_payload_json text,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_health_records_upload_metric_start
    on health_records (upload_id, metric_key, start_at);

create index if not exists idx_health_records_user_metric_start
    on health_records (user_id, metric_key, start_at);

create index if not exists idx_health_records_upload_category
    on health_records (upload_id, category_key);

create table if not exists health_metric_daily (
    id bigserial primary key,
    user_id uuid not null,
    upload_id uuid not null references uploads(id) on delete cascade,
    metric_key varchar(128) not null,
    date date not null,
    value_avg double precision,
    value_min double precision,
    value_max double precision,
    value_sum double precision,
    sample_count integer not null default 0,
    baseline_avg_30d double precision,
    trend_delta_7d double precision,
    trend_delta_30d double precision,
    anomaly_flag boolean not null default false,
    created_at timestamp with time zone not null default now()
);

create unique index if not exists uq_health_metric_daily_upload_metric_date
    on health_metric_daily (upload_id, metric_key, date);
