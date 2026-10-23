ALTER TABLE resolved_recipe_ingredient
    ADD COLUMN stock_uom_shortname varchar(32),
    ADD COLUMN stock_usable numeric(14,4),
    ADD COLUMN stock_wastage numeric(14,4),
    ADD COLUMN is_broken_link boolean;

-- Rebuild the disposable projection: source detail ids can repeat within one recipe.
DELETE FROM resolved_recipe_ingredient;
INSERT INTO resolved_recipe_ingredient (
 id, parent_recipe_id, line_recipe_id, line_recipe_name, is_sub_recipe, recipe_detail_id,
 stock_id, stock_code, stock_description, portion_weight, unit_of_measurement,
 cost_per_qty, cost_per_unit, supplier_name, resolved_at,
 stock_uom_shortname, stock_usable, stock_wastage, is_broken_link)
SELECT gen_random_uuid(), parent_recipe_id, line_recipe_id, line_recipe_name, is_sub_recipe,
 recipe_detail_id, stock_id, stock_code, stock_description, portion_weight, unit_of_measurement,
 cost_per_qty, cost_per_unit, supplier_name, CURRENT_TIMESTAMP,
 stock_uom_shortname, stock_usable, stock_wastage, is_broken_link
FROM canonical_recipe_ingredient WHERE superseded_at IS NULL;
