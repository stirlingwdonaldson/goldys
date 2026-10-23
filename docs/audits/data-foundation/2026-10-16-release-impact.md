# Release prerequisites and impact boundaries

This is the publishable release summary. Live aggregate evidence, account-role populations and repair
candidate counts stay in the operator's local assessment; no production probe exports are included
in this PR or its history. The private preparation checkout is `/tmp/goldys-data-correctness-release`.

## Compatibility checks

The read-only assessment found the expected V82 boundary and matching applied migration checksums.
Production runs PostgreSQL 16. The release adds V83–V98; V74–V80 histories require a separate transition.
No current product-reference uniqueness or inventory raw-reference blocker was found at inspection.
Rerun preflight while quiesced; these findings are not an assurance of a later live state.

`ops/releases/2026-10-16-data-correctness/preflight.py` uses read-only transactions/timeouts and reports
aggregate candidates, expected-version/checksum failures, PG16 compatibility, analytical-role access
and migration uniqueness/FK hazards. It never repairs data/history. Tests cover changed/foreign/
failed/missing histories, BOM/line-ending checksum normalization and repeated CSV result sections.

The current Compose wiring uses the database container principal for Flyway and application access.
V88/V94 create/grant `goldys_ro`; the analytical executor explicitly assumes that SELECT-only role.
The release does not harden the existing application datasource principal. Separate app/migration
principals need an explicit membership grant and role-specific checks. After migration:

```sql
BEGIN READ ONLY;
SELECT pg_has_role(current_user,'goldys_ro','USAGE') AS can_assume_analytical_role;
SELECT has_table_privilege('goldys_ro','resolved_daily_sales','SELECT') AS resolved_read,
       has_table_privilege('goldys_ro','raw_record','SELECT') AS raw_read,
       has_table_privilege('goldys_ro','resolved_daily_sales','INSERT,UPDATE,DELETE') AS resolved_write;
ROLLBACK;
```

Expected: true / true / false / false. For separate principals check the actual application role.

## Historical scope

Existing canonical facts survive. Legacy invoice lines and PDF links receive nullable parent columns
without speculative assignment. Invoice-specific detail/supplier attribution can remain incomplete
until an evidence-backed repair is approved; known aggregate purchases remain available qualified.
A unique current invoice number is a candidate, not proof of original issuer attribution. Historical
coerced zeros, missing units, source revision meaning and tax bases are not repaired by deploying code.
Legacy product facts remain name-based; no historical code/name split is guessed. Empty observation
projections do not imply zero stock/waste. Completed old forecasts remain readable with unknown input
snapshots; queued legacy forecasts without a snapshot require recreation rather than invented inputs.

Daily-sales startup previously skipped populated projections. It now rebuilds them with the existing
transactional projector and accepted overrides/rules, so the corrected tax/net agreement logic takes
effect after deployment. A PostgreSQL regression simulates an old false agreement, verifies startup
reclassification without new canonical facts, then repeats startup. The completion log is a gate for
synchronous projection rebuilds; it does not assert asynchronous/provider health.

## Release gates

Follow the [operator runbook](../../operations/data-correctness-release-2026-10-16.md): verified main
commit, replay-disabled overlays, quiesced backup, isolated restore/migration rehearsal, paired app
rollout, permissions/UI checks and explicit restore approval if rollback needs a database restore.
No production migration, backup, restore rehearsal, replay, ingestion, restart or deployment was
performed in preparing this PR. Historical repair and production rollout remain separately approved.
