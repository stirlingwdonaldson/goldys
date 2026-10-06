# Goldy's Daily Sales Read Model (Read-Architecture Slice 1)

**Status:** Draft — for review
**Date:** 2026-10-06
**Scope:** The daily-sales resolved read model: a persisted `resolved_daily_sales`
projection plus a `reconciliation_exception` projection, a reconciliation
projector, and the migration of daily-sales read paths off on-demand
recomputation. Grounded in the architectural review (`docs/` architectural
assessment) and the existing reconciliation implementation.

## 1. Objective

Finish the read side of the daily-sales pipeline. Today every read recomputes
reconciliation state on demand from canonical rows, causing N+1 query patterns
(`DailySalesReconciliationService.conflicts()` loads *all* current daily sales,
then per date loads an override and a rule). This slice materializes the resolved
daily-sales state into disposable projection tables maintained by a projector,
and points the dashboard, the Sales "latest" read, Ask Goldy's, and the
reconciliation exceptions list at those projections.

The invariant this restores: **reads consume resolved values, never canonical
per-source rows**, and a resolved value is computed once (on write) rather than
on every read.

## 2. Current baseline

- Canonical: `canonical_daily_sales` (bitemporal, append-only), written by
  `CanonicalDailySalesService.record()` keyed by trading date, one row per
  (date, source).
- Overrides: `daily_sales_override` (append-only), written by
  `DailySalesOverrideService.save()`.
- Rules: `resolution_rule` (append-only), written by
  `ResolutionRuleService.save()` / `delete()`.
- On-demand recomputation: `DailySalesReconciliationService.conflicts()` and
  `.resolved(date)`.
- Consumers of the on-demand path: `DashboardController.summary()`,
  `ReconciliationController.exceptions()`, `SalesController.latest()`,
  `GetSalesByPeriodTool`.

The exact resolution semantics are specified in §6 and must be preserved
verbatim by the projector.

## 3. Scope

### In scope

- Flyway `V15__daily_sales_read_model.sql`: two projection tables (§5).
- `DailySalesProjector`: `recompute(LocalDate...)` and `recomputeAll()` (§7).
- A pure `DailySalesResolver` extracted from the existing reconciliation service
  so the projector and any future consumer share one resolution implementation.
- Event wiring: `CanonicalDailySalesService.record()` publishes a
  `DailySalesRecorded` event; override/rule services trigger the projector
  directly (§8).
- Read queries: `ResolvedDailySalesQuery` and `ReconciliationExceptionQuery`
  (§9).
- Consumer migration: dashboard summary, `GET /api/sales/latest`,
  `GetSalesByPeriodTool`, daily exceptions list (§10).
- Removal of the now-redundant `DailySalesReconciliationService` (§11).

### Out of scope (explicitly deferred)

- Product-sales projection (`resolved_product_sales`, product exceptions) —
  product reconciliation stays on-demand. It is the next slice.
- Batch canonicalisation (`recordBatch`), CTB watermark/cursor, collection
  pagination, ingestion stage-success classification, Actuator/Micrometer.
- `gst_total` / `net_total` on the resolved projection (no current consumer;
  add when one appears).

## 4. Design decisions

### 4.1 Trigger: synchronous same-transaction event

The projector must run when resolution inputs change, without introducing a
package or bean cycle (today `reconciliation` depends on `canonical`, not the
reverse). Chosen approach:

- `CanonicalDailySalesService.record()` publishes a `DailySalesRecorded(LocalDate)`
  domain event (defined in `canonical`) via `ApplicationEventPublisher`.
- A `DailySalesProjectionListener` in `reconciliation` handles it with a plain
  `@EventListener`, so the projection write runs **in the same transaction** as
  the canonical write — they commit or roll back atomically.
- `DailySalesOverrideService.save()` and `ResolutionRuleService.save()` /
  `delete()` are already in `reconciliation`, so they call the projector
  directly (no event). A **daily_sales** rule change triggers `recomputeAll()`
  because one rule can affect every date.

Rejected alternatives:

- `@TransactionalEventListener(AFTER_COMMIT)`: decoupled, but the projection
  writes in a separate transaction, so a projection failure leaves drift until
  replay.
- Caller-orchestrated (ingestion collects affected dates): clean, but requires
  restructuring the ingestion flow and loses per-record correctness.

### 4.2 Exception source values: lean table + bulk join

`reconciliation_exception` stores no source snapshot. The exceptions list
endpoint fetches source values with **one** bulk `IN (open dates)` query against
canonical. Canonical stays authoritative; there is no denormalized snapshot to
drift. (Alternative — a JSONB `sources` column — was considered and rejected to
avoid duplicated, driftable data.)

## 5. Schema (Flyway `V15__daily_sales_read_model.sql`)

```sql
CREATE TABLE resolved_daily_sales (
  trading_date         DATE PRIMARY KEY,
  total_sales          NUMERIC(14,4),          -- null when unresolved; matches canonical_daily_sales.total_sales
  resolution_type      TEXT NOT NULL,          -- 'agreed' | 'override' | 'rule' | 'conflict' | 'missing'
  authoritative_source TEXT,                   -- source name / 'agreed' / null
  has_conflict         BOOLEAN NOT NULL,
  resolved_at          TIMESTAMPTZ NOT NULL
);

CREATE TABLE reconciliation_exception (
  entity_type  TEXT NOT NULL,                  -- 'daily_sales'
  entity_key   TEXT NOT NULL,                  -- date string (ISO-8601)
  trading_date DATE NOT NULL,
  field_key    TEXT NOT NULL,                  -- 'daily_sales'
  status       TEXT NOT NULL,                  -- 'conflict' | 'missing'
  detected_at  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (entity_type, entity_key, field_key)
);
```

Both tables are disposable projections. Canonical + overrides + rules remain the
authoritative source; `truncate` + `recomputeAll()` always reconstructs them.

## 6. Resolution semantics (must be preserved exactly)

For a trading date with at least one current canonical row, apply in order:

1. **Override** — if an active override exists, resolve to the override's
   authoritative source: `resolution_type='override'`,
   `authoritative_source=<source>`, `total_sales=<that source's total>`,
   `has_conflict=false`.
2. **Agreement** — else if every source agrees within one cent
   (`classify(...) == "agreed"`), resolve to that value:
   `resolution_type='agreed'`, `authoritative_source='agreed'`,
   `total_sales=<first source's total>`, `has_conflict=false`.
3. **Rule** — else if the current `daily_sales`/`daily_sales` rule picks a source
   via `RuleEvaluator.resolve`, resolve to it: `resolution_type='rule'`,
   `authoritative_source=<chosen source>`, `total_sales=<chosen source's total>`,
   `has_conflict=false`.
4. **Unresolved** — otherwise no resolved total: `total_sales=NULL`,
   `authoritative_source=NULL`, `has_conflict=true`. The status distinguishes
   the two unresolved cases via `classify`: fewer than two sources →
   `resolution_type='missing'`; two or more disagreeing sources →
   `resolution_type='conflict'`.

A date with zero current canonical rows produces **no** `resolved_daily_sales`
row and no exception row.

`reconciliation_exception` holds exactly the `has_conflict=true` dates:
`status` is `'conflict'` or `'missing'` as above, `detected_at` is the time the
exception was first observed (set on insert, preserved across recomputes while
the exception persists).

## 7. Projector

`DailySalesProjector.recompute(LocalDate... dates)`:

1. Bulk-load current canonical rows for the dates (one `IN (:dates)` query),
   current overrides for the dates (one `IN (:dates)` query), and the current
   `daily_sales`/`daily_sales` rule (one query). This requires new bulk
   repository methods: `CanonicalDailySalesRepository.findCurrentByDates(...)`
   and `DailySalesOverrideRepository.findCurrentByDates(...)`.
2. Resolve each date in memory via `DailySalesResolver` (§6).
3. Upsert `resolved_daily_sales` rows.
4. Reconcile `reconciliation_exception` rows for the affected dates: insert rows
   for newly-open exceptions (`detected_at = now`), update `status` for
   exceptions that remain open **without** resetting `detected_at`, and delete
   rows for dates that have become resolved. `detected_at` thus records when the
   exception first opened and is preserved until it clears.

`DailySalesProjector.recomputeAll()`: truncate both tables, load all current
canonical rows + overrides + the rule, resolve every date, and insert. Used for
deploy backfill, rule changes, and manual repair.

`DailySalesResolver` is a pure (no-I/O) extraction of the resolution logic,
sharing `classify` and the one-cent tolerance with the existing code.

## 8. Trigger wiring

- `CanonicalDailySalesService.record()` → after saving, publish
  `DailySalesRecorded(input.tradingDate())`. JPA's default flush-before-query
  guarantees the projector's read sees the just-written row within the same
  transaction.
- `DailySalesOverrideService.save()` → `projector.recompute(date)`.
- `ResolutionRuleService.save()` / `delete()` → if `entityType == "daily_sales"`,
  `projector.recomputeAll()`.

## 9. Read queries

- `ResolvedDailySalesQuery`: `latest()` (most recent date + resolved value),
  `between(start, end)`, `countOpenConflicts()` — each a single SQL query.
- `ReconciliationExceptionQuery`: `listDaily()` (all `daily_sales` exceptions,
  newest first) plus a bulk source-value lookup for the open dates.

## 10. Consumer migration

| Consumer | Before | After |
|---|---|---|
| `SalesController.latest()` | `resolved(latestDate)` on demand | `ResolvedDailySalesQuery.latest()` |
| `GetSalesByPeriodTool` | loops `resolved(date)` per day | `ResolvedDailySalesQuery.between(start,end)` |
| `DashboardController.summary()` | `dailySales.conflicts().size()` | `ResolvedDailySalesQuery.countOpenConflicts()` + product `conflicts().size()` (product stays on-demand) |
| `ReconciliationController.exceptions()` | `dailySales.conflicts()` | `ReconciliationExceptionQuery.listDaily()` + bulk source values |

Frontend DTOs are unchanged; only the backend read path changes. The
`GetSalesByPeriodTool` "source" field now emits the plain `authoritative_source`
(no `override:`/`rule:` prefix), a minor, intentional cleanup.

## 11. Removal

`DailySalesReconciliationService` becomes unused after §10. Delete it and move
its resolution logic into `DailySalesResolver`; port its unit tests to the
resolver and projector.

## 12. Replay / backfill

On startup, if `resolved_daily_sales` is empty (first deploy of the table),
run `recomputeAll()` to seed it. A manual repair path (`recomputeAll()`)
remains for operational recovery.

## 13. Testing

- `DailySalesResolver` unit tests (ported from `DailySalesReconciliationTest`):
  agreed / within-cent / conflict / missing, override precedence, rule
  precedence, unresolved.
- Projector integration tests (Testcontainers Postgres):
  - two agreeing sources → `resolved_daily_sales` `agreed`, no exception;
  - conflicting sources → `has_conflict`, `reconciliation_exception` row;
  - override save → projector flips the date to `override`;
  - rule save → `recomputeAll()` resolves across all dates;
  - `recomputeAll()` rebuild reproduces identical projections from canonical.
- Controller tests updated for the new query beans (`SalesControllerTest`,
  `DashboardControllerTest`, `ReconciliationControllerTest`).
- `GetSalesByPeriodToolTest` updated for the bulk read.

## 14. Boundaries

### Always

- Canonical + overrides + rules stay authoritative and append-only; projections
  are disposable and reconstructible.
- The projector preserves the §6 resolution semantics exactly.
- Read paths query projections, not canonical, for resolved values.

### Ask first

- Adding `gst_total`/`net_total` or new resolution metadata to the projection.
- Changing the trigger mechanism (event → outbox/worker).
- Any change to the resolution precedence order.

### Never

- Derive resolved values from canonical at read time.
- Mutate canonical or override/rule history from the projector.
- Introduce an async queue for this slice (no Kafka/outbox yet).

## 15. Follow-on (gated)

**Product-sales read model** is the next slice: `resolved_product_sales` and
product exceptions, following this same projector pattern. Also queued
separately: batch canonicalisation, CTB watermark, pagination, ingestion
stage-success, and Actuator/Micrometer observability.
