-- V79 - Extend the read-only analytical role to the Batch B resolved projections.
--
-- V76/V78 created goldys_ro and granted SELECT on the resolved projections registered at those
-- points. This migration extends the same database-side allowlist to the four further resolved
-- projections registered for dataset coverage (Batch B): resolved_reservation_day,
-- resolved_recipe, resolved_recipe_ingredient and resolved_inventory_day. Keeps the DB allowlist
-- in step with the DatasetRegistry so a query over these projections resolves under a future
-- goldys_ro connection, and nothing else becomes readable.
--
-- Additive and idempotent: GRANT is idempotent, and the role is guaranteed to exist by V76
-- (recreated defensively for the same reason V76/V78 guard their own CREATE ROLE).
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'goldys_ro') THEN
    CREATE ROLE goldys_ro;
  END IF;
END $$;

GRANT SELECT ON
    resolved_reservation_day,
    resolved_recipe,
    resolved_recipe_ingredient,
    resolved_inventory_day
TO goldys_ro;
