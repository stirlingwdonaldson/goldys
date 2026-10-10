-- Lightspeed payments canonical entity (single-source, no reconciliation).
-- One row per payment tender; a sale can be split across several tenders.

CREATE TABLE canonical_payment (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    trading_date date NOT NULL,
    sale_number varchar(255) NOT NULL,
    payment_type_code varchar(255),
    payment_type_name varchar(255),
    payment_source_type varchar(255),
    lspay_payment_mode varchar(255),
    clearing_account varchar(255),
    amount numeric(14,4) NOT NULL,
    tip numeric(14,4) NOT NULL,
    tendered numeric(14,4) NOT NULL,
    surcharge numeric(14,4) NOT NULL,
    payment_count integer NOT NULL,
    tip_count integer NOT NULL,
    reconciled varchar(64) NOT NULL,
    register_code varchar(255),
    register_name varchar(255),
    staff_name varchar(255),
    staff_code varchar(255),
    site_id varchar(255),
    customer_name varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX uq_payment_current_source_fact
    ON canonical_payment (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_payment_logical_current
    ON canonical_payment (logical_entity_id) WHERE superseded_at IS NULL;
