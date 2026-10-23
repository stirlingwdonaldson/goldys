-- V76 - Read-only analytical role for the relational query backend.
--
-- A dedicated least-privilege role that can only SELECT the approved analytical read models. It
-- cannot see the raw ledger or any canonical table that has a resolved projection, so even a
-- miscompiled query cannot bypass reconciliation. The application connects read-only (a separate
-- deployment concern); this role is the database-side enforcement of that boundary.
--
-- Additive and idempotent: creates the role only if absent, then grants SELECT on the three
-- registered datasets. No data change, no permission seeds.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'goldys_ro') THEN
    CREATE ROLE goldys_ro;
  END IF;
END $$;

GRANT SELECT ON resolved_daily_sales, canonical_invoice, canonical_invoice_line TO goldys_ro;
