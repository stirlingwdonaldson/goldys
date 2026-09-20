# Goldy's Sales Reconciliation & Connectors (Phase 1, Slice 1)

**Status:** Draft — for review
**Date:** 2026-09-20
**Scope:** The Lightspeed and Cooking the Books (CTB) connectors, and the daily-sales reconciliation they feed. Grounded in the source-access discovery in `docs/connectors/source-access.md`.

## 1. Objective

Ship the first real vertical slice: pull sales data from Lightspeed and CTB into
the byte-faithful raw log, canonicalize it, and reconcile the two sources at the
level the data actually supports — **daily sales totals, keyed by trading date** —
surfacing field-level (day-level) discrepancies for staff to inspect and
manually override. This is the concrete realization of PRD Requirements 4, 5, 6,
and 7, corrected by what the sources actually expose.

## 2. Sources of Truth and Current Baseline

- Architecture invariants and the three-layer model: `docs/system-context.md`.
- Scope/acceptance criteria: `docs/prd.md` (Requirements 4–7).
- Connector isolation + raw-log + bitemporal + permission mechanisms already
  built: `docs/superpowers/specs/2026-09-19-phase-one-mvp-design.md` and the
  foundation plan (V1–V4 migrations, `SourceConnector` port, `IngestionRun`,
  `RawRecord`, `CanonicalSaleItem`, `CanonicalShift`, `PermissionService`).
- Access details (login flows, endpoints, export mechanics): `docs/connectors/source-access.md`.

## 3. What the data actually is (discovery result)

The PRD's example — *"`quantity_sold` from Lightspeed vs. CTB"* — assumed CTB
holds sales *line items* that overlap Lightspeed's. The real data is different
and, in one way, simpler:

- **Lightspeed** exports a *transaction-level* Sales Feed (one row per sale:
  `SaleID`, `SaleNo`, `SaleDate`, `Net Amount`, `Tax Amount`, `Tip`, `Total`),
  not line items. Line-item detail is a separate "Sales By" report.
- **CTB** exposes *daily revenue* entries (`Revenue/SearchRevenues`, the
  "SALES" summary) and *ingested POS sale items* (`Sale/SearchSaleItemsByDateRange`,
  populated by CTB's own Kounta/Lightspeed integration), plus a **variance
  report** (`Sale/GetVarianceReportData`) it already computes.

So the two sources overlap on **sales by trading day**. A sample comparison
(Lightspeed transaction totals vs. CTB daily revenue, 13–20 Sep 2026) showed the
daily figures match to within ~0.1–1.8% on seven of eight days, with one real
outlier (13 Sep: Lightspeed ~$6.7k / 32% higher than CTB). That day-level
difference is exactly the field-level conflict this platform is meant to surface.

## 4. Scope

### In scope

- **Lightspeed connector** — deterministic Playwright scrape: login → Sales Feed
  → export CSV → raw log (byte-faithful) → canonicalize to daily sales totals.
- **CTB connector** — authenticated AJAX pull: login → `Revenue/SearchRevenues`
  (and `Sale/SearchSaleItemsByDateRange`) → raw log → canonicalize.
- **Daily-sales matching** — keyed by trading date (deterministic, no tolerances).
- **Daily-total reconciliation** — detect per-day revenue conflicts between the
  two sources, compute resolved views, and let authorized staff append manual
  overrides.
- **Frontend wiring** — the existing demo-mode data screens already model the
  reconciliation UI; align the contract to the real daily-total shape and swap
  `liveApi` to the new endpoints.

### Out of scope

- **Sale-item / line-item matching** — requires time+amount tolerance design
  against real paired samples; deferred (see §11). It is a gated follow-on, not
  guessed here.
- CTB purchase-invoice reconciliation (COGS vs. sales) — a different slice.
- Deputy and OpenTable connectors — follow the same port pattern later.
- Rule engine, Conversational BI, Smart Exporter, Automation Hub (Phase 2).

## 5. Entity-Matching Strategy (documented, per PRD Requirement 7)

**Entity: the trading day.** Two records from different sources describe the
same event when their **trading date** is equal.

- Keys: `Lightspeed.SaleDate` truncated to the venue's trading date, and
  `CTB.Revenue.date` (both normalised to the venue timezone).
- No tolerances, no fuzzy matching — equality on the date key.
- Match outcomes: `MATCHED` (both sources present for the date), `UNMATCHED`
  (one source only — surfaced as "no data from source X", never silently omitted).
- This strategy is deterministic and fixture-testable; it does not guess any
  matching tolerance, so it satisfies Requirement 7 for the daily-total entity.

## 6. Connectors

Both adapters implement the existing `SourceConnector` port
(`fetch(watermark, IngestionSink)`), persist bytes before parsing, and record
failures through `IngestionFailure`.

### 6.1 Lightspeed (scrape)

- Authenticate via the email/password form; drive the Sales Feed export with a
  deterministic Playwright flow (selectors recorded in `source-access.md`).
- Persist the exported CSV bytes as the raw payload (byte-faithful), then parse
  into `CanonicalSaleItem` rows (aggregated per trading day for reconciliation).
- Failure modes: login failure, export-button absence (schema drift → `CONNECTOR_SCHEMA_MISMATCH`),
  empty export (`NO_NEW_DATA`).

### 6.2 CTB (authenticated AJAX)

- `POST /Account/Login` for the session cookie, then paginate
  `Revenue/SearchRevenues` (and, for the follow-on slice, `Sale/SearchSaleItemsByDateRange`).
- Persist each JSON response as the raw payload; canonicalize daily revenue.
- Failure modes: login failure, non-success `message.IsSuccess`, pagination
  stall, empty result set.

## 7. Reconciliation

- **Conflict detection** — per trading date, compare Lightspeed's aggregate
  total against CTB's revenue. Equal (within a documented rounding rule, e.g.
  cents) → agreed; otherwise → a day-level conflict with both source values.
- **Resolved views** — derived from canonical state + active manual overrides;
  recomputation never mutates canonical history (the existing bitemporal
  pattern holds).
- **Manual override** — staff select which source's total is authoritative for a
  date, with an optional reason; append-only, permission-gated via the existing
  `PermissionService`.
- **Observability** — a connector failure is a first-class `IngestionFailure`
  run state, distinct from "no new data" (foundation already supports this).

## 8. API Contract (frontend alignment)

Reuse and narrow the frontend contract already built in demo mode
(`docs/superpowers/specs/2026-09-20-frontend-data-screens-design.md`). The
reconciliation shapes become day-keyed:

- `ReconciliationException` → `{ id, recordId (trading date), entity, field
  ("daily_sales"), sources, status }`.
- New endpoints for `liveApi`: `GET /api/reconciliation/exceptions`,
  `GET /api/reconciliation/records/{date}`, `GET /api/dashboard/summary`,
  `GET /api/connectors`, `POST /api/reconciliation/records/{date}/override`.

The frontend screens, the demo fixtures, and the Vitest coverage already exist;
this slice adds the backend endpoints and points `liveApi` at them.

## 9. Testing

- Connector contract tests (both adapters) against the shared port, with
  fixture responses for the Lightspeed CSV and CTB JSON envelope.
- Matching tests: date-key equality, unmatched-source surfacing.
- Reconciliation tests: day conflict detection, rounding rule, override
  append-only + permission denial, recomputation preserving canonical history.
- The acceptance dataset must include the known 13 Sep outlier.

## 10. Boundaries

### Always

- Persist source bytes before parsing; raw log append-only.
- Canonical rows close (never overwrite); resolved views are derived.
- Route override actions through the sole `PermissionService`.
- Surface "no data from source X" explicitly.

### Ask first

- Sale-item matching tolerances (needs paired real samples — see §11).
- New sources, entity types, or permission-matrix rows.
- Any change to the connector port or bitemporal pattern.

### Never

- Write back to a source system.
- Guess matching tolerances or permission seed data.
- Hard-code credentials (env vars only).

## 11. Follow-On (gated)

**Sale-item / line-item matching** is the next slice. It needs a time+amount
tolerance design against paired real Lightspeed "Sales By" and CTB
`sale_items` samples, documented before implementation — the PRD's explicit
"no guessing" rule.
