-- Review exact native CTB site/code identities without equating legacy and current sites.
ALTER TABLE product_reporting_mapping DROP CONSTRAINT product_reporting_mapping_scope_check;
ALTER TABLE product_reporting_mapping ADD CONSTRAINT product_reporting_mapping_scope_check
 CHECK (scope IN ('PRODUCT_NAME','CTB_PRODUCT','CTB_RECIPE','CTB_STOCK','WASTE_LINE'));
-- A partial/mixed source policy (including UNKNOWN withdrawal) holds the entire resolved alias.
-- A tax observation belongs to the selected product/day source, never to a similar name
-- from another source. Legacy or mismatched evidence remains explicitly unavailable.
CREATE OR REPLACE VIEW resolved_reporting_sales AS
WITH ctb_evidence AS (
 SELECT f.*, regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ','') AS name_key,
        CASE WHEN site_number IS NOT NULL AND nullif(btrim(stock_code),'') IS NOT NULL
             THEN 'site:' || site_number || ':code:' || length(stock_code) || ':' || stock_code END AS product_ref
 FROM source_product_sale_fact f WHERE superseded_at IS NULL AND source_system='CTB'
), tax_evidence AS (
 SELECT source_system, trading_date,
        name_key AS product_name_key,
        sum(amount) AS gross, sum(quantity_sold) AS quantity,
        CASE WHEN bool_and(amount IS NOT NULL AND tax_amount IS NOT NULL AND quantity_sold IS NOT NULL)
                  AND bool_and(site_number IS NOT NULL)
                  AND count(DISTINCT site_number)=1
             THEN sum(amount-tax_amount) END AS net,
        count(m.id)>0 AS source_policy,
        CASE WHEN bool_and(m.id IS NOT NULL) AND count(DISTINCT m.sales_nature)=1
             THEN min(m.sales_nature) ELSE 'UNKNOWN' END AS source_nature
 FROM ctb_evidence f
 LEFT JOIN LATERAL (
  SELECT r.id,r.sales_nature FROM product_reporting_mapping r
  WHERE r.scope='CTB_PRODUCT' AND r.source_system='CTB' AND r.entity_key=f.product_ref
    AND r.effective_from<=f.trading_date
  ORDER BY r.effective_from DESC,r.id DESC LIMIT 1
 ) m ON true
 GROUP BY 1,2,3
 UNION ALL
 SELECT source_system, trading_date,
        regexp_replace(btrim(regexp_replace(lower(item_name),'[^a-z0-9]+',' ','g')),'^new ','') AS product_name_key,
        sum(amount),sum(quantity_sold),
        CASE WHEN bool_and(amount IS NOT NULL AND total_tax IS NOT NULL)
                  AND bool_and(site_number IS NOT NULL)
                  AND count(DISTINCT site_number)=1
             THEN sum(amount-total_tax) END,false,null::varchar
 FROM resolved_sale_line WHERE source_system='LIGHTSPEED'
 GROUP BY 1,2,3
)
SELECT s.trading_date,s.product_name_key,s.amount AS gross_amount,
       CASE WHEN t.gross=s.amount AND t.quantity=s.quantity_sold THEN t.net END AS net_amount,
       s.authoritative_source,
       CASE WHEN t.source_policy THEN
         CASE WHEN t.gross=s.amount AND t.quantity=s.quantity_sold THEN t.source_nature ELSE 'UNKNOWN' END
       ELSE coalesce(m.sales_nature,'UNKNOWN') END::varchar AS sales_nature
FROM resolved_product_sales s
LEFT JOIN tax_evidence t ON t.source_system=s.authoritative_source
 AND t.trading_date=s.trading_date AND t.product_name_key=s.product_name_key
LEFT JOIN LATERAL (
 SELECT r.sales_nature FROM product_reporting_mapping r
 WHERE r.scope='PRODUCT_NAME' AND r.source_system='RESOLVED'
   AND r.entity_key=s.product_name_key AND r.effective_from<=s.trading_date
 ORDER BY r.effective_from DESC,r.id DESC LIMIT 1
) m ON true;
