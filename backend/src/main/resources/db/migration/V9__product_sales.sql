CREATE TABLE canonical_product_sales (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    trading_date date NOT NULL,
    product_name_key varchar(512) NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    quantity_sold numeric(14,4) NOT NULL,
    amount numeric(14,4) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_product_sales_current
    ON canonical_product_sales (product_name_key, trading_date, source_system)
    WHERE superseded_at IS NULL;
CREATE INDEX ix_product_sales_logical
    ON canonical_product_sales (logical_entity_id) WHERE superseded_at IS NULL;
