-- Dated audit. Run only against an authorized database connection.
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '60s';
SHOW default_transaction_read_only;

-- days
WITH s AS(SELECT trading_date,sum(amount) sales FROM resolved_sale_item_day GROUP BY 1),p AS(SELECT trading_date,sum(amount) payments FROM resolved_payment_day GROUP BY 1),d AS(SELECT trading_date,sum(total_inc_tax) deleted,count(*) deleted_count FROM canonical_deleted_sale WHERE superseded_at IS NULL GROUP BY 1) SELECT coalesce(s.trading_date,p.trading_date) report_date,sales,payments,deleted,deleted_count FROM s FULL JOIN p USING(trading_date) LEFT JOIN d ON d.trading_date=coalesce(s.trading_date,p.trading_date) ORDER BY 1;

-- measure
WITH i AS(SELECT trading_date,sale_number,sum(amount) items FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-01-01' AND '2026-10-25' GROUP BY 1,2),p AS(SELECT trading_date,sale_number,sum(amount) payments,sum(payment_count) n FROM canonical_payment WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-01-01' AND '2026-10-25' GROUP BY 1,2),a AS(SELECT coalesce(i.trading_date,p.trading_date) report_date,items,payments,n,coalesce(payments,0)-coalesce(items,0) delta FROM i FULL JOIN p USING(trading_date,sale_number) WHERE abs(coalesce(payments,0)-coalesce(items,0))>0.01) SELECT date_trunc('month',report_date)::date AS report_month,count(*) anomalies,count(*) FILTER(WHERE n>1 AND items=payments*n) exact_payment_count_multiples,sum(delta) difference,sum(delta) FILTER(WHERE n>1 AND items=payments*n) multiplication_difference,sum(delta) FILTER(WHERE items IS NULL OR payments IS NULL) unmatched_difference FROM a GROUP BY 1 ORDER BY 1;
SELECT adjustment->>'type' type,count(*),sum(amount) final_amount,sum((adjustment->>'grossIncTax')::numeric) gross_before_adjustment,sum((adjustment->>'amountIncTax')::numeric) adjustment,sum(amount-(adjustment->>'grossIncTax')::numeric-(adjustment->>'amountIncTax')::numeric) identity_residual FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-11' GROUP BY 1 ORDER BY 2 DESC;
SELECT sale_type,count(*),sum(amount) FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-11' GROUP BY 1;
SELECT payment_type_name,count(*),sum(amount),sum(tip),sum(surcharge) FROM canonical_payment WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-11' GROUP BY 1 ORDER BY 3 DESC;
SELECT category_name,item_name,count(*),sum(amount) FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-11' AND (item_name ILIKE '%gift%' OR category_name ILIKE '%gift%') GROUP BY 1,2;

-- screenshot
SELECT payment_type_name,sum(amount),sum(tip),sum(surcharge) FROM canonical_payment WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-14' AND '2026-11-11' GROUP BY 1 ORDER BY 2 DESC;
SELECT r.id,r.fetcher_identity,r.fetched_at,r.payload_byte_length FROM raw_record r WHERE id IN (SELECT raw_record_id FROM canonical_sale_item WHERE superseded_at IS NULL AND sale_number IN ('SP-96 1021082405','SP-9 1022074935','SP-213 1024064823','SP-182 1024055847'));
SELECT sale_number,source_record_ref,trading_date,quantity_sold,amount FROM canonical_sale_item WHERE superseded_at IS NULL AND sale_number IN ('SP-96 1021082405','SP-9 1022074935','SP-213 1024064823','SP-182 1024055847') ORDER BY 1,2;
SELECT adjustment->>'type' type,count(*),sum(amount) final_amount,sum((adjustment->>'amountIncTax')::numeric) adjustment FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-14' AND '2026-11-11' GROUP BY 1;
SELECT sale_type,count(*),sum(amount) FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-14' AND '2026-11-11' GROUP BY 1;
SELECT count(*),sum(total_inc_tax) FROM canonical_deleted_sale WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-11';
SELECT count(*) current_rows,count(DISTINCT source_record_ref) distinct_ids,count(*) FILTER(WHERE amount IS NULL) missing_amounts FROM canonical_sale_item WHERE superseded_at IS NULL;
SELECT count(*) current_rows,count(DISTINCT source_record_ref) distinct_ids,count(*) FILTER(WHERE amount IS NULL) missing_amounts FROM canonical_payment WHERE superseded_at IS NULL;

-- residuals
WITH i AS(SELECT trading_date,sale_number,sum(amount) items FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-01-01' AND '2026-10-25' GROUP BY 1,2),p AS(SELECT trading_date,sale_number,sum(amount) payments,sum(payment_count) n FROM canonical_payment WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-01-01' AND '2026-10-25' GROUP BY 1,2) SELECT coalesce(i.trading_date,p.trading_date) report_date,coalesce(i.sale_number,p.sale_number) sale_number,items,payments,n,coalesce(payments,0)-coalesce(items,0) difference FROM i FULL JOIN p USING(trading_date,sale_number) WHERE abs(coalesce(payments,0)-coalesce(items,0))>0.01 AND NOT coalesce(n>1 AND items=payments*n,false) ORDER BY report_date;
SELECT payment_type_name,count(*),sum(amount) FROM canonical_payment WHERE superseded_at IS NULL AND amount<0 AND trading_date BETWEEN '2026-10-14' AND '2026-11-11' GROUP BY 1 ORDER BY 3;
SELECT category_name,count(*),sum(amount) FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2025-01-01' AND '2025-06-30' GROUP BY 1 ORDER BY 3 DESC;
SELECT sale_number,trading_date,payment_type_name,amount,payment_count FROM canonical_payment WHERE superseded_at IS NULL AND sale_number IN ('SP-96 1021082405','SP-9 1022074935','SP-213 1024064823','SP-182 1024055847') ORDER BY 2,1;
SELECT min(trading_date),max(trading_date),count(*),sum(amount) FROM resolved_sale_item_day WHERE trading_date BETWEEN '2026-10-14' AND '2026-11-11';

-- special
SELECT sale_number,trading_date,payment_type_name,amount,payment_count FROM canonical_payment WHERE superseded_at IS NULL AND sale_number IN ('SP-39 0601085711','SP-49 0713093951','SP-48 0725054853','SP-29 0920022844','SP-133 0912075300') ORDER BY sale_number,trading_date,payment_type_name;
SELECT sale_number,trading_date,sale_type,count(*),sum(amount),sum(quantity_sold) FROM canonical_sale_item WHERE superseded_at IS NULL AND sale_number IN ('SP-39 0601085711','SP-49 0713093951','SP-48 0725054853','SP-29 0920022844') GROUP BY 1,2,3 ORDER BY 1,2;
SELECT count(*) FILTER(WHERE sale_number IS NULL) missing_receipts,count(*) FILTER(WHERE site_number IS NULL) missing_site FROM canonical_sale_item WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-14' AND '2026-11-11';

ROLLBACK;
