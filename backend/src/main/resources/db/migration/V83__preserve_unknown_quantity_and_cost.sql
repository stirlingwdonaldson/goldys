-- Unknown is not a numeric zero. Existing observations are retained unchanged;
-- correcting historically coerced values requires a separately approved evidence replay.
ALTER TABLE canonical_invoice_line ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE canonical_invoice_line ALTER COLUMN unit_cost DROP NOT NULL;
ALTER TABLE canonical_stock_count ALTER COLUMN quantity_on_hand DROP NOT NULL;

-- Preserve a known inclusive observation when exclusive/tax evidence is incomplete.
ALTER TABLE canonical_daily_sales ALTER COLUMN total_sales DROP NOT NULL;
ALTER TABLE canonical_daily_sales ALTER COLUMN gst_total DROP NOT NULL;
ALTER TABLE canonical_daily_sales ALTER COLUMN net_total DROP NOT NULL;
