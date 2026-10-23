-- Preserve legacy facts; only new source-qualified inputs use the new identity.
ALTER TABLE canonical_product_sales ADD COLUMN source_product_ref varchar(512);
DROP INDEX ux_product_sales_current;
CREATE UNIQUE INDEX ux_product_sales_current ON canonical_product_sales(source_system,source_record_ref) WHERE superseded_at IS NULL;
CREATE TABLE product_sales_contributor (
 canonical_version_id uuid NOT NULL REFERENCES canonical_product_sales(id),
 raw_record_id uuid NOT NULL REFERENCES raw_record(id),
 PRIMARY KEY(canonical_version_id,raw_record_id)
);
ALTER TABLE source_product_sale_fact ADD COLUMN quantity_sold numeric(14,4), ADD COLUMN amount numeric(14,4),
 ADD COLUMN product_name varchar(512), ADD COLUMN site_number integer;
