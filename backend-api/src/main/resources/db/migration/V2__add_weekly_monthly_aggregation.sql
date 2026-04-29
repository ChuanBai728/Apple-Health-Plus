create table if not exists health_metric_weekly (
    id bigserial primary key,
    user_id uuid not null,
    upload_id uuid not null references uploads(id) on delete cascade,
    metric_key varchar(128) not null,
    week_start date not null,
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

create unique index if not exists uq_health_metric_weekly_upload_metric_week
    on health_metric_weekly (upload_id, metric_key, week_start);

create table if not exists health_metric_monthly (
    id bigserial primary key,
    user_id uuid not null,
    upload_id uuid not null references uploads(id) on delete cascade,
    metric_key varchar(128) not null,
    month_start date not null,
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

create unique index if not exists uq_health_metric_monthly_upload_metric_month
    on health_metric_monthly (upload_id, metric_key, month_start);
