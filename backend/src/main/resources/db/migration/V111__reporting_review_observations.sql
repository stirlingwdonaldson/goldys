-- Keep financial review reads on projections; evidence never substitutes another selected source.
CREATE VIEW resolved_reporting_pos_evidence AS
SELECT regexp_replace(btrim(regexp_replace(lower(item_name),'[^a-z0-9]+',' ','g')),'^new ','') name_key,
 string_agg(DISTINCT category_name,' | ') categories
FROM canonical_sale_item WHERE superseded_at IS NULL GROUP BY 1;

CREATE VIEW resolved_reporting_review_sale AS
WITH native AS (
 SELECT f.*,
   regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ','') name_key,
   CASE WHEN site_number IS NOT NULL AND nullif(btrim(stock_code),'') IS NOT NULL
     THEN 'site:' || site_number || ':code:' || length(stock_code) || ':' || stock_code END product_ref
 FROM source_product_sale_fact f WHERE superseded_at IS NULL AND source_system='CTB'
)
SELECT s.trading_date, s.product_name_key, s.authoritative_source,
  CASE WHEN f.product_ref IS NOT NULL THEN 'CTB_PRODUCT' ELSE 'PRODUCT_NAME' END scope,
  CASE WHEN f.product_ref IS NOT NULL THEN 'CTB' ELSE 'RESOLVED' END source_system,
  coalesce(f.product_ref,s.product_name_key) entity_key,
  coalesce(f.product_name,s.product_name_key) observed_name,
  s.sales_nature,
  -- Native amounts qualify only after the complete selected alias passed the existing basis check.
  CASE WHEN s.net_amount IS NOT NULL THEN
    CASE WHEN f.id IS NOT NULL THEN f.amount-f.tax_amount ELSE s.net_amount END END net_amount,
  CASE WHEN f.id IS NOT NULL THEN f.amount ELSE s.gross_amount END gross_amount,
  s.net_amount IS NULL AND s.gross_amount IS NOT NULL missing_tax,
  s.gross_amount IS NULL unresolved,
  m.id mapping_id, m.scope mapping_scope, m.sales_nature policy_nature,
  m.effective_from, m.reason policy_reason,
  CASE WHEN s.net_amount IS NULL THEN 'Missing tax or selected-source gross/quantity unresolved'
    WHEN s.sales_nature <> 'UNKNOWN' THEN NULL
    WHEN m.id IS NULL THEN 'No applicable policy'
    WHEN m.sales_nature='UNKNOWN' THEN 'Explicit UNKNOWN withdrawal'
    ELSE 'Alias held: partial, mixed or UNKNOWN contributing policies' END hold_reason,
  concat('Selected source: ',s.authoritative_source,'; alias: ',s.product_name_key,
    '; native: ',coalesce(f.product_ref,'unavailable'),'; source ref: ',coalesce(f.source_record_ref,'unavailable'),
    '; raw record: ',coalesce(f.raw_record_id::text,'unavailable'),
    '; recipe links (code-only, site equivalence unverified): ',coalesce(e.links,'unavailable'),
    '; POS category corroboration by name only (not identity equivalence): ',coalesce(pos.categories,'unavailable'),
    '; current recipe evidence is not historical cost; absent evidence is not approval') evidence
FROM resolved_reporting_sales s
LEFT JOIN native f ON s.authoritative_source='CTB' AND f.trading_date=s.trading_date
 AND f.name_key=s.product_name_key
LEFT JOIN LATERAL (
 SELECT r.* FROM product_reporting_mapping r
 WHERE r.effective_from<=s.trading_date AND
  ((f.product_ref IS NOT NULL AND r.scope='CTB_PRODUCT' AND r.source_system='CTB' AND r.entity_key=f.product_ref)
   OR (r.scope='PRODUCT_NAME' AND r.source_system='RESOLVED' AND r.entity_key=s.product_name_key
       AND NOT EXISTS (SELECT 1 FROM native n JOIN product_reporting_mapping p
         ON p.scope='CTB_PRODUCT' AND p.source_system='CTB' AND p.entity_key=n.product_ref
         AND p.effective_from<=s.trading_date
         WHERE n.trading_date=s.trading_date AND n.name_key=s.product_name_key AND s.authoritative_source='CTB')))
 ORDER BY CASE r.scope WHEN 'CTB_PRODUCT' THEN 0 ELSE 1 END,r.effective_from DESC,r.id DESC LIMIT 1
) m ON true
LEFT JOIN LATERAL (
 SELECT string_agg(DISTINCT (l.recipe_id::text || ': ' || coalesce(d.definition->'header'->>'name','name unavailable') ||
   '; observed=' || coalesce(d.definition->>'observedAt','unavailable') ||
   '; ingredients=' || coalesce((SELECT string_agg(x.value->>'description',' | ')
      FROM (SELECT value FROM jsonb_array_elements(d.definition->'stocks') WITH ORDINALITY
        ORDER BY ordinality LIMIT 8) x),'unavailable')), ' | ') links
 FROM resolved_sale_recipe_link l LEFT JOIN resolved_recipe_definition d ON d.recipe_id=l.recipe_id
 WHERE l.sale_item_stock_code=f.stock_code
) e ON true
LEFT JOIN resolved_reporting_pos_evidence pos ON pos.name_key=s.product_name_key;
