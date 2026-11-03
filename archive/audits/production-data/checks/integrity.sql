-- I01: Safe stack-frame classification, no exception message/payload export.
SELECT failure_type,
       CASE WHEN stack_trace LIKE '%CtbClient.getAllRecipes%' THEN 'getAllRecipes'
            WHEN stack_trace LIKE '%CtbClient.searchRevenues%' THEN 'searchRevenues'
            WHEN stack_trace LIKE '%CtbClient.login%' THEN 'login' ELSE 'other' END AS boundary,
       CASE WHEN detail='Non-JSON CTB response' THEN 'Non-JSON response' ELSE 'other (not exported)' END AS safe_detail,
       count(*) FROM ingestion_failure GROUP BY 1,2,3 ORDER BY 1,2;
-- I02: Current canonical completeness, monetary consistency, impossible dates.
SELECT source_system,count(*) AS current_rows,count(DISTINCT trading_date) AS dates,
 min(trading_date),max(trading_date),
 count(*) FILTER (WHERE abs(total_sales-net_total-gst_total)>0.01) AS gross_net_gst_mismatch,
 count(*) FILTER (WHERE total_sales<0) AS negative_gross,
 count(*) FILTER (WHERE total_sales=0) AS zero_gross
FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1;
SELECT count(*) AS headers,count(*) FILTER (WHERE invoice_date<'2020-01-01') AS old_dates,
 count(*) FILTER (WHERE invoice_date>current_date) AS future_dates,
 count(*) FILTER (WHERE total_amount IS NULL) AS total_missing,
 count(*) FILTER (WHERE amount_ex_tax IS NULL) AS ex_tax_missing,
 count(*) FILTER (WHERE gst_amount IS NULL) AS gst_missing,
 count(*) FILTER (WHERE freight_amount IS NULL) AS freight_missing,
 count(*) FILTER (WHERE account_number IS NULL) AS account_missing,
 count(*) FILTER (WHERE tax_code IS NULL) AS tax_code_missing,
 count(*) FILTER (WHERE freight_gst_amount IS NULL) AS freight_gst_missing,
 count(*) FILTER (WHERE pdf_filename IS NULL) AS pdf_missing,
 count(*) FILTER (WHERE abs(total_amount-amount_ex_tax-gst_amount)>0.01) AS header_tax_mismatch
FROM canonical_invoice WHERE superseded_at IS NULL;
SELECT count(*) AS lines,count(*) FILTER (WHERE quantity=1) AS quantity_one,
 count(*) FILTER (WHERE quantity=0) AS quantity_zero,
 count(*) FILTER (WHERE unit_cost=0) AS unit_cost_zero,
 count(*) FILTER (WHERE abs(quantity*unit_cost-line_total)>0.01) AS quantity_cost_mismatch,
 count(*) FILTER (WHERE stock_code IS NULL OR stock_code='') AS stock_missing,
 count(*) FILTER (WHERE category IS NULL) AS category_missing,
 count(*) FILTER (WHERE uom IS NULL) AS uom_missing,
 count(*) FILTER (WHERE unit_quantity IS NULL) AS unit_quantity_missing,
 count(*) FILTER (WHERE pack_size IS NULL) AS pack_size_missing,
 count(*) FILTER (WHERE wet_amount IS NULL) AS wet_missing,
 count(*) FILTER (WHERE line_total<0) AS negative_lines,
 count(*) FILTER (WHERE invoice_date<'2020-01-01') AS old_dates
FROM canonical_invoice_line WHERE superseded_at IS NULL;
-- I03: Invoice number join safety; report counts, never supplier names or invoice numbers.
WITH h AS (SELECT invoice_number,count(*) AS n FROM canonical_invoice WHERE superseded_at IS NULL GROUP BY 1),
 l AS (SELECT invoice_number,count(*) AS n,sum(line_total) AS total FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1)
SELECT (SELECT count(*) FROM h WHERE n>1) AS ambiguous_header_keys,
 (SELECT count(*) FROM h LEFT JOIN l USING(invoice_number) WHERE l.invoice_number IS NULL) AS headers_without_lines,
 (SELECT count(*) FROM l LEFT JOIN h USING(invoice_number) WHERE h.invoice_number IS NULL) AS orphan_invoice_keys,
 (SELECT count(*) FROM canonical_invoice i JOIN l USING(invoice_number) WHERE i.superseded_at IS NULL AND abs(i.amount_ex_tax-l.total)>0.01) AS header_line_ex_tax_mismatches,
 (SELECT count(*) FROM canonical_invoice i JOIN l USING(invoice_number) WHERE i.superseded_at IS NULL AND abs(i.amount_ex_tax-l.total)>0.01 AND abs(i.amount_ex_tax-l.total-coalesce(i.freight_amount,0))>0.01) AS mismatch_after_freight;
WITH d AS (SELECT invoice_number,stock_code,product_name_key,quantity,unit_cost,line_total,count(*) AS n
 FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1,2,3,4,5,6 HAVING count(*)>1)
SELECT count(*) AS repeated_line_groups,coalesce(sum(n-1),0) AS repeated_line_excess FROM d;
WITH d AS (SELECT stock_code,count(DISTINCT product_name_key) AS names FROM canonical_invoice_line
 WHERE superseded_at IS NULL AND stock_code IS NOT NULL GROUP BY 1)
SELECT count(*) AS stock_codes,count(*) FILTER(WHERE names>1) AS codes_with_multiple_names FROM d;
WITH d AS (SELECT product_name_key,count(DISTINCT stock_code) AS codes FROM canonical_invoice_line
 WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS product_keys,count(*) FILTER(WHERE codes>1) AS names_with_multiple_codes FROM d;
-- I04: Cross-domain natural-name overlap (not proof of identity).
WITH p AS (SELECT DISTINCT product_name_key FROM canonical_product_sales WHERE superseded_at IS NULL),
 i AS (SELECT DISTINCT product_name_key FROM canonical_invoice_line WHERE superseded_at IS NULL)
SELECT (SELECT count(*) FROM p) AS sale_products,(SELECT count(*) FROM i) AS purchase_products,
 (SELECT count(*) FROM p JOIN i USING(product_name_key)) AS overlapping_names;
-- I05: Projection identity/key/value equality against current canonical facts.
WITH c AS (SELECT invoice_date AS trading_date,sum(line_total) AS purchases FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS dates_checked,count(*) FILTER(WHERE c.trading_date IS NULL OR r.trading_date IS NULL) AS missing_keys,
 count(*) FILTER(WHERE c.purchases IS DISTINCT FROM r.purchases) AS amount_mismatches,
 count(*) FILTER(WHERE r.wastage IS NULL) AS null_wastage,
 count(*) FILTER(WHERE r.stock_on_hand IS NULL) AS null_stock
FROM c FULL JOIN resolved_inventory_day r USING(trading_date);
WITH c AS (SELECT trading_date,product_name_key,quantity_sold,amount FROM canonical_product_sales WHERE superseded_at IS NULL AND source_system='CTB')
SELECT count(*) AS product_days_checked,count(*) FILTER(WHERE c.trading_date IS NULL OR r.trading_date IS NULL) AS missing_keys,
 count(*) FILTER(WHERE c.amount IS DISTINCT FROM r.amount OR c.quantity_sold IS DISTINCT FROM r.quantity_sold) AS value_mismatches
FROM c FULL JOIN resolved_product_sales r USING(trading_date,product_name_key);
SELECT resolution_type,authoritative_source,has_conflict,count(*) AS dates,
 count(*) FILTER(WHERE total_sales IS NULL) AS missing_gross,
 count(*) FILTER(WHERE net_sales IS NULL) AS missing_net,
 count(*) FILTER(WHERE abs(total_sales-net_sales-gst)>0.01) AS gross_net_gst_mismatches
FROM resolved_daily_sales GROUP BY 1,2,3 ORDER BY 1,2;
SELECT count(*) AS resolved_rows,count(*) FILTER (WHERE c.id IS NULL) AS no_authoritative_fact,
 count(*) FILTER(WHERE r.total_sales IS DISTINCT FROM c.total_sales OR r.net_sales IS DISTINCT FROM c.net_total OR r.gst IS DISTINCT FROM c.gst_total) AS authoritative_value_mismatches
FROM resolved_daily_sales r LEFT JOIN canonical_daily_sales c
 ON c.trading_date=r.trading_date AND c.source_system=r.authoritative_source AND c.superseded_at IS NULL;
-- I06: Overrides/rules, no actors or free-text reasons exported.
SELECT entity_type,field_key,strategy,source_priority,recorded_at,superseded_at FROM resolution_rule ORDER BY recorded_at;
SELECT trading_date,authoritative_source,recorded_at,superseded_at FROM daily_sales_override ORDER BY trading_date;
-- I07: Grain-aware daily sales / product report consistency. Definitions may differ.
WITH p AS (SELECT trading_date,sum(amount) AS product_amount FROM canonical_product_sales WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) AS overlapping_days,count(*) FILTER(WHERE abs(p.product_amount-d.total_sales)>0.01) AS gross_mismatch_days,
 count(*) FILTER(WHERE abs(p.product_amount-d.net_total)>0.01) AS net_mismatch_days,
 min(p.trading_date),max(p.trading_date)
FROM p JOIN canonical_daily_sales d USING(trading_date) WHERE d.superseded_at IS NULL AND d.source_system='CTB';
-- I08: Representative-date purchasing and sales verification; counts/deltas only.
WITH p AS (SELECT invoice_date,count(*) AS line_count,sum(line_total) AS purchases FROM canonical_invoice_line
 WHERE superseded_at IS NULL AND invoice_date IN ('2026-10-02','2026-10-05','2026-10-08') GROUP BY 1)
SELECT p.invoice_date,p.line_count,p.purchases-r.purchases AS projection_delta FROM p JOIN resolved_inventory_day r ON r.trading_date=p.invoice_date;
SELECT trading_date,count(*) AS sources,min(total_sales),max(total_sales),max(total_sales)-min(total_sales) AS source_gross_delta
FROM canonical_daily_sales WHERE superseded_at IS NULL AND trading_date IN ('2026-10-02','2026-10-03','2026-10-05','2026-10-08') GROUP BY 1 ORDER BY 1;
