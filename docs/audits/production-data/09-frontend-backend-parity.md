# 09 — Database → API → screen consistency

## Runtime verification and limits

**Production fact:** frontend build ID `sMnma63DXhth4pBn9fm27`; deployed JS chunk
`6225-5d4eccc701baac83.js` initialises DemoModeProvider with `useState(!1)` (false).
This verifies **build default LIVE**. `frontend/lib/demo-mode.tsx:33–43` allows a per-browser
`goldys-demo-mode` override. No user browser preference was inspected, so the owner's current session mode is unknown.

Unauthenticated safe GETs with both `Accept: */*` and `application/json`:
health **200 JSON UP**; sales/data/inventory endpoints **302 → /login**. Followed redirects return login HTML.
Results: `evidence/http-read-checks.json`. No login or write request was made.
The desktop browser was disconnected, and no existing authorised browser session was available.
Consequently **authenticated API response shape and actual rendered data remain unverified**.
The matrix below separates measured database populations from verified source route/rendering code.

## API-to-screen matrix

| Surface | Database facts exist? | Backend query/route | Frontend consumption | Boundary / confidence |
|---|---|---|---|---|
| Data Explorer raw | 3,715 payloads | `/api/data/raw`, detail ID | source/fetcher/method/time filters; JSON or base64 | Code connected; no SQL operations; authenticated runtime unknown |
| Data Explorer canonical/resolved | 10 canonical/5 resolved registry types; 4/3 populated | `/api/data/canonical/{entity}`, `/resolved/{domain}` | Generic string grid, 50/page | All versions, no field types/constraints/joins; null-only fields hidden |
| Sales | 195 current source-days | `/api/sales/daily`, `/latest` | Source table + locally summed chart + gross trace | **F01** chart sums alternatives; resolved latest correct by SQL |
| Home Dashboard | 187 resolved days, product facts | `/api/dashboard/bootstrap`, sales-trend/top-sellers | `DashboardBootstrap` and widget components | Uses resolved sales; ingestion “completeness” is run cleanliness |
| Reservations | 0 raw/canonical/resolved | `/api/reservations/summary`, `/covers` | Today cards, 30-day table | Empty is expected for live data; per-day Covers Trace targets unsupported metric |
| Kitchen | 6,156 lines, partial PDF enrichment, 164 purchase dates | `/api/inventory/summary`, `/lines`, `/graph/...` | 30-day purchases/waste/food-cost, UOM blend, supplier graph | Purchase-spend semantics; fixed date window; no ingredient/inventory model |
| Recipes | Raw links, no recipes/ingredients | No recipe-specific query endpoint found | Hardcoded AwaitingData page | Raw link IDs/cost measures not exposed as recipes |
| Staff/labour | 0 | `/api/labour/summary` | Last-30-day labour cards and seniority-aware view | No received facts; no individual shift/entity drilldown |
| Saved dashboards | 4 dashboards/7 widgets | `/api/dashboards/{id}/render` | Stored metric queries/filters | Reauthorises declared metric; not a saved relational query |
| Ask Goldy's | Existing sales/purchases/product summaries | Fixed tool dispatcher, conversational endpoints | Stream/tool/widget views | 10 compiled tools; no schema-aware novel joins; no AI call made |
| Reconciliation | Source mismatches, 2 overrides, 1 current rule | `/api/reconciliation/...` | Exceptions/source comparison/decision trace | 0 open conflicts ≠ 0 source disagreement; rules choose winners |
| Data health/logs | 21 run failures, 916 invoice flags | connectors/dashboard/query services; no invoice-flag read endpoint found | Status cards/pipeline navigation | Latest-per-source hides web partial behind CSV success; **916 invoice flags not surfaced** |

Primary paths: `frontend/lib/api/live.ts:35–102,150–172`, `types.ts:140–199,201–239,306–334,439–503`,
`use-api-data.ts:15–61`. Controllers delegate to application services with permission checks.

## Confirmed/code-supported discrepancies

### F01: source table is appropriate, source-summed sales chart is not

`SalesReportingService.java:37–43` correctly returns separate source observations for reconciliation;
`latestTradingDay:49–57` returns the resolved metric. `frontend/app/(app)/sales/page.tsx:55–62`
adds all source rows per date. T03 independently shows all 8 dual-source dates would differ from resolved values.
Fix the chart to use resolved trend; keep the per-source table labelled as observations.
Do not compute business revenue by summing alternate evidence sources.

### F13: “COGS / Food cost / Unit cost” are weaker definitions than the labels imply

Kitchen correctly hints “Purchases as a share of gross sales” (`kitchen/page.tsx:54–60`), but metric catalogue
labels purchases “COGS” and purchases/gross “Food cost %” (`MetricCatalog:204–213,322–327`).
All bought categories, including non-food, may participate; category null on every line. No consumption accounting.
`InvoiceLineMetricsServiceImpl.java:40–74` blends **different products** by UOM and multiplies coarse quantity
by unitQuantity when available; pack_size is ignored. This is an approximate UOM-wide spending denominator,
not a comparable ingredient unit price. SQL measured 84.49% missing unitQuantity and 94.62% missing pack size.
`cogsBySupplier:77–94` treats missing WET as zero in aggregate, losing unknown/not-applicable distinction.
Graph supplier total uses ex-tax line spend, but invoice nodes use gross header totals
(`InvoiceGraphServiceImpl:54–78,100–107`): node levels have different tax bases, so add explicit labels/control sums.

### F14: reservation Trace is wired to a backend that rejects it

`reservations/page.tsx:73–77` requests `reservations.covers`; `TrustService.java:127–132` permits only
sales gross/net/GST provenance. This is a **verified code mismatch**, not a witnessed production click
because the reservation tables are empty. Implement authorised reservation lineage or hide unsupported trace.
Reservation table also shows “Agree” whenever `hasConflict=false` (`reservations/page:39–46`), including
single-source results; use “Single source”/trust state rather than implying comparison happened.

### F15: authentication expiry can look like an unreadable response

Safe runtime GETs redirect unauthenticated APIs to login, even with JSON Accept. `fetchApi`
follows redirects and parses the resulting 200 HTML as JSON (`client.ts:28,36–46`), producing
UNPARSEABLE_RESPONSE rather than NOT_PERMITTED/session-expired. This is an access boundary,
not missing database data. Handle API 401/403 explicitly without exposing private content.

### Data Explorer is a record browser, not native schema introspection

`CanonicalBrowseQuery:111–127` stringifies numeric/temporal values and omits null fields;
`generic-table.tsx:9–25` derives columns from current-page keys, sorts page-local strings.
It exposes source IDs/raw pointers as text, not general navigable relationships. Current and history rows are
mixed, with no current filter. All-null account/category fields cannot be discovered as schema columns from a page.
CSV and PDF detail are base64 (`RawRecordBrowseQuery:45–55`), not decoded tabular relational datasets.
The UI has a useful bounded browse foundation; add typed metadata and query plans rather than more entity-specific pages.

## Numeric/time contract assessment

Backend BigDecimal domain values serialize as JSON numbers on typed endpoints; Explorer emits decimal strings.
TypeScript commonly uses number (some sales/top-seller fields accept number|string); TypeScript generics
cast responses without runtime schema validation (`client.ts:39`). The inspected live DTOs broadly agree with
types, but authenticated contract fidelity was not measured. Numeric formatting converts through JavaScript
Number and displays two decimal currency/one decimal percent (`format.ts:15–45`).
No current large-number precision error was demonstrated. For accounting/calculated outputs, specify decimal
string plus scale/unit consistently and perform authoritative arithmetic server-side. Formatting loss is not
stored-fact loss. Percent fields are fractions and frontend multiplies by 100, which is consistent with code.

Kitchen window uses UTC dates (`kitchen/page:20–24`); staff/reservations browser-local dates
(`staff/page:7–11`, `reservations/page:21–30`); CTB revenue uses Melbourne. A shared venue calendar should
replace these definitions for service-level analysis. No measured current timezone discrepancy was claimed.

## Demo/live parity

Demo has only 4 canonical and 2 resolved registry entries versus live 10/5
(`demo.ts:350–360` versus live registry). Fixtures include populated reservation/labour/story data and different
statuses, not evidence those sources exist in production. Demo reports ingestion completeness 92 and includes
agreed/conflicted examples (`demo.ts:401–426`). Demo mode can materially change the perceived data foundation.
Always label mode; report actual current browser preference in the authenticated follow-up.

## F23: recorded invoice anomalies have no audited read surface

Production has **910 PDF_ONLY_LINE** and **6 MISSING_PDF** flags. `InvoiceIngestFlagService.java:25–36`
records them, deduplicating on type/invoice/PDF/stock-code identity. No controller/read facade or Explorer
registration exposes `invoice_ingest_flag`; searches found only write/exists-equivalent paths.
These are SQL-accessible anomaly records, not frontend data-health observations. Add an authorised,
sanitised paginated flag query and link it to relevant invoice/raw evidence.

## Permission observations

T05: all 12 stored permission grants are `ALL / OWNER`. Explorer blanket connectors permission currently
therefore remains owner-only. `InventoryReportingService` authorises inventory cost but reads gross sales;
`LabourReportingService` authorises hours/cost but reads reservations/sales; declared derived metrics are
authorised by their own permission in dashboard/tool paths, not recursively all constituent permissions.
No current non-owner leakage was demonstrated. Before granting new roles or enabling arbitrary query plans,
enforce permissions on every base field/operand and joined dataset, not just the final metric label.
