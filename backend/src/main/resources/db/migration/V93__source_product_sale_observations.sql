-- Processing receipts make retained source replay idempotent without rewriting raw evidence.
CREATE TABLE source_product_sale_observation (
    source_system varchar(255) NOT NULL,
    source_record_ref varchar(512) NOT NULL,
    raw_record_id uuid NOT NULL REFERENCES raw_record(id),
    PRIMARY KEY (source_system, source_record_ref, raw_record_id)
);
INSERT INTO source_product_sale_observation(source_system, source_record_ref, raw_record_id)
SELECT DISTINCT source_system, source_record_ref, raw_record_id FROM source_product_sale_fact
ON CONFLICT DO NOTHING;
