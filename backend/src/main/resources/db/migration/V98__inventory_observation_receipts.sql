-- Derived count dates retain the immutable header version that supplied the date.
-- Legacy header provenance is unknown; no historical header association is guessed.
ALTER TABLE canonical_stock_count ADD COLUMN header_version_id uuid REFERENCES canonical_stocktake(id);

-- Append processing receipts in the same transaction as canonical version changes. Equal-value
-- observations belong here too, otherwise replay can manufacture revisions after a correction.
CREATE TABLE inventory_observation_receipt (
    observation_kind varchar(16) NOT NULL CHECK (observation_kind IN ('HEADER','COUNT','WASTE')),
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    header_version_id uuid REFERENCES canonical_stocktake(id),
    recorded_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_observation_receipt UNIQUE NULLS NOT DISTINCT
      (observation_kind, source_system, source_record_ref, raw_record_id, header_version_id),
    CONSTRAINT ck_inventory_receipt_header_context CHECK (observation_kind='COUNT' OR header_version_id IS NULL)
);
CREATE INDEX idx_inventory_receipt_source ON inventory_observation_receipt
    (observation_kind, source_system, source_record_ref);

-- Seed only provenance already evidenced in canonical history, including superseded versions.
-- This does not reinterpret source corrections or rewrite business facts/raw evidence.
INSERT INTO inventory_observation_receipt(observation_kind,source_system,source_record_ref,raw_record_id)
SELECT 'HEADER',source_system,source_record_ref,raw_record_id FROM canonical_stocktake
UNION SELECT 'COUNT',source_system,source_record_ref,raw_record_id FROM canonical_stock_count
UNION SELECT 'WASTE',source_system,source_record_ref,raw_record_id FROM canonical_wastage
ON CONFLICT DO NOTHING;
