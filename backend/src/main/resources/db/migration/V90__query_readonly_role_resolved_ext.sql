-- V78 - Extend the read-only analytical role to the newly registered resolved projections.
--
-- V76 created goldys_ro and granted SELECT on the three datasets registered at that time.
-- The relational query backend (DatasetRegistry) has since registered four further resolved
-- projections: resolved_product_sales, resolved_payment_day, resolved_deleted_sale_day and
-- resolved_sale_item_day. This migration keeps the database-side allowlist in step with the
-- catalogue so that, if the application is ever wired to connect as goldys_ro, a query over
-- these projections still resolves — and no other table becomes readable.
--
-- Additive and idempotent: GRANT is idempotent, and the role is guaranteed to exist by V76
-- (recreated here defensively for the same reason V76 guards its own CREATE ROLE).
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'goldys_ro') THEN
    CREATE ROLE goldys_ro;
  END IF;
END $$;

GRANT SELECT ON
    resolved_product_sales,
    resolved_payment_day,
    resolved_deleted_sale_day,
    resolved_sale_item_day
TO goldys_ro;
