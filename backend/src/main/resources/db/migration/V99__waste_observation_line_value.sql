-- Waste observations exposed CTB's per-unit cost as source_value and left unit_cost null.
-- CTB's wastage-detail `cost` is per stock unit (verified against the live site 2026-10-23:
-- record 149 lines 0.5x40 + 0.5x56.65 + 4x26.5 = 154.325, exactly the header total CTB shows).
-- So for WASTE rows unit_cost is the source cost and source_value is quantity x cost, the same
-- per-line value CTB displays. COUNT rows are unchanged. Column names, order and types are kept,
-- so CREATE OR REPLACE is valid and existing grants on the view stay in place.
CREATE OR REPLACE VIEW resolved_inventory_observation AS
SELECT 'COUNT'::text AS kind, c.source_system, c.source_record_ref, c.logical_entity_id,
       c.id AS canonical_version_id, c.raw_record_id, c.counted_date AS observation_date,
       c.product_name_key, c.stock_code, c.quantity_on_hand AS quantity,
       c.unit_of_measurement_name AS unit, NULL::varchar(64) AS unit_size,
       c.unit_cost, c.line_value AS source_value, c.area_name,
       s.is_partial AS stocktake_partial, s.is_locked AS stocktake_locked,
       c.stocktake_id AS parent_source_id, c.recorded_at
FROM canonical_stock_count c
LEFT JOIN canonical_stocktake s ON s.source_system=c.source_system
  AND s.stocktake_id=c.stocktake_id AND s.superseded_at IS NULL
WHERE c.superseded_at IS NULL
UNION ALL
SELECT 'WASTE', w.source_system, w.source_record_ref, w.logical_entity_id,
       w.id, w.raw_record_id, w.wastage_date,
       w.product_name_key, NULL::varchar(255), w.quantity,
       w.unit_of_measurement_name, w.unit_size, w.cost,
       round(w.quantity * w.cost, 4)::numeric(14,4),
       NULL::varchar(255), NULL::boolean, NULL::boolean, w.wastage_record_id, w.recorded_at
FROM canonical_wastage w WHERE w.superseded_at IS NULL;
