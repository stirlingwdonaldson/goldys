-- R01: Bounded latest CTB web-run JSON. No payload values, source IDs or names returned.
WITH latest AS (SELECT id FROM ingestion_run WHERE connector_name='ctb-revenue' ORDER BY started_at DESC LIMIT 1),
 records AS (SELECT r.id,r.fetcher_identity,convert_from(payload_bytes,'UTF8')::jsonb AS j
 FROM raw_record r WHERE ingestion_run_id IN (SELECT id FROM latest) AND content_type='application/json'),
 invoices AS (SELECT x FROM records CROSS JOIN LATERAL jsonb_array_elements(j->'data') x WHERE fetcher_identity='ctb-invoices-ajax'),
 grouped AS (SELECT x->>'invoiceNo' AS invoice_number,count(DISTINCT x->>'invoiceId') AS ids,
 count(DISTINCT x->>'supplierId') AS suppliers FROM invoices GROUP BY 1)
SELECT (SELECT count(*) FROM invoices) AS source_invoice_rows,
 (SELECT count(*) FROM grouped) AS source_invoice_number_keys,
 (SELECT count(*) FROM grouped WHERE ids>1) AS colliding_invoice_number_keys,
 (SELECT count(*) FROM grouped WHERE suppliers>1) AS cross_supplier_collisions,
 (SELECT count(*) FROM grouped g LEFT JOIN canonical_invoice c ON c.invoice_number=g.invoice_number AND c.superseded_at IS NULL WHERE c.id IS NULL) AS source_keys_missing_canonical,
 (SELECT count(*) FROM canonical_invoice c LEFT JOIN grouped g USING(invoice_number) WHERE c.superseded_at IS NULL AND g.invoice_number IS NULL) AS canonical_keys_absent_source;
-- R02: Revenue pages independently summed; quantities not compared one raw page to one row.
WITH latest AS (SELECT id FROM ingestion_run WHERE connector_name='ctb-revenue' ORDER BY started_at DESC LIMIT 1),
 pages AS (SELECT id,convert_from(payload_bytes,'UTF8')::jsonb AS j FROM raw_record WHERE ingestion_run_id IN (SELECT id FROM latest) AND fetcher_identity='ctb-revenue'),
 facts AS (SELECT p.id AS raw_id,x,
 (to_timestamp(((x->>'revenueDate')::numeric-621355968000000000)/10000000) AT TIME ZONE 'Australia/Melbourne')::date AS d
 FROM pages p CROSS JOIN LATERAL jsonb_array_elements(j->'data') x WHERE x ? 'revenueId'),
 totals AS (SELECT d,sum((x->>'totalSales')::numeric) AS gross,sum((x->>'GSTTotal')::numeric) AS gst,
 sum((x->>'kitchenRevenueTotal')::numeric) AS net,count(DISTINCT raw_id) AS pages FROM facts GROUP BY 1)
SELECT count(*) AS source_days,count(*) FILTER(WHERE t.pages>1) AS days_spanning_raw_pages,
 count(*) FILTER(WHERE c.id IS NULL) AS missing_canonical_days,
 count(*) FILTER(WHERE abs(t.gross-c.total_sales)>0.0001 OR abs(t.gst-c.gst_total)>0.0001 OR abs(t.net-c.net_total)>0.0001) AS mismatch_over_four_decimal_tolerance,
 count(*) FILTER(WHERE t.gross<>c.total_sales OR t.gst<>c.gst_total OR t.net<>c.net_total) AS exact_decimal_mismatches,
 max(abs(t.gross-c.total_sales)) AS maximum_gross_rounding_delta
FROM totals t LEFT JOIN canonical_daily_sales c ON c.trading_date=t.d AND c.source_system='CTB' AND c.superseded_at IS NULL;
-- R03: Meaningful raw product-cost availability; field presence alone is insufficient.
WITH latest AS (SELECT id FROM ingestion_run WHERE connector_name='ctb-revenue' ORDER BY started_at DESC LIMIT 1),
 facts AS (SELECT x FROM raw_record r CROSS JOIN LATERAL jsonb_array_elements(convert_from(payload_bytes,'UTF8')::jsonb->'data') x
 WHERE r.ingestion_run_id IN (SELECT id FROM latest) AND r.fetcher_identity='ctb-revenue' AND x ? 'stockDescription')
SELECT count(*) AS sale_product_rows,count(DISTINCT x->>'stockCode') AS distinct_stock_codes,
 count(*) FILTER(WHERE (x->>'totalFoodCost')::numeric<>0) AS nonzero_food_cost_rows,
 count(*) FILTER(WHERE (x->>'costPerUnit')::numeric<>0) AS nonzero_unit_cost_rows,
 count(*) FILTER(WHERE coalesce(x->>'recipeName','')<>'') AS recipe_named_rows,
 count(*) FILTER(WHERE (x->>'discountedAmount')::numeric<>0) AS discounted_rows,
 count(*) FILTER(WHERE (x->>'taxAmount')::numeric<>0) AS taxed_rows
FROM facts;
-- R04: Source codes for cross-domain joins exist but are not retained on product_sales.
WITH latest AS (SELECT id FROM ingestion_run WHERE connector_name='ctb-revenue' ORDER BY started_at DESC LIMIT 1),
 pages AS (SELECT fetcher_identity,convert_from(payload_bytes,'UTF8')::jsonb AS j FROM raw_record WHERE ingestion_run_id IN (SELECT id FROM latest)),
 sales AS (SELECT DISTINCT x->>'stockCode' AS code FROM pages CROSS JOIN LATERAL jsonb_array_elements(j->'data') x WHERE fetcher_identity='ctb-revenue' AND x ? 'stockDescription'),
 links AS (SELECT DISTINCT x->>'saleItemStockCode' AS code FROM pages CROSS JOIN LATERAL jsonb_array_elements(j->'data') x WHERE fetcher_identity='ctb-sale-recipe-links')
SELECT (SELECT count(*) FROM sales) AS sale_codes,(SELECT count(*) FROM links) AS link_codes,
 (SELECT count(*) FROM sales JOIN links USING(code)) AS exact_code_overlap;
-- R05: Existing structured price-history candidates; no individual stock/supplier output.
WITH p AS (SELECT h.supplier_name,l.stock_code,count(DISTINCT l.invoice_date) AS dates,
 count(DISTINCT l.unit_cost) AS costs FROM canonical_invoice_line l JOIN canonical_invoice h USING(invoice_number)
 WHERE l.superseded_at IS NULL AND h.superseded_at IS NULL AND l.stock_code IS NOT NULL GROUP BY 1,2)
SELECT count(*) AS supplier_stock_pairs,count(*) FILTER(WHERE dates>1) AS repeated_purchase_pairs,
 count(*) FILTER(WHERE dates>1 AND costs>1) AS changing_price_candidates FROM p;
