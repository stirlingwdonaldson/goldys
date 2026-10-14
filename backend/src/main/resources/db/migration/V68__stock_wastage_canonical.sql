-- Stock & wastage canonicalisation: stocktake header entity + enrich the stock-count and
-- wastage line entities (previously modelled in V22 with no ingestion source). Single-source CTB.
-- The two ALTERs target empty tables (0 rows), so adding NOT NULL columns is safe.

CREATE TABLE canonical_stocktake (
    id uuid PRIMARY KEY,
    logical_entity_id uuid NOT NULL,
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    stocktake_id integer NOT NULL,
    stocktake_date date NOT NULL,
    description varchar(512),
    outlet_name varchar(255),
    total_stock_value numeric(14,4),
    is_partial boolean,
    is_locked boolean,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);
CREATE UNIQUE INDEX uq_stocktake_current_source_fact
    ON canonical_stocktake (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_stocktake_id ON canonical_stocktake (stocktake_id);

ALTER TABLE canonical_stock_count
    ADD COLUMN stocktake_id integer NOT NULL,
    ADD COLUMN line_id integer NOT NULL,
    ADD COLUMN stock_id integer,
    ADD COLUMN stock_code varchar(255),
    ADD COLUMN unit_of_measurement_name varchar(64),
    ADD COLUMN unit_cost numeric(14,4),
    ADD COLUMN line_value numeric(14,4),
    ADD COLUMN supplier_name varchar(512),
    ADD COLUMN area_name varchar(255),
    ADD COLUMN recipe_id integer;
CREATE UNIQUE INDEX uq_stock_count_current_source_fact
    ON canonical_stock_count (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_stock_count_stocktake ON canonical_stock_count (stocktake_id);

ALTER TABLE canonical_wastage
    ADD COLUMN wastage_record_id integer NOT NULL,
    ADD COLUMN line_id integer NOT NULL,
    ADD COLUMN cost numeric(14,4),
    ADD COLUMN supplier_name varchar(512),
    ADD COLUMN unit_of_measurement_name varchar(64),
    ADD COLUMN unit_size varchar(64),
    ADD COLUMN recipe_id integer;
CREATE UNIQUE INDEX uq_wastage_current_source_fact
    ON canonical_wastage (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_wastage_record ON canonical_wastage (wastage_record_id);
