-- I2-01: Invoice-number identity collisions — how many invoice numbers map to
-- more than one distinct supplier (the audit's "2 cross-supplier collisions").
-- Counts only; never supplier names or invoice numbers.
WITH x AS (SELECT invoice_number,count(DISTINCT supplier_name) AS suppliers
           FROM canonical_invoice WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS invoice_numbers,
       count(*) FILTER (WHERE suppliers>1) AS multi_supplier_numbers,
       max(suppliers) AS max_suppliers_per_number
FROM x;
-- I2-02: product_name_key <-> stock_code ambiguity on invoice lines.
WITH a AS (SELECT stock_code,count(DISTINCT product_name_key) AS names
           FROM canonical_invoice_line WHERE superseded_at IS NULL AND stock_code IS NOT NULL GROUP BY 1)
SELECT count(*) AS stock_codes,count(*) FILTER (WHERE names>1) AS codes_with_multiple_names FROM a;
WITH b AS (SELECT product_name_key,count(DISTINCT stock_code) AS codes
           FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS product_keys,count(*) FILTER (WHERE codes>1) AS names_with_multiple_codes FROM b;
-- I2-03: Sale <-> recipe link coverage (cross-domain joinability).
SELECT count(*) AS links,
       count(*) FILTER (WHERE recipe_id IS NULL OR recipe_id=0) AS null_recipe,
       count(DISTINCT sale_item_stock_code) AS sale_codes,
       count(DISTINCT recipe_id) AS recipe_ids
FROM canonical_sale_recipe_link WHERE superseded_at IS NULL;
-- I2-04: Stocktake -> stock-count date resolution (detail carries no date).
SELECT (SELECT count(*) FROM canonical_stocktake WHERE superseded_at IS NULL) AS stocktakes,
       (SELECT count(*) FROM canonical_stock_count WHERE superseded_at IS NULL) AS stock_counts,
       (SELECT count(*) FROM canonical_stock_count c LEFT JOIN canonical_stocktake s
         ON s.stocktake_id=c.stocktake_id AND s.superseded_at IS NULL
         WHERE c.superseded_at IS NULL AND s.id IS NULL) AS orphan_counts;
-- I2-05: Guest <-> reservation linkage (guest_id nullable on reservation).
SELECT (SELECT count(*) FROM canonical_guest WHERE superseded_at IS NULL) AS guests,
       (SELECT count(*) FROM canonical_reservation WHERE superseded_at IS NULL) AS reservations,
       (SELECT count(*) FROM canonical_reservation WHERE superseded_at IS NULL AND guest_id IS NOT NULL) AS linked_reservations;
-- I2-06: Recipe ingredient parent/child identity (sub-recipe vs main).
SELECT count(*) AS ingredients,
       count(*) FILTER (WHERE is_sub_recipe) AS sub_recipe_lines,
       count(*) FILTER (WHERE is_broken_link) AS broken_links,
       count(DISTINCT parent_recipe_id) AS parent_recipes
FROM canonical_recipe_ingredient WHERE superseded_at IS NULL;
