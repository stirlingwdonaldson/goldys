-- T01: Bitemporal version chains — is valid-time distinct from system-time?
WITH v AS (
  SELECT 'daily_sales' AS domain,valid_from,valid_to,recorded_at,superseded_at FROM canonical_daily_sales
  UNION ALL SELECT 'product_sales',valid_from,valid_to,recorded_at,superseded_at FROM canonical_product_sales
  UNION ALL SELECT 'invoice',valid_from,valid_to,recorded_at,superseded_at FROM canonical_invoice
  UNION ALL SELECT 'invoice_line',valid_from,valid_to,recorded_at,superseded_at FROM canonical_invoice_line
  UNION ALL SELECT 'reservation',valid_from,valid_to,recorded_at,superseded_at FROM canonical_reservation
  UNION ALL SELECT 'payment',valid_from,valid_to,recorded_at,superseded_at FROM canonical_payment
  UNION ALL SELECT 'sale_item',valid_from,valid_to,recorded_at,superseded_at FROM canonical_sale_item
  UNION ALL SELECT 'recipe_ingredient',valid_from,valid_to,recorded_at,superseded_at FROM canonical_recipe_ingredient
)
SELECT domain,count(*) AS versions,
       count(*) FILTER (WHERE valid_from=recorded_at) AS valid_equals_recorded,
       count(*) FILTER (WHERE valid_to IS NULL) AS open_valid_to,
       count(*) FILTER (WHERE superseded_at<recorded_at) AS invalid_system_intervals
FROM v GROUP BY 1 ORDER BY 1;
-- T02: Date coverage per daily-sales source (calendar span vs present dates).
SELECT source_system,count(*) AS rows,count(DISTINCT trading_date) AS present_dates,
       max(trading_date)-min(trading_date)+1 AS calendar_span,
       (max(trading_date)-min(trading_date)+1)-count(DISTINCT trading_date) AS absent_calendar_dates
FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1 ORDER BY 1;
-- T03: Resolved daily-sales projection parity (canonical -> resolved).
WITH c AS (SELECT trading_date,count(*) AS sources FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS resolved_dates,
       count(*) FILTER (WHERE c.sources>1) AS multisource_dates,
       count(*) FILTER (WHERE r.has_conflict) AS unresolved_conflicts,
       count(*) FILTER (WHERE r.resolution_type='rule') AS rule_chosen,
       count(*) FILTER (WHERE r.resolution_type='override') AS override_chosen
FROM resolved_daily_sales r LEFT JOIN c USING(trading_date);
-- T04: Resolved inventory projection parity (invoice lines -> purchases).
WITH c AS (SELECT invoice_date AS d,sum(line_total) AS purchases
           FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS dates_checked,
       count(*) FILTER (WHERE c.purchases IS DISTINCT FROM r.purchases) AS amount_mismatches,
       count(*) FILTER (WHERE r.wastage IS NULL) AS null_wastage,
       count(*) FILTER (WHERE r.stock_on_hand IS NULL) AS null_stock
FROM c FULL JOIN resolved_inventory_day r ON r.trading_date=c.d;
-- T05: Resolved product-sales parity (CTB canonical -> resolved).
WITH c AS (SELECT trading_date,product_name_key,quantity_sold,amount
           FROM canonical_product_sales WHERE superseded_at IS NULL AND source_system='CTB')
SELECT count(*) AS product_days_checked,
       count(*) FILTER (WHERE c.amount IS DISTINCT FROM r.amount OR c.quantity_sold IS DISTINCT FROM r.quantity_sold) AS value_mismatches
FROM c FULL JOIN resolved_product_sales r USING(trading_date,product_name_key);
-- T06: Payments / sale-items resolved coverage.
SELECT count(*) AS payment_days FROM resolved_payment_day;
SELECT count(*) AS sale_item_days FROM resolved_sale_item_day;
SELECT count(*) AS reservation_days FROM resolved_reservation_day;
SELECT count(*) AS recipe_rows FROM resolved_recipe;
SELECT count(*) AS recipe_ingredient_rows FROM resolved_recipe_ingredient;
