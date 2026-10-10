-- quantity_sold is fractional in the real Lightspeed data (weighed items, e.g.
-- 0.5), not an integer count. Widen to a fixed-precision decimal.
ALTER TABLE canonical_sale_item ALTER COLUMN quantity_sold TYPE numeric(20, 6);
