SELECT count(*) revisions FROM product_reporting_mapping;
SELECT authoritative_source,count(*),sum(amount),count(*) FILTER(WHERE amount IS NULL) unresolved FROM resolved_product_sales WHERE trading_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1;
SELECT trading_date,sum(amount) gross,sum(tax_amount) tax,sum(amount-tax_amount) net,count(*),count(tax_amount),string_agg(DISTINCT site_number::text,',') sites FROM source_product_sale_fact WHERE superseded_at IS NULL AND trading_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1 ORDER BY 1;
SELECT trading_date,sum(amount) gross,sum(total_tax) tax,sum(amount-total_tax) net,count(*),count(total_tax),string_agg(DISTINCT site_number::text,',') sites FROM resolved_sale_line WHERE trading_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1 ORDER BY 1;
SELECT * FROM resolved_daily_sales WHERE trading_date BETWEEN '2026-10-26' AND '2026-11-01' ORDER BY trading_date;
SELECT wastage_record_id,wastage_date,unit_of_measurement_name,unit_size,count(*),sum(quantity*cost) value FROM canonical_wastage WHERE superseded_at IS NULL AND wastage_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1,2,3,4 ORDER BY 1,3;
