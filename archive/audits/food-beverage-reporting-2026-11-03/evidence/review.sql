WITH totals AS (SELECT product_name_key,sum(amount) gross FROM resolved_product_sales WHERE trading_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY 1 ORDER BY sum(amount) DESC LIMIT 100),
labels AS (SELECT regexp_replace(btrim(regexp_replace(lower(item_name),'[^a-z0-9]+',' ','g')),'^new ','') product_name_key,string_agg(DISTINCT category_name,' | ') categories FROM canonical_sale_item WHERE superseded_at IS NULL GROUP BY 1)
SELECT t.product_name_key,t.gross,l.categories pos_categories,
 (SELECT count(DISTINCT c.source_product_ref) FROM canonical_product_sales c WHERE c.product_name_key=t.product_name_key AND c.superseded_at IS NULL) identities
FROM totals t LEFT JOIN labels l USING(product_name_key) ORDER BY t.gross DESC;
SELECT column_name FROM information_schema.columns WHERE table_name IN ('canonical_recipe_definition','raw_record') ORDER BY table_name,ordinal_position;
SELECT recipe_id,count(*),min(wastage_date),sum(quantity*cost) value,string_agg(DISTINCT product_name_key,' | ') names FROM canonical_wastage WHERE superseded_at IS NULL AND wastage_date BETWEEN '2026-10-26' AND '2026-11-01' GROUP BY recipe_id ORDER BY sum(quantity*cost) DESC;
