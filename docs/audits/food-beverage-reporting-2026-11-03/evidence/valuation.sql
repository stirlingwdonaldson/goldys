SELECT w.wastage_record_id,w.source_record_ref,w.product_name_key,w.quantity,w.cost,w.unit_of_measurement_name,w.unit_size,w.recipe_id,
 (convert_from(r.payload_bytes,'UTF8')::jsonb)->'totalCount' AS advertised_count,
 jsonb_typeof(convert_from(r.payload_bytes,'UTF8')::jsonb) AS shape
FROM canonical_wastage w JOIN raw_record r ON r.id=w.raw_record_id WHERE w.superseded_at IS NULL AND w.wastage_record_id IN (149,159,160,170) ORDER BY w.wastage_record_id,w.source_record_ref;
SELECT fetcher_identity,count(*) FROM raw_record WHERE source_system='CTB' AND fetcher_identity LIKE '%wastage%' GROUP BY 1;
SELECT d.recipe_id,d.name,d.unit,d.portion_count,d.portion_size,d.cost_per_portion,d.total_cost,
 d.menu_description, string_agg(DISTINCT l.recipe_folder_name,' | ') folders
FROM canonical_recipe_definition d LEFT JOIN canonical_sale_recipe_link l ON l.recipe_id=d.recipe_id AND l.superseded_at IS NULL
WHERE d.superseded_at IS NULL AND d.recipe_id IN (SELECT DISTINCT recipe_id FROM canonical_wastage WHERE superseded_at IS NULL AND wastage_date BETWEEN '2026-10-26' AND '2026-11-01')
GROUP BY d.id ORDER BY d.recipe_id;
