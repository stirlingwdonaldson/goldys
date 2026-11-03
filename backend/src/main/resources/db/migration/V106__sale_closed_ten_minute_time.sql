-- Source-local ten-minute bucket; never imply exact event time. Older observations remain unknown.
ALTER TABLE canonical_sale_item ADD COLUMN sale_closed_ten_minute_time time without time zone;

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
      WHEN s.site_number IS NULL THEN 'SITE_UNOBSERVED' ELSE 'MAPPING_MISSING' END AS receipt_association,
 s.sale_closed_ten_minute_time
 FROM canonical_sale_item s LEFT JOIN resolved_receipt_mapping m ON m.source_system=s.source_system
 AND m.site_number=s.site_number AND m.trading_date=s.trading_date AND m.receipt_line_id=s.source_record_ref
 WHERE s.superseded_at IS NULL;
