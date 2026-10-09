-- Lightspeed deleted-orders canonical entity (single-source, no reconciliation).
-- One row per deleted order; sale_number is the unique natural key.

CREATE TABLE canonical_deleted_sale (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    trading_date date NOT NULL,
    sale_number varchar(255) NOT NULL,
    order_type varchar(255),
    note varchar(512),
    total_inc_tax numeric(14,4) NOT NULL,
    total_ex_tax numeric(14,4) NOT NULL,
    total_tax numeric(14,4) NOT NULL,
    total_cost numeric(14,4),
    opened_register_code varchar(255),
    opened_register_name varchar(255),
    deleted_register_code varchar(255),
    deleted_register_name varchar(255),
    staff_name varchar(255),
    staff_code varchar(255),
    deleted_by_staff_name varchar(255),
    deleted_by_staff_code varchar(255),
    table_number varchar(255),
    site_id varchar(255),
    customer_name varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX uq_deleted_sale_current_source_fact
    ON canonical_deleted_sale (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX ix_deleted_sale_logical_current
    ON canonical_deleted_sale (logical_entity_id) WHERE superseded_at IS NULL;
