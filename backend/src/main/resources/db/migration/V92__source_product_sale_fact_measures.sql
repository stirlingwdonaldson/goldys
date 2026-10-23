-- V80 - Extend source_product_sale_fact with the remaining CTB source-native measures.
--
-- V75 created source_product_sale_fact for the source cost/tax/discount/unit-price measures CTB
-- supplies per product-day. The retained ctb-revenue raw records also carry foodCost, profit and
-- uniqueGroupId (verified in the production payload-shapes evidence), which were dropped. food_cost
-- and profit are source-estimated (CTB recipe costing), distinct from purchasing COGS
-- (canonical_invoice_line) and from cost_per_unit/total_food_cost. unique_group_id preserves the
-- source's grouping namespace so distinct rows that share a stock code and day remain distinct.
--
-- Additive and nullable (production may hold rows predating this migration). No raw-ledger or
-- resolved-table change.
ALTER TABLE source_product_sale_fact
    ADD COLUMN food_cost      numeric(14,4),
    ADD COLUMN profit         numeric(14,4),
    ADD COLUMN unique_group_id varchar(255);

-- Keep the database-side read allowlist in step with the DatasetRegistry: source_product_sale_fact
-- is registered as a single-source canonical-direct dataset (like canonical_invoice_line), so
-- goldys_ro gains SELECT on it and nothing else. Idempotent and defensive (role guaranteed by V76).
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'goldys_ro') THEN
    CREATE ROLE goldys_ro;
  END IF;
END $$;

GRANT SELECT ON source_product_sale_fact TO goldys_ro;
