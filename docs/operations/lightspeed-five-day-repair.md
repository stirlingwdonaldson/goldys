# Bounded Lightspeed historical repair

This operation accepts only the five original Look 9501 exports in
`goldys-report-preparation-2026-10-27.zip`. It never runs on startup, on a schedule,
or through the scheduled-report webhook. No production repair has been executed
as part of implementing it. No migration or configuration change is required.

## Approved source boundary

Supply one original CSV as multipart field `file`. The server checks its exact
SHA-256, then site **96181**, exact closed date, row count, unique line IDs,
required quantities/amounts and net-plus-tax arithmetic. Unsafe payment/product
joins remain prohibited. Editing or reserializing a file invalidates its hash.

| Closed date | Rows | Original CSV SHA-256 |
|---|---:|---|
| 2025-07-25 | 1,548 | `bd25c0ddd099c9581ff613a1869d96a4d13b2ca746e10743adf6a12e66fa6577` |
| 2025-08-17 | 1,131 | `cbc553e69e4fd6cb682d8910cf978d7b1e5f6b1a824cfc2ea528262645960149` |
| 2025-11-29 | 2,726 | `edd84cd94f711cbbdaf168e57f5bc35ad106186863c5f9ce347a993dcdacf656` |
| 2025-11-30 | 1,399 | `4c02adedc06fa81a4559e073d7f5da251c45ab5fccf7eb1e48be735364c0fce6` |
| 2025-12-01 | 657 | `c47a0bb2479e2970f8039a585aeeb9f693c454fd3e9e93fb5daf65dd291570e1` |

Dates are source closed dates in Australia–Sydney. They are not reinterpreted as
a different trading-day boundary.

## Preview, review, apply

Both routes require the normal authenticated session, CSRF token and table-driven
`connectors` **WRITE** permission. They are outside the public webhook subtree.
Call them on the already-running backend; preview requires no restart or startup
jobs. No frontend button or new permission grant is added.

1. POST the original file to
   `/api/connectors/lightspeed/historical-repair/preview`.
   This uses a PostgreSQL read-only, repeatable-read transaction and makes no raw,
   canonical, stage or ingestion-run writes.
2. Save and review the JSON. `sha256` fingerprints the complete `plan`, including
   per-line actions, current version IDs, old/candidate quantities and amounts,
   expected amount totals, holds and qualifications. Totals describe canonical
   sale-line amounts, not a promised change to every dashboard metric.
3. Obtain explicit operator approval for that exact preview and file/date.
4. Only then POST the same file to
   `/api/connectors/lightspeed/historical-repair/apply`, with multipart field
   `approvedPreviewSha256` set to the reviewed response's `sha256`.
   Missing/invalid approval, changed input or stale database state rejects before
   evidence or canonical writes. Review a fresh preview after rejection. The
   digest is an execution guard, not an electronic approval system.
5. Verify `inserted`/`corrected` counts and a new preview; reconcile source totals
   and wait for the existing debounced resolved projection before the next date.
   Do not infer completeness from HTTP success. Repeating requires a fresh
   preview and creates no duplicate canonical facts or repeat correction.

## Actions and unresolved flags

- `INSERT`: only a globally missing `LIGHTSPEED` receipt-line key. Preserve signed
  quantities, amounts, adjustments, zero-value lines and distinct legitimate
  identical items. Each insert flags unavailable receipt number and staff name;
  neither is guessed.
- `UNCHANGED`: retain the existing version and all metadata. Existing adjustment
  evidence is not enriched or replaced by this operation.
- `CORRECT_QUANTITY`: only **27919361920005**, **Peppercorn Sauce**, on
  **2025-12-01**, source amount zero, candidate quantity **1**, current quantity
  **3**, and other comparable source fields matching. A successor changes quantity
  and evidence provenance only. Receipt/staff, register/table, closed time,
  adjustment, source identity and original validity interval are preserved. The
  predecessor remains available with quantity 3.
- `HOLD`: other financial/quantity/comparable metadata differences, a matching ID
  on another date, or a current date-line absent from the export. Retain the
  current version. No general overwrite, deletion, name-based identity or division
  of multiplied measures is authorized.

Apply with holds commits only supported actions. The approved preview and hold
reasons are retained as immutable `lightspeed-historical-repair-plan` raw evidence
linked to the original CSV raw UUID. Both CSV and plan runs are marked `PARTIAL`
through failed canonicalization stages when holds remain, so the later plan run
cannot hide the incomplete outcome. These are maintenance/audit
flags, not a new reconciliation UI. Qualifications remain in every preview.

## Transactions and limits

Apply takes a PostgreSQL `SHARE ROW EXCLUSIVE` lock on `canonical_sale_item`, with
a 10-second acquisition timeout, before regenerating the preview. This blocks
concurrent writers, including inserts into absent keys, for the bounded repair;
ordinary reads remain available. Expect brief writer contention.

Original CSV bytes and the approved plan commit as raw evidence before canonical
writes. Canonical changes for the file share one transaction. An exception rolls
them all back and retains raw evidence plus a failed processing stage. Successful
changes publish normal post-commit projection events. Correction lineage follows
the successor's CSV raw UUID and predecessor's original raw UUID.

Independent daily counts/control totals are not supplied. The 22 known receipt
lines and five dates do not prove all history or same-line multiple-adjustment
semantics. Reported costs remain source observations, not verified historical
recipe COGS. CTB and all other history are outside this repair.

The earlier read-only comparison found 7,031 missing keys, 430 existing keys and
one eligible quantity correction. These are historical preview counts, not
execution constants: always regenerate against current state.
