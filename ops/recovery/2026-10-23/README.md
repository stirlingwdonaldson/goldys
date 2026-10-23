# Restore ingestion coverage — 23 October 2026

Prepared locally; no production deployment, replay, source fetch, schedule change or repair has been performed. Publishing the branch, merging, deployment and production recovery require separate authorization. Production credentials/configuration are not copied into this directory.

## Changes

- Reuse the invoice PDF read-lock correction and PostgreSQL regression from PR #204 (`e18881f`).
- Accept CTB's real ungrouped product-day rows. Product identity is still source/site/code; null/blank group means an ungrouped observation. Conflicting repeats remain errors. No schema migration or invented group identifier.
- A recorded failed processing stage downgrades a stored push run to PARTIAL (or FAILED without persisted bytes) and writes a classified failure. Existing connector health and completeness reads therefore see the failure. A later successful stage does not erase the historical failure. Existing historical stage-only failures are not silently rewritten by deploying this change.
- Provide an explicitly enabled, single-report recovery runner. Preview is the default. Actual recovery only inserts missing canonical keys, never replaces newer facts, preserves raw bytes and fails on an unexpected manifest count. Each inserted line commits independently, so an interrupted pass is resumable.

This release does not add hourly/adjustment fields or claim external-source completeness.

## Real-sample verification

The manifest records 410,558 source rows / 409,565 distinct 2025 Lightspeed receipt-line IDs. The actual Java parser accepted every row. Thirteen keys repeat with conflicting modelled facts (quantity in 13, amount/tax in 11, cost in two); the recovery path holds them for review. Eleven of those keys are absent from canonical state. The other **376,533 missing keys** can be recovered without choosing between conflicting source observations. Two ambiguous keys already have canonical facts, which recovery leaves unchanged.

All seven retained dated CTB pages were streamed through the actual Java parser and updated batch in an isolated JVM: **954/954 rows accepted**, with no duplicate product/site keys per page and no missing code/quantity/amount. All groups are null/blank. The committed fixture is the latest 186-row page with business names and stock codes replaced; numeric source measures, site/group values and identity relationships are preserved. Earlier audit wording called all these groups null; the actual sample also uses empty strings.

`RetainedPayloadProbe.java` accepts a payload on stdin and emits counts only; it never starts Spring or connects to a database. Compile it against the built backend runtime classpath, then run `com.goldys.platform.connectors.ctb.RetainedPayloadProbe ctb` or `lightspeed`. The Lightspeed mode exits nonzero when conflicting repeats need review; that is the expected result for this report, not a parse failure. Source bodies and parser exception messages must stay out of logs/artifacts.

## Authorized rollout sequence to review

1. Review and publish the branch, run PR CI, then deploy the approved image through the existing release procedure. Keep legacy replay/backfill flags disabled using `ops/releases/2026-10-16-data-correctness/safety.yml`.
2. Check authenticated invoice list/detail/document GETs with an existing authorized session. Confirm PDF filenames no longer trigger a transaction error. No test accounts or permission changes are needed.
3. Allow one normal CTB scheduled pull, or explicitly approve one `POST /api/connectors/CTB/run` (not executed here). Its existing 90-day per-day window includes the missed October product days. Verify status, per-day canonical/product projection coverage and source-native measures. Seven failed retained pages cover only the first attempted historical day of their runs; replaying those seven alone cannot recover the missed recent October days.
4. Review `manifest.json`, confirm raw ID/hash/length against the immutable ledger, then run only the single-report preview. `preview.yml` contains the exact application options but has **not** been applied. It must use the fixed image and existing safety configuration.
5. Preview reports rows, keys, conflicting keys, held missing keys and missing unambiguous keys. Counts should agree with the manifest; current missing counts can decrease if other authorized work intervenes. Hold unexpected changes for review.
6. Following explicit production-replay approval, use the same options with `APP_LIGHTSPEED_SALE_ITEMS_RECOVERY_DRY_RUN=false`. Expected keys must remain 409565 and expected conflicts 13. The runner intentionally excludes ambiguous source keys and records how many missing ones remain held. It verifies all unambiguous keys exist after insertion. It does not verify that held conflicts have been resolved.
7. Allow the debounced sale-item projector to flush, then verify day/category sums against current canonical facts. Record 2025 receipt-to-payment coverage separately from money reconciliation; payment-created dates and sale-closed dates differ. Do not sum repeated CSV rows to establish merchandise totals.
8. Remove the recovery enable flag before the next routine startup. A repeat insert-only pass is idempotent, but loading/parsing this 235 MB report on every restart is unnecessary.

Application properties: `app.lightspeed-sale-items-recovery.enabled` (default off), `raw-id` (required when enabled), `dry-run` (default true), `expected-keys` (required positive count for writes), `expected-conflicts` (default zero). A read-only preview runner does not make the whole Spring application read-only: ordinary jobs/startup behavior still apply. Do not start an extra application against production just for preview without the normal maintenance controls. No Compose operation is authorized by this document.

## Remaining recovery checks

- The report parsed in an isolated JVM with a 3 GiB heap. Production replay runtime, peak memory under normal application load and projection completion have not been measured. Budget a maintenance window; monitor inserted-key progress and rerun only missing keys if interrupted.
- Do not run the legacy whole-ledger `app.lightspeed-sale-items-replay` for this repair: it can replace existing facts and swallows individual report failures.
- Review the 11 missing ambiguous keys against source evidence before modelling them. No first-row/last-row choice or unverified summing is applied.
- Latest inspected Lightspeed deliveries were 22 October around 06:03–06:10 Melbourne. Host uptime reports a 23 October 12:01 boot; frontend/backend/Postgres started around 12:03. This supports a downtime hypothesis for the missed 06:00 delivery but does not establish the shutdown time. Sender schedule/retry history remains unverified. The next three-day report may recover the recent window; verify its actual dates and key counts before claiming recovery.
- Fixing health applies to newly recorded failures. A separately reviewed reconciliation of historical stage failures may be useful; it is not part of this deployment.
