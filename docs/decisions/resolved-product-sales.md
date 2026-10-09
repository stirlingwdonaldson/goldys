# ADR — Resolved product-sales semantic layer

Status: accepted (implemented this pass)

## Context

Daily sales already follows the `raw → canonical → reconciliation → resolved → consumers`
pipeline: `canonical_daily_sales → DailySalesResolver → DailySalesProjector →
resolved_daily_sales → ResolvedDailySalesQuery → dashboards/reporting/AI`.

Product sales had canonical data, rules, overrides, reconciliation exceptions and a
`ProductSalesProjector`, but the projector only maintained the exception projection — there was
no `resolved_product_sales` table, and `DashboardController.topSellers()` read
`CanonicalProductSalesQuery.topProductsByAmount(...)` directly. When two sources report the same
product/day, that canonical aggregation sums both and double-counts a single business fact.

## Decision

Bring product sales to parity with the daily-sales pattern by adding a disposable
`resolved_product_sales` projection, a `ResolvedProductSalesQuery` read facade, and migrating the
only business-facing canonical read (`DashboardController.topSellers()`) to it.

`ProductSalesResolver` now returns a full result (resolution type, authoritative source, quantity,
amount) instead of only an exception status, so the projector can maintain both the resolved value
and the exception from a single resolution.

Canonical reads remain permitted for reconciliation internals, provenance drill-in, and projection
building only. The raw/canonical read facades (`CanonicalProductSalesQuery`,
`CanonicalDailySalesQuery`, `RawLedgerQuery`) may not be reached from business-facing packages
(dashboard/reporting/conversational), enforced by an ArchUnit test.

### Business rules (confirmed)

- **Single source is trusted:** a product reported by only one source resolves to that source
  (`resolution_type = "single"`), never flagged as `missing`. Sources do not overlap 1:1 at product
  level.
- **One-cent rounding tolerance:** agreement requires exact quantity and amount within `$0.01`
  (mirrors daily-sales tolerance).
- **Unresolved products are surfaced:** `topProductsByAmount` returns products with unresolved days,
  flagging them `hasConflict` and summing only resolved rows, rather than dropping them.

## Schema

`resolved_product_sales (trading_date, product_name_key, quantity_sold, amount, resolution_type,
authoritative_source, has_conflict, resolved_at)` — composite PK, disposable/recomputable, null
quantity/amount while unresolved. See `V17__resolved_product_sales.sql`.

## Consequences

- Business product metrics are derived from resolved rows only.
- Rule/override/canonical changes deterministically recompute the affected projection.
- The canonical `topProductsByAmount` aggregation is removed (its only consumer was the dashboard).
