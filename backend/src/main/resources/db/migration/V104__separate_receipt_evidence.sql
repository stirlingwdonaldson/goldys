-- Observation batches preserve every source claim. Omission is never a deletion or a correction.
-- Indexed disposable projections avoid expanding observation batches for every receipt lookup.
CREATE TABLE canonical_receipt_evidence_batch (
 id uuid PRIMARY KEY, logical_entity_id uuid NOT NULL,
 source_system varchar(255) NOT NULL, source_record_ref varchar(512) NOT NULL,
 raw_record_id uuid NOT NULL REFERENCES raw_record(id), scope_raw_id uuid NOT NULL REFERENCES raw_record(id),
 valid_from timestamptz(6) NOT NULL, valid_to timestamptz(6), recorded_at timestamptz(6) NOT NULL, superseded_at timestamptz(6),
 kind varchar(255) NOT NULL CHECK(kind IN ('MAPPING','TRANSACTIONS')),
 site_number bigint NOT NULL CHECK(site_number>0), site_name varchar(255) NOT NULL,
 window_from date NOT NULL, window_to date NOT NULL CHECK(window_to>=window_from),
 mapping_rows jsonb NOT NULL, transaction_rows jsonb NOT NULL
);
CREATE UNIQUE INDEX ux_receipt_evidence_current ON canonical_receipt_evidence_batch(source_system,source_record_ref) WHERE superseded_at IS NULL;
ALTER TABLE canonical_sale_item ADD COLUMN site_number bigint;

CREATE MATERIALIZED VIEW resolved_receipt_mapping_claim AS
 SELECT DISTINCT b.source_system,b.site_number,r."tradingDate" AS trading_date,
 r."receiptLineId" AS receipt_line_id,r."receiptNumber" AS receipt_number
 FROM canonical_receipt_evidence_batch b
 CROSS JOIN LATERAL jsonb_to_recordset(b.mapping_rows) AS r("receiptLineId" text,"receiptNumber" text,"siteNumber" bigint,"tradingDate" date)
 WHERE b.kind='MAPPING' AND b.superseded_at IS NULL AND r."receiptNumber" IS NOT NULL;
CREATE UNIQUE INDEX ux_resolved_receipt_claim ON resolved_receipt_mapping_claim
 (source_system,site_number,trading_date,receipt_line_id,receipt_number);
CREATE INDEX ix_resolved_receipt_number ON resolved_receipt_mapping_claim(receipt_number);

CREATE MATERIALIZED VIEW resolved_receipt_mapping AS
 SELECT g.source_system,g.site_number,d.trading_date,g.receipt_line_id,
 greatest(g.number_count,cardinality(g.trading_dates)) AS receipt_number_count,
 CASE WHEN g.number_count=1 AND cardinality(g.trading_dates)=1 THEN g.receipt_number END AS receipt_number
 FROM (
  SELECT source_system,site_number,receipt_line_id,count(DISTINCT receipt_number) AS number_count,
   min(receipt_number) AS receipt_number,array_agg(DISTINCT trading_date) AS trading_dates
  FROM resolved_receipt_mapping_claim GROUP BY source_system,site_number,receipt_line_id
 ) g CROSS JOIN LATERAL unnest(g.trading_dates) AS d(trading_date);
CREATE UNIQUE INDEX ux_resolved_receipt_mapping ON resolved_receipt_mapping
 (source_system,site_number,trading_date,receipt_line_id);
CREATE INDEX ix_resolved_mapping_number ON resolved_receipt_mapping(receipt_number);

CREATE MATERIALIZED VIEW resolved_sale_transaction AS
 SELECT DISTINCT b.source_system,b.site_number,
 r."transactionId" AS transaction_id,r."receiptNumber" AS receipt_number,
 r."linkedTransactionId" AS linked_transaction_id,r."sourceLocalTime" AS source_local_time,
 r.net,r.tax,r.tip,r.total
 FROM canonical_receipt_evidence_batch b
 CROSS JOIN LATERAL jsonb_to_recordset(b.transaction_rows) AS r("transactionId" text,"receiptNumber" text,"linkedTransactionId" text,"sourceLocalTime" timestamp,net numeric,tax numeric,tip numeric,total numeric)
 WHERE b.kind='TRANSACTIONS' AND b.superseded_at IS NULL;
CREATE UNIQUE INDEX ux_resolved_transaction_fact ON resolved_sale_transaction
 (source_system,site_number,transaction_id,receipt_number,linked_transaction_id,source_local_time,net,tax,tip,total);
CREATE INDEX ix_resolved_transaction_number ON resolved_sale_transaction(receipt_number,site_number,transaction_id);
CREATE INDEX ix_resolved_transaction_identity ON resolved_sale_transaction(source_system,site_number,transaction_id);


-- Keep the original view columns in order; add scoped mapping and presentation fields.
CREATE OR REPLACE VIEW resolved_sale_line AS
 SELECT s.id AS canonical_version_id,s.logical_entity_id,s.raw_record_id,s.source_record_ref AS receipt_line_id,
 s.trading_date,
 CASE WHEN m.receipt_number_count>1 THEN NULL ELSE coalesce(m.receipt_number,s.sale_number) END::varchar(255) AS sale_number,
 s.item_name,s.quantity_sold,s.amount,s.total_tax,s.adjustment,
 s.source_system,s.site_number,s.product_number,s.sku,s.category_name,s.sold_price_inc_tax,s.cost_inc_tax,
 s.order_type,s.sale_type,s.staff_name,s.register_name,s.table_number,
 CASE WHEN m.receipt_number_count>1 THEN 'MAPPING_CONFLICT'
      WHEN m.receipt_number IS NOT NULL THEN 'RECEIPT_GROUP'
      WHEN s.sale_number IS NOT NULL THEN 'LEGACY_RECEIPT_GROUP'
      WHEN s.site_number IS NULL THEN 'SITE_UNOBSERVED' ELSE 'MAPPING_MISSING' END AS receipt_association
 FROM canonical_sale_item s LEFT JOIN resolved_receipt_mapping m ON m.source_system=s.source_system
 AND m.site_number=s.site_number AND m.trading_date=s.trading_date AND m.receipt_line_id=s.source_record_ref
 WHERE s.superseded_at IS NULL;
