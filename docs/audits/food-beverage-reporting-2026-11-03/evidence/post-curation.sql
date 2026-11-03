WITH reporting AS (WITH tax_evidence AS (
 SELECT source_system, trading_date,
        regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ','') AS product_name_key,
        sum(amount) AS gross, sum(quantity_sold) AS quantity,
        CASE WHEN bool_and(amount IS NOT NULL AND tax_amount IS NOT NULL AND quantity_sold IS NOT NULL)
                  AND count(DISTINCT coalesce(site_number::text,'UNKNOWN'))=1
             THEN sum(amount-tax_amount) END AS net
 FROM source_product_sale_fact WHERE superseded_at IS NULL AND source_system='CTB'
 GROUP BY 1,2,3
 UNION ALL
 SELECT source_system, trading_date,
        regexp_replace(btrim(regexp_replace(lower(item_name),'[^a-z0-9]+',' ','g')),'^new ','') AS product_name_key,
        sum(amount),sum(quantity_sold),
        CASE WHEN bool_and(amount IS NOT NULL AND total_tax IS NOT NULL)
                  AND count(DISTINCT coalesce(site_number::text,'UNKNOWN'))=1
             THEN sum(amount-total_tax) END
 FROM resolved_sale_line WHERE source_system='LIGHTSPEED'
 GROUP BY 1,2,3
)
SELECT s.trading_date,s.product_name_key,s.amount AS gross_amount,
       CASE WHEN t.gross=s.amount AND t.quantity=s.quantity_sold THEN t.net END AS net_amount,
       s.authoritative_source,
       coalesce(m.sales_nature,'UNKNOWN') AS sales_nature
FROM resolved_product_sales s
LEFT JOIN tax_evidence t ON t.source_system=s.authoritative_source
 AND t.trading_date=s.trading_date AND t.product_name_key=s.product_name_key
LEFT JOIN LATERAL (
 SELECT r.sales_nature FROM product_reporting_mapping r
 WHERE r.scope='PRODUCT_NAME' AND r.source_system='RESOLVED'
   AND r.entity_key=s.product_name_key AND r.effective_from<=s.trading_date
 ORDER BY r.effective_from DESC,r.id DESC LIMIT 1
) m ON true)
SELECT trading_date,sales_nature,count(*) rows,count(net_amount) known_tax_rows,sum(gross_amount) gross,sum(net_amount) net,sum(abs(net_amount)) abs_net FROM reporting WHERE trading_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1,2 ORDER BY 1,2;
SELECT sales_nature,count(*) lines,count(quantity*source_unit_cost) valued_lines,sum(round(quantity*source_unit_cost,4)) value,count(*) FILTER(WHERE reason IS NULL OR btrim(reason)='') missing_reasons FROM resolved_classified_wastage WHERE wastage_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1 ORDER BY 1;
WITH headers AS (
 SELECT DISTINCT ON ((h->>'wastageRecordId')::int) (h->>'wastageRecordId')::int record_id,(h->>'total')::numeric header_total
 FROM raw_record r CROSS JOIN LATERAL jsonb_array_elements(convert_from(r.payload_bytes,'UTF8')::jsonb->'data') h
 WHERE r.source_system='CTB' AND r.fetcher_identity='ctb-wastage'
 ORDER BY (h->>'wastageRecordId')::int,r.fetched_at DESC
), recorded AS (
 SELECT wastage_record_id record_id,count(*) lines,sum(quantity*cost) value,string_agg(DISTINCT unit_of_measurement_name,',') units
 FROM canonical_wastage WHERE superseded_at IS NULL AND (wastage_date BETWEEN '2026-10-26' AND '2026-11-01' OR wastage_record_id=149) GROUP BY 1
)
SELECT r.*,h.header_total,r.value-h.header_total difference FROM recorded r LEFT JOIN headers h USING(record_id) ORDER BY record_id;
SELECT name,group_name,description FROM product_category ORDER BY name;
SELECT a.product_name_key,c.name,a.source,a.status FROM product_category_assignment a JOIN product_category c ON c.id=a.product_category_id WHERE a.product_name_key IN ('porterhouse','porterhouse 250g');
SELECT scope,count(*),min(effective_from),max(effective_from),count(DISTINCT actor) actors FROM product_reporting_mapping GROUP BY scope;
