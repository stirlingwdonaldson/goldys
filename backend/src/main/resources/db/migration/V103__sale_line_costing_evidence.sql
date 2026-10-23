ALTER TABLE canonical_sale_item ADD COLUMN adjustment jsonb;

-- Current-version views avoid a second copy of millions of lines and stale recipe joins.
CREATE VIEW resolved_sale_line AS
SELECT id AS canonical_version_id, logical_entity_id, raw_record_id, source_record_ref AS receipt_line_id,
       trading_date, sale_number, item_name, quantity_sold, amount, total_tax, adjustment
FROM canonical_sale_item WHERE superseded_at IS NULL;

CREATE VIEW resolved_sale_recipe_link AS
SELECT sale_item_stock_code, sale_item_stock_description, recipe_id, is_recipe_active
FROM canonical_sale_recipe_link WHERE superseded_at IS NULL;
