# Goldy's Product-Sales Exception Read Model (Read-Architecture Slice 2)

**Status:** Draft — for review
**Date:** 2026-10-06
**Scope:** The product-sales exceptions read model: project the per-product/day
reconciliation exceptions into the existing `reconciliation_exception` table
(`entity_type='product_sales'`), maintain them with a projector, and migrate the
product read paths off on-demand recomputation. Follows the pattern established
by the daily-sales slice (`2026-10-06-daily-sales-read-model-design.md`).

## 1. Objective

Finish the read side of product-sales reconciliation. Today
`ProductSalesReconciliationService.conflicts()` loads *all* current product
sales, groups in memory, then per product/day queries an override and a rule —
the remaining N+1 that the daily-sales slice did not touch. The dashboard
summary and the product-exceptions list both pay it. This slice materializes the
product exceptions into a projection and points those two consumers at it.

Per the daily-sales spec §15 this was scoped as "resolved_product_sales and
product exceptions"; this slice builds the **exceptions only**. No consumer reads
a resolved product quantity/amount yet, so the `resolved_product_sales` values
table is deferred (YAGNI) until a consumer (e.g. "top sellers" reporting) exists.

## 2. Current baseline

- Canonical: `canonical_product_sales` (bitemporal, append-only), keyed by
  `(product_name_key, trading_date, source_system)`, written by
  `CanonicalProductSalesService.record()`.
- Overrides: `product_sales_override`, keyed by `(product_name_key, trading_date)`,
  written by `ProductSalesOverrideService.save()`.
- Rules: `resolution_rule` (shared with daily sales), looked up for products as
  `product_sales/<product_name_key>` then the `product_sales/*` catch-all.
- On-demand recomputation: `ProductSalesReconciliationService.conflicts()` (the
  only product read that recomputes; there is no `resolved()` for products).
- Consumers of that path: `DashboardController.summary()` (`conflicts().size()`)
  and `ReconciliationController.productExceptions()`.

## 3. Scope

### In scope

- Flyway `V16`: add `trading_date` to the `reconciliation_exception` primary key.
- `ProductSalesResolver`: a pure, no-I/O extraction of the product resolution
  logic (§6).
- `ProductSalesProjector`: `recompute(productNameKey, date)` and
  `recomputeAll()` (§7).
- A `ProductSalesRecorded` event published by
  `CanonicalProductSalesService.record()`, plus trigger wiring for the projector
  (§8).
- `ProductSalesExceptionQuery`: `listAll()` and `countOpen()` (§9).
- Consumer migration: dashboard product-conflict count, product-exceptions list
  (§10).
- Removal of `ProductSalesReconciliationService` and `ProductSalesConflict` (§11).

### Out of scope (explicitly deferred)

- `resolved_product_sales` values table (no consumer yet).
- The product drill-in (`/api/reconciliation/products/{date}/{product}`) and the
  `products` list (`/api/reconciliation/products`) — both are already bounded
  (single-date / single query), not N+1.
- Batch canonicalisation, collection pagination.

## 4. Design decisions

### 4.1 Exceptions only

No `resolved_product_sales` table. The projector still computes the full
resolution (override → agreement → rule → unresolved) to decide whether a
product/day is an exception, but it persists only the exception row. When a
consumer needs resolved product values, add the values table then — the projector
will already compute the winning source's `quantity_sold`/`amount`.

### 4.2 Exception primary key migration

The daily-sales slice created `reconciliation_exception` with
`PRIMARY KEY (entity_type, entity_key, field_key)`. That was sufficient for daily
sales (`entity_key` = the ISO date) but cannot hold the same product conflicting
on two different dates (`entity_key` = product name). `V16` adds `trading_date`
to the key:

```sql
ALTER TABLE reconciliation_exception DROP CONSTRAINT reconciliation_exception_pkey;
ALTER TABLE reconciliation_exception ADD PRIMARY KEY (entity_type, entity_key, trading_date, field_key);
```

Existing daily rows satisfy the new key (their `entity_key` equals their
`trading_date`), so the migration is safe.

### 4.3 Per-pair recompute

Product identity is `(product_name_key, trading_date)`, so the incremental
recompute is per pair — not per date — to avoid recomputing an entire day's
products when one product changes during a backfill. `recomputeAll()` (rule
changes, deploy backfill) rebuilds every product exception.

### 4.4 Bean-cycle avoidance

As in the daily slice, `ProductSalesProjector` injects `ResolutionRuleRepository`
and `ProductSalesOverrideRepository` (not the services), so the trigger services
can inject the projector without a Spring bean cycle.

## 5. Schema (Flyway `V16__product_sales_exception_key.sql`)

Only the key migration above. No new tables. Product exception rows use:

| column | value |
|---|---|
| `entity_type` | `'product_sales'` |
| `entity_key` | product name key |
| `trading_date` | the trading date |
| `field_key` | `'product_sales'` (constant) |
| `status` | `'conflict'` or `'missing'` |
| `detected_at` | first observed |

The frontend `ProductExceptionDto.field` continues to show the product name
(derived from `entity_key` at read time), so the DTO contract is unchanged.

## 6. Resolution semantics (must be preserved exactly)

For a `(product_name_key, trading_date)` with sources `S` (each carrying
`quantity_sold` and `amount`), apply in order:

1. **Override** — if an active `product_sales_override` exists for the pair, it
   is resolved (no exception).
2. **No data** — if `S` is empty, no exception (there is nothing to reconcile).
3. **Agreement** — if every source matches the first **exactly** on both
   `quantity_sold` and `amount` (`compareTo == 0`, zero tolerance), resolved (no
   exception).
4. **Rule** — else look up the current rule `product_sales/<product_name_key>`,
   falling back to `product_sales/*`; if `RuleEvaluator.resolve` picks a source
   (evaluated on `quantity_sold`), resolved (no exception).
5. **Unresolved** — otherwise an exception with `status` = `'conflict'` (≥2
   sources disagreeing) or `'missing'` (<2 sources), via `classify`.

`classify(S)`: fewer than two sources → `'missing'`; all sources' `quantity_sold`
and `amount` exactly equal → `'agreed'`; otherwise `'conflict'`.

## 7. Projector

`ProductSalesProjector.recompute(String productNameKey, LocalDate date)`:

1. Load current canonical rows for the pair (one query), the current override
   for the pair (one query), and the effective rule — `product_sales/<key>` then
   `product_sales/*` (up to two queries).
2. Resolve via `ProductSalesResolver` (§6).
3. Reconcile the pair's exception row: insert (with `detected_at = now`) if a new
   exception, update `status` (preserving `detected_at`) if the status changed,
   delete if it became resolved.

`ProductSalesProjector.recomputeAll()`: delete every `entity_type='product_sales'`
exception row, load all current product sales (one query), all current product
overrides (one query), and **all** current `product_sales` rules in one bulk
query (built into a `fieldKey → rule` map, so the per-product rule lookup does
not reintroduce N+1), resolve every pair, and insert the open exceptions.

New repository methods required:

- `CanonicalProductSalesRepository.findCurrentByDateAndProduct(LocalDate, String)`
  (single pair) and `findCurrentByDates(Collection<LocalDate>)` (bulk, for the
  read-query source join); facade `CanonicalProductSalesQuery.currentProductSalesForDates(...)`.
- `ProductSalesOverrideRepository.findAllCurrent()` (bulk overrides for `recomputeAll`).
- `ResolutionRuleRepository.findByEntityTypeAndSupersededAtIsNull(String)` (bulk
  product rules for `recomputeAll`).
- `ReconciliationExceptionRowRepository.findByEntityTypeAndEntityKeyAndTradingDate(...)`,
  `deleteByEntityType(String)`, `countByEntityType(String)`.

## 8. Trigger wiring

- `CanonicalProductSalesService.record()` → publishes
  `ProductSalesRecorded(productNameKey, tradingDate)` after saving (same
  transaction).
- `ProductSalesProjectionListener` (`@EventListener`, synchronous) →
  `projector.recompute(productNameKey, tradingDate)`.
- `ProductSalesOverrideService.save()` → `projector.recompute(productNameKey, date)`.
- `ResolutionRuleService.save()` / `delete()` → also triggers
  `ProductSalesProjector.recomputeAll()` when `entityType == "product_sales"`
  (in addition to the existing `daily_sales` handling).

## 9. Read query + consumer migration

- `ProductSalesExceptionQuery.listAll()` → product exceptions (`entity_type =
  'product_sales'`, newest first) with per-source `quantity_sold`/`amount` values
  fetched via one bulk `IN (dates)` join to canonical.
- `ProductSalesExceptionQuery.countOpen()` → count of product_sales exceptions.

| Consumer | Before | After |
|---|---|---|
| `DashboardController.summary()` | `productSales.conflicts().size()` | `productSalesExceptions.countOpen()` |
| `ReconciliationController.productExceptions()` | `productSales.conflicts()` | `productSalesExceptions.listAll()` |

The drill-in (`productRecord`) and `products` list stay canonical.

## 10. Removal

`ProductSalesReconciliationService` and `ProductSalesConflict` become unused and
are deleted; their logic moves to `ProductSalesResolver`. `ProductSourceTotal`
stays (used by the resolver and the read query).

## 11. Testing

- `ProductSalesResolverTest` (ported from `ProductSalesReconciliationTest`):
  zero-tolerance agreement, conflict, missing, override precedence, product +
  `*` rule precedence, empty sources.
- `ProductSalesProjectorIntegrationTest` (Testcontainers): conflicting pair →
  exception; agreeing pair → none; override → resolves; product rule and `*`
  rule → resolves; `recomputeAll()` idempotent; `detected_at` preserved.
- `ProductSalesExceptionQueryIntegrationTest`: list (per-source values +
  newest-first) and count.
- Trigger integration test: record/override/rule wiring.
- Update `DashboardControllerTest` and `ReconciliationControllerTest`.

## 12. Boundaries

### Always

- Canonical + overrides + rules stay authoritative and append-only; the
  exception projection is disposable and reconstructible.
- Preserve §6 semantics exactly (zero tolerance; product rule then `*` catch-all;
  `quantity_sold` metric).
- Read paths query the projection, not canonical, for product conflicts.

### Ask first

- Adding `resolved_product_sales` values or new exception metadata.
- Changing the exception key or resolution precedence.

### Never

- Recompute product conflicts from canonical at read time.
- Mutate canonical or override/rule history from the projector.

## 13. Follow-on (gated)

`resolved_product_sales` (resolved `quantity_sold`/`amount` per product/day) once
a consumer exists — e.g. the dashboard "Top sellers" tile or a product-sales
reporting tool. Batch canonicalisation and collection pagination remain queued
separately.
