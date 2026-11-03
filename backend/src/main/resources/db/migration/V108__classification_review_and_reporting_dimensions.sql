-- Additive review state only: no historical assignment, hierarchy or source fact is changed.
ALTER TABLE classification_run ADD COLUMN context_snapshot text;
ALTER TABLE classification_run ADD COLUMN proposal_snapshot text;
ALTER TABLE classification_run ADD COLUMN naming_snapshot text;

-- Independent, effective-dated reporting decisions. Revisions append; UNKNOWN withdraws a mapping.
CREATE TABLE product_reporting_mapping (
 id bigserial PRIMARY KEY,
 scope varchar(32) NOT NULL CHECK (scope IN ('PRODUCT_NAME','CTB_RECIPE','CTB_STOCK','WASTE_LINE')),
 source_system varchar(64) NOT NULL,
 entity_key varchar(512) NOT NULL CHECK (btrim(entity_key) <> ''),
 effective_from date NOT NULL,
 sales_nature varchar(16) NOT NULL CHECK (sales_nature IN ('FOOD','BEVERAGE','OTHER','UNKNOWN')),
 stock_bucket varchar(32) NOT NULL CHECK (stock_bucket IN ('KEGS','WINE','SPIRITS','TINNIES','OTHER_BEVERAGE','MERCHANDISE','MEAT','SEAFOOD','POULTRY','DAIRY','FROZEN','BREAD','UNKNOWN')),
 item_role varchar(24) NOT NULL CHECK (item_role IN ('SALE_PRODUCT','INGREDIENT','MODIFIER','MERCHANDISE','OTHER','UNKNOWN')),
 business_area varchar(16) NOT NULL CHECK (business_area IN ('PUB','CAFE','SHARED','UNKNOWN')),
 lifecycle varchar(16) NOT NULL CHECK (lifecycle IN ('CURRENT','HISTORICAL','RETIRED','UNKNOWN')),
 reason text NOT NULL CHECK (btrim(reason) <> ''),
 actor varchar(255) NOT NULL,
 recorded_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
CREATE INDEX product_reporting_mapping_lookup ON product_reporting_mapping(scope,source_system,entity_key,effective_from DESC,id DESC);

-- Non-aggregating projection. Never join recipe or stock identities by normalized name.
CREATE VIEW resolved_classified_wastage AS
SELECT w.source_system, w.source_record_ref, w.raw_record_id, w.wastage_date,
       w.product_name_key, w.quantity, w.cost AS source_unit_cost,
       w.unit_of_measurement_name AS unit, w.unit_size, w.reason, w.supplier_name, w.recipe_id,
       m.id AS mapping_revision_id, m.scope AS mapping_scope, m.reason AS mapping_reason,
       COALESCE(m.sales_nature,'UNKNOWN') AS sales_nature,
       COALESCE(m.stock_bucket,'UNKNOWN') AS stock_bucket,
       COALESCE(m.item_role,'UNKNOWN') AS item_role,
       COALESCE(m.business_area,'UNKNOWN') AS business_area,
       COALESCE(m.lifecycle,'UNKNOWN') AS lifecycle
FROM canonical_wastage w
LEFT JOIN LATERAL (
 SELECT r.* FROM product_reporting_mapping r
 WHERE r.source_system=w.source_system AND r.effective_from<=w.wastage_date
 AND ((r.scope='WASTE_LINE' AND r.entity_key=w.source_record_ref)
   OR (r.scope='CTB_RECIPE' AND w.recipe_id>0 AND r.entity_key=w.recipe_id::text))
 ORDER BY CASE r.scope WHEN 'WASTE_LINE' THEN 0 ELSE 1 END, r.effective_from DESC, r.id DESC
 LIMIT 1
) m ON true
WHERE w.superseded_at IS NULL;
