# Goldy's Line-Item Reconciliation (Phase 1, Slice 2)

**Status:** Draft — for review
**Date:** 2026-09-21
**Scope:** Reconcile the two sources at the **per-product line-item** level — the natural next step after the daily-total slice. Grounded in the line-item discovery in `docs/connectors/line-item-matching.md`.

## 1. Objective

Extend the reconciliation from "is this day's total right?" to "which products differ, and by how much?". Match Lightspeed's per-product sales against CTB's per-product `sale_items` for each trading day, surface the products whose quantity or amount disagree, and let authorized staff override which source is authoritative — reusing the same canonical/bitemporal/reconciliation/override machinery the daily slice built.

## 2. Sources of Truth and Current Baseline

- Architecture invariants: `docs/system-context.md`.
- Daily-total slice (already shipped, PRs #8/#9/#10): `CanonicalDailySales`, `DailySalesReconciliationService`, `DailySalesOverride`, the `SourceConnector` port, the byte-faithful raw log, and the frontend reconciliation screen.
- Line-item discovery: `docs/connectors/line-item-matching.md`.
- Access reference: `docs/connectors/source-access.md`.

## 3. What the data actually is (discovery result)

Both sources expose **per-product** line items (not per-transaction):

- **Lightspeed** — the "Sales By" report (`/report/salesummarybyproduct`) exports one row per product: name, quantity, sale amount. Its CSV has an **empty `Product Number`** column, so there is no SKU on the Lightspeed side.
- **CTB** — `Sale/SearchSaleItemsByDateRange` returns one row per `stockCode`: `stockDescription`, `quantitySold`, `amount` (incl. tax), `taxAmount`. The API exposes only the per-product aggregate; modifier linkage (`parentStockCode`/`childGroupData`) is null for every `searchType`.

Measured on a paired sample (14–20 Sep 2026):

- **285 products match** by normalized name (plus a `Kids …` ↔ `New Kids …` alias).
- Matched `quantitySold` and `amount` agree **to the cent** for nearly all products; the total matched residual is **$105.67** (~0.06%, across 39 products).
- **17 products are Lightspeed-only** — modifiers/sides (`Dill Aioli`, `Parmesan`, `Large Chips`, `Cash Out`, …) totalling **$1,145.84** that CTB's Kounta integration does not ingest.
- The daily difference reconciles **exactly**: `$1,251.51 = $105.67 (matched residual) + $1,145.84 (modifiers)`.

Conclusion: matching is **name-keyed, tolerance-zero**, and the only unmatched class is the modifiers, surfaced explicitly. No SKU or per-transaction data is required.

## 4. Scope

### In scope

- **Lightspeed line-item connector** — a scheduled Lightspeed Insights report ("sales by product") delivered to the webhook receiver, parsed into per-product rows (a new parser; no scraper).
- **CTB sale-items connector** — extend the existing CTB connector with `Sale/SearchSaleItemsByDateRange`.
- **Canonical per-product sales** — a new canonical entity keyed by (normalized product name, trading date, source) holding `quantitySold` and `amount`.
- **Line-item reconciliation** — match by name (with the alias), detect per-product quantity/amount conflicts, and surface the 17 modifiers as "no data from CTB".
- **Manual override** — at the product level, reusing the append-only, permission-gated override pattern.
- **Frontend** — extend the reconciliation screen to drill into per-product conflicts.

### Out of scope

- Per-transaction matching (the data is per-product; transaction-level needs a different source).
- The 17 modifiers beyond surfacing them as "no data from CTB" (no attempt to map them into CTB).
- Deputy and OpenTable connectors.

## 5. Matching strategy (documented, per PRD Requirement 7)

**Entity: the product × trading day.** A Lightspeed "Sales By" row and a CTB
`sale_items` row describe the same thing when their **normalized product name** is
equal.

- Normalize: lowercase, strip non-alphanumerics, collapse whitespace.
- Alias: strip a leading `New ` from the CTB name (resolves `Kids …` ↔ `New Kids …`).
- Tolerance: **zero** — compare `quantitySold` and `amount` on exact equality; the
  matched data already agrees to the cent.
- Match outcomes: `MATCHED` (both sources present, equal), `CONFLICT` (both present,
  differ), `UNMATCHED` (one source only — surfaced as "no data from source X", never
  silently dropped). This is deterministic and fixture-testable; no tolerance is guessed.

## 6. Connectors

Both adapters implement the existing `SourceConnector` port, persist bytes before
parsing, and record failures through `IngestionFailure`.

### 6.1 Lightspeed (scheduled report webhook)

- A scheduled Insights report ("sales by product") posts to a new webhook endpoint
  `POST /api/ingest/lightspeed-products`, mirroring the daily slice's receiver (a
  distinct endpoint because the per-product CSV has a different shape and parser).
- A new parser maps the per-product CSV into per-product rows (name, quantity, amount).
- Failure modes: missing columns (schema drift → `CONNECTOR_SCHEMA_MISMATCH`), empty
  export.

### 6.2 CTB (authenticated AJAX)

- Extend `CtbConnector`/`CtbClient` with `Sale/SearchSaleItemsByDateRange`
  (`fromDate`/`toDate`/`searchType=-1`, paged), persisting each page's JSON as the raw
  payload and parsing per-`stockCode` rows.

## 7. Reconciliation

- **Conflict detection** — per (product, day), compare Lightspeed's `quantity`/`amount`
  against CTB's `quantitySold`/`amount`. Equal → agreed; otherwise → a per-product
  conflict carrying both source values.
- **Unmatched surfacing** — a product present in only one source is reported as
  "no data from source X" (this is where the 17 modifiers land).
- **Resolved views** — derived from canonical state + active overrides; recomputation
  never mutates canonical history.
- **Manual override** — per (product, day), select which source's quantity/amount is
  authoritative, with an optional reason; append-only, permission-gated via the sole
  `PermissionService`.

## 8. API Contract

Reuse the daily slice's shapes, keyed at the product level:

- `GET /api/reconciliation/products/exceptions` → per-product conflicts/unmatched rows.
- `GET /api/reconciliation/products/{date}/{product}` → per-product drill-in.
- `POST /api/reconciliation/products/{date}/{product}/override` → product override.
- `GET /api/dashboard/summary` — `openConflicts` now counts product-level conflicts.

The frontend screen, demo fixtures, and Vitest coverage follow the existing pattern.

## 9. Testing

- Parser contract tests for the Lightspeed per-product CSV and the CTB `sale_items`
  envelope (fixtures from the discovery samples).
- Matching tests: name normalization, the `New ` alias, exact-equality conflict
  detection, unmatched surfacing.
- Reconciliation tests: per-product conflict + override + permission denial.
- The acceptance dataset must include the 14–20 Sep sample (285 matched, $105.67
  residual, 17 modifiers).

## 10. Boundaries

### Always

- Persist source bytes before parsing; raw log append-only.
- Canonical rows close (never overwrite); resolved views are derived.
- Route overrides through the sole `PermissionService`.
- Surface "no data from source X" explicitly.

### Ask first

- Any matching tolerance (none is needed here — exact equality holds).
- New sources, entity types, or permission-matrix rows.
- Any change to the connector port or bitemporal pattern.

### Never

- Write back to a source system.
- Guess matching tolerances or permission seed data.
- Hard-code credentials (env vars only).

## 11. Follow-on (gated)

Transaction-level matching (per individual sale, with modifiers) is out of scope until
a source exposes per-transaction line items with a stable SKU; the 17 modifiers are a
known, surfaced gap rather than a hidden one.
