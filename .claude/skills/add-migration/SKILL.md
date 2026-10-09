---
name: add-migration
description: Write a Flyway schema migration for Goldy's (new table, column, index, or permission seed). Use for any database schema change; Hibernate never alters the schema here.
---

The schema is owned by Flyway in `backend/src/main/resources/db/migration`, and
Hibernate runs with `ddl-auto: validate`. Entities must match what migrations create.

## Rules

- **Next number.** Name it `V<n>__<snake_case_description>.sql`, where `<n>` is one
  more than the highest existing version (`ls ... | sort -V | tail -1`). Before
  committing, check no other branch has taken the same number; renumber if `main`
  moved.
- **Never edit an applied migration**, even a typo. Write a new corrective one.
  Production has applied everything on `main`.
- **Raw ledger is append-only.** Never add a migration that updates or deletes
  `raw_record` rows or weakens the `raw_record_append_only` trigger.
- **Canonical tables** carry the bitemporal columns (`valid_from`, `valid_to`,
  `recorded_at`, `superseded_at`) and `raw_record_id uuid NOT NULL REFERENCES
  raw_record(id)`, matching `BitemporalEntity`, plus partial unique indexes on the
  identity columns `WHERE superseded_at IS NULL` (one current version per entity).
  Copy the shape from `V21__labour.sql` (canonical, resolved and override tables in
  one migration) or `V11__reservations.sql`.
- **Resolved tables** are disposable projections: no data that exists only there.
- **Permission seeds** insert `(department, seniority, resource, can_read, can_write)`
  rows for `ALL × OWNER` only, using `gen_random_uuid()` ids (see
  `V23__seed_domain_permissions.sql`). Never guess BOH/FOH grants.
- Use `timestamp(6) with time zone` for instants and `numeric(14,4)` style for money
  and quantities, as existing tables do.
- New columns on populated tables need a default or must be nullable; production has
  real data.

## Verify

`./gradlew test --tests '*DatabaseMigrationTest'` applies every migration to an empty
PostgreSQL 16 container, and the integration suite validates entities against the
result. Run the full `./gradlew test` before pushing.
