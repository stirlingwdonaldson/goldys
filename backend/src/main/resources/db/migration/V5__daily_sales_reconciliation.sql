-- Daily sales (per-source daily totals) and manual overrides for the reconciliation slice.

CREATE TABLE canonical_daily_sales (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    trading_date date NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    total_sales numeric(14,4) NOT NULL,
    gst_total numeric(14,4) NOT NULL,
    net_total numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_daily_sales_current
    ON canonical_daily_sales (trading_date, source_system)
    WHERE superseded_at IS NULL;
CREATE INDEX ix_daily_sales_logical
    ON canonical_daily_sales (logical_entity_id) WHERE superseded_at IS NULL;

CREATE TABLE daily_sales_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    authoritative_source varchar(255) NOT NULL,
    reason text,
    actor_oidc_issuer varchar(512) NOT NULL,
    actor_oidc_subject varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_daily_sales_override_current
    ON daily_sales_override (trading_date) WHERE superseded_at IS NULL;
