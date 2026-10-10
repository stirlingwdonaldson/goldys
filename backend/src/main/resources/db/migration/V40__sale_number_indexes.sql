-- The sale-number drill-down on the sale-items and payments list endpoints filters
-- both large tables by sale_number alone (within a trading-date range). A partial index
-- on the current rows only keeps them small and makes those lookups index range scans.
CREATE INDEX idx_canonical_sale_item_sale_number
    ON canonical_sale_item (sale_number) WHERE superseded_at IS NULL;
CREATE INDEX idx_canonical_payment_sale_number
    ON canonical_payment (sale_number) WHERE superseded_at IS NULL;
