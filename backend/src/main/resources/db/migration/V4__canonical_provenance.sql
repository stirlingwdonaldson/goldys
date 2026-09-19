-- The cleared rebuild has no canonical rows, so the additive provenance columns can be NOT NULL
-- without a backfill. Each canonical version gains a stable logical identity, its source fact
-- reference, and the raw record that produced it. The partial unique index makes "one current
-- version per source fact" a database guarantee, not just an application convention.
ALTER TABLE canonical_shift
    ADD COLUMN logical_entity_id uuid NOT NULL,
    ADD COLUMN source_system varchar(255) NOT NULL,
    ADD COLUMN source_record_ref varchar(512) NOT NULL,
    ADD COLUMN raw_record_id uuid NOT NULL REFERENCES raw_record(id);

ALTER TABLE canonical_sale_item
    ADD COLUMN logical_entity_id uuid NOT NULL,
    ADD COLUMN source_system varchar(255) NOT NULL,
    ADD COLUMN source_record_ref varchar(512) NOT NULL,
    ADD COLUMN raw_record_id uuid NOT NULL REFERENCES raw_record(id);

-- The V1 current-row indexes indexed valid_from; they are superseded by the source-fact indexes.
DROP INDEX idx_canonical_shift_current;
DROP INDEX idx_canonical_sale_item_current;

CREATE UNIQUE INDEX uq_canonical_shift_current_source_fact
    ON canonical_shift (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_canonical_shift_logical_current
    ON canonical_shift (logical_entity_id, source_system) WHERE superseded_at IS NULL;

CREATE UNIQUE INDEX uq_canonical_sale_item_current_source_fact
    ON canonical_sale_item (source_system, source_record_ref) WHERE superseded_at IS NULL;
CREATE INDEX idx_canonical_sale_item_logical_current
    ON canonical_sale_item (logical_entity_id, source_system) WHERE superseded_at IS NULL;
