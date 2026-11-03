-- A tax observation belongs to the selected product/day source, never to a similar name
-- from another source. Legacy or mismatched evidence remains explicitly unavailable.
CREATE VIEW resolved_reporting_sales AS
WITH tax_evidence AS (
 SELECT source_system, trading_date,
        regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ','') AS product_name_key,
        sum(amount) AS gross, sum(quantity_sold) AS quantity,
        CASE WHEN bool_and(amount IS NOT NULL AND tax_amount IS NOT NULL AND quantity_sold IS NOT NULL)
                  AND bool_and(site_number IS NOT NULL)
                  AND count(DISTINCT site_number)=1
             THEN sum(amount-tax_amount) END AS net
 FROM source_product_sale_fact WHERE superseded_at IS NULL AND source_system='CTB'
 GROUP BY 1,2,3
 UNION ALL
 SELECT source_system, trading_date,
        regexp_replace(btrim(regexp_replace(lower(item_name),'[^a-z0-9]+',' ','g')),'^new ','') AS product_name_key,
        sum(amount),sum(quantity_sold),
        CASE WHEN bool_and(amount IS NOT NULL AND total_tax IS NOT NULL)
                  AND bool_and(site_number IS NOT NULL)
                  AND count(DISTINCT site_number)=1
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
) m ON true;
