-- Daily-sales read model: disposable resolved-value and exception projections.
-- Reconstructed from canonical_daily_sales + daily_sales_override + resolution_rule by
-- DailySalesProjector. Never a source of truth; safe to truncate and replay.

CREATE TABLE resolved_daily_sales (
    trading_date date PRIMARY KEY,
    total_sales numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE reconciliation_exception (
    entity_type text NOT NULL,
    entity_key text NOT NULL,
    trading_date date NOT NULL,
    field_key text NOT NULL,
    status text NOT NULL,
    detected_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (entity_type, entity_key, field_key)
);
