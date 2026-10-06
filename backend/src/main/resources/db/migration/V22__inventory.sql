-- Inventory canonical entities (observations only) + resolved daily read model + manual overrides.
-- Invoices: CSV supplies metadata, PDFs (text-extracted) supply line items. Stock/wastage are
-- modelled but have no ingestion source yet.

CREATE TABLE canonical_invoice (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    supplier_name varchar(255),
    invoice_number varchar(255) NOT NULL,
    invoice_date date NOT NULL,
    due_date date,
    total_amount numeric(14,4),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_invoice_current_source_fact
    ON canonical_invoice (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE canonical_invoice_line (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    invoice_number varchar(255) NOT NULL,
    invoice_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity numeric(14,4) NOT NULL,
    unit_cost numeric(14,4) NOT NULL,
    line_total numeric(14,4) NOT NULL,
    category varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_invoice_line_current_source_fact
    ON canonical_invoice_line (source_system, source_record_ref) WHERE superseded_at IS NULL;

CREATE TABLE canonical_stock_count (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    counted_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity_on_hand numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE TABLE canonical_wastage (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    wastage_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    quantity numeric(14,4) NOT NULL,
    reason varchar(255),
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE TABLE resolved_inventory_day (
    trading_date date PRIMARY KEY,
    purchases numeric(14,4),
    wastage numeric(14,4),
    stock_on_hand numeric(14,4),
    resolution_type text NOT NULL,
    authoritative_source text,
    has_conflict boolean NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE inventory_override (
    id uuid PRIMARY KEY,
    trading_date date NOT NULL,
    overridden_purchases numeric(14,4),
    reason text,
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX ux_inventory_override_current
    ON inventory_override (trading_date) WHERE superseded_at IS NULL;
