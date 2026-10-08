# Integrate Data-Source Research — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the companion research repo (`stirlingwdonaldson/goldys-data-sources`) into the platform — first the docs, then CTB and Lightspeed connector extensions, then (after owner sign-off) a MarketMan connector — without breaking the connector-isolation and raw-ledger invariants.

**Architecture:** Raw-ledger-first. Every new endpoint is pulled through the existing `CtbClient`/`CtbConnector` (session login → form POST → envelope handling → pagination → per-page raw-ledger write). No new canonical types ship in Phase 2; the research is recorded in `docs/connectors/*` and the "Per-Source Ingestion Reality" table so the canonicalization decisions are made later against real data.

**Tech Stack:** Java 25 / Spring Boot 3.5, Gradle 9.5 wrapper, Flyway, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito + AssertJ.

**Spec:** The research repo `docs/recommended-endpoints.md` (primary input) plus `cooking-the-books/`, `marketman/`, `lightspeed/` READMEs and schema files; this repo's `docs/system-context.md`, `docs/prd.md`, `docs/connectors/*`.

## Global Constraints

- Read-only: never call a CTB/MarketMan Add/Save/Update/Delete/Approve/Transfer/MarkAsExported action. Connectors are one-way, source → platform, permanently.
- Every fetch passes through the raw ingestion ledger, byte-faithful, before parsing; `source_system`/`fetch_method`/`content_type`/`fetched_at`/`fetcher_identity` all populated. Failures are first-class ledger states, not missing rows. No source-specific staging tables.
- Each vendor stays behind the connector port (`ingestion/port/SourceConnector`); adding a source never changes code outside its adapter except wiring/registration.
- Schema changes only via new Flyway migrations; never edit existing migrations. (`ddl-auto: validate`.)
- Integration tests against real Postgres (Testcontainers), never H2.
- Credentials only from env vars; names come from the research repo's `.env.example` (`CTB_*`, `MARKETMAN_*`, `LIGHTSPEED_*`, …). Add them empty to this repo's `.env.example`. Never commit real values/cookies/tokens; never log them.
- Do not guess entity-matching tolerances or field-to-role permission seed data (both gated on owner input per PRD).
- Do not copy research `data/raw/*` wholesale into fixtures. Build small, trimmed, anonymised fixtures (fake supplier names/emails/IDs) keeping only parser-relevant fields.
- `./gradlew test spotlessCheck build` must pass at each phase. Frontend checks only if the frontend changes.

## Review Focus

1. **Permission-denied CTB responses** — `{"IsSuccess": false, "Info": "<p><b>You don't have authority…"}` must surface as a distinct `CONNECTOR_PERMISSION_DENIED` failure state, not a parse error or a generic `CONNECTOR_FETCH_FAILED`. (Task 2.2.)
2. **Invoice double-count** — the same CTB invoice number arrives via the CSV upload (`/api/ingest/ctb-invoices`, `FILE_EXPORT`) and via `Invoice/SearchInvoices` (`API`). The matching/dedup is documented and no canonicalisation is wired until the strategy is agreed. (Task 2.3.)
3. **Pagination boundary** — `Search*` endpoints page with `start`/`limit`/`totalCount`; `GetAll*` endpoints return the full array in one call. A pagination bug (off-by-one, or trusting `totalCount` when it's absent) silently drops rows. (Tasks 2.2, 2.3.)
4. **`SearchDistinctSaleItemsForLinkingWithRecipes` totalCount** — the sample reports `totalCount=524` but only 100 rows were captured. Do not assume page size 200 for this endpoint without verifying. (Task 2.2.)
5. **Stocktake/wastage are header-only** — `SearchStocktakes`/`SearchWastageRecords` return header rows (no per-product line items), so they cannot feed the existing `CanonicalStockCount`/`CanonicalWastage` (which require per-product quantity). Do not wire canonicalisation that would silently produce empty quantity rows. (Task 2.3.)

---

## Phase 1 — Documentation (execute now; STOP after)

### Task 1.1: Extend `docs/connectors/source-access.md`

**Files:**
- Modify: `docs/connectors/source-access.md`

**Interfaces:** none (docs).

- [ ] **Step 1: CTB section — add the "add next" endpoints table, params, pagination/envelope, permission-denied HTML, empty integration-config.**

Add, under "Cooking the Books (CTB)", after the existing key-endpoints table:

```markdown
### Endpoints to add next (inventory / purchasing / food cost)

List endpoints page with `start` + `limit` and return `totalCount`; `GetAll*`
endpoints return the full array in one call. Row counts are from the 2026-08-25
sample pull.

| Domain | Endpoint | Params | Returns |
|---|---|---|---|
| Invoices | `Invoice/SearchInvoices` | `start`,`limit` | supplier invoices (784) — a second path to the CSV upload |
| Sale↔recipe links | `Sale/SearchDistinctSaleItemsForLinkingWithRecipes` | `start`,`limit` | POS item → recipe link (`saleItemStockCode`, `recipeId`, `recipeName`, …) |
| Recipes | `RecipeBook/GetAllRecipes` | — | recipe master (486) |
| Stocks | `Stock/SearchStocks` | `start`,`limit` | stock-item master (1,030) |
| Suppliers | `Supplier/GetAllSuppliers` | — | supplier master (87) |
| Stocktakes | `Stocktake/SearchStocktakes` | `start`,`limit` | stocktake headers (18; no line items) |
| Wastage | `WastageRecord/SearchWastageRecords` | `start`,`limit` | wastage headers (58; no line items) |
| Stock orders | `StockOrder/SearchStockOrders` | `start`,`limit` | purchase orders (219) |
| Statements | `ProformaInvoice/SearchStatement` | `start`,`limit` | supplier statements (152) |
| Variance | `Sale/GetVarianceReportData` | date range | CTB's own POS-vs-expected variance |
| Missing revenue | `Report/MissingRevenueReport` | — | dates with no revenue entry |

Reference/master data (small, slow-changing): `BusinessDepartmentActivity/GetAllDepartments`
(2), `BusinessDepartmentActivity/GetAllActivities` (8),
`StockCategory/GetAllStockCategories` (47), `UnitOfMeasurement/GetAllUOMs` (8),
`UnitOfMeasurement/GetAllDistinctSupplierMeasurements` (39),
`MeasurementConversion/GetAllMeasurementConversions` (64),
`RecipeCategory/GetAllRecipeCategories`, `Setting/GetCompanyInformation`.
```

- [ ] **Step 2: CTB section — document the permission-denied HTML behaviour.**

Append to the CTB notes:

```markdown
- **Permission-denied responses.** A failed permission check is still a JSON
  envelope, but `IsSuccess: false` with `Info` (and `message.Info`) carrying the
  HTML string `"<p><b>You don't have authority to perform this action.</b></p>…"`.
  The connector must treat this as a distinct permission failure, not a parse
  error or a generic fetch failure.
- **No accounting export is configured.** Every `*/GetIntegrationConfiguration`
  (Xero, Square, NetSuite, Micropower, Neto, Shoebooks, Adept) returns an empty
  configuration, so CTB is not pushing invoices to any accounting system.
```

- [ ] **Step 3: Lightspeed section — add public API facts, the back-office login flow, other pages, `/report/zreport`.**

Replace/extend the Lightspeed section with the public-API and back-office detail from the research `lightspeed/README.md`:

```markdown
### Public REST API (plan-gated, not currently used)

- Base `https://api.kounta.com/v1/`; docs at `https://apidoc.kounta.com`.
- Auth: Basic `client_id:client_secret` (dev) or OAuth 2.0 (prod). Token at
  `POST https://api.kounta.com/v1/token`.
- Rate limits: 60 req/min (Basic), 180 req/min (OAuth); a 429 carries
  `X-Ratelimit-Reset`.
- Pagination: 25 per page; follow the `X-Next-Page` header (a full URL). List
  endpoints return abridged records; GET by id for the full record.
- **Plan gate:** "Raw API access" is a paid add-on at **+$169/month**. On this
  account `POST /addon/checknewapp` passes but `GET /integration/newapp`
  returns **403 "You don't have access to this page!"**. The account predates
  the Lightspeed rebrand (when the Kounta API was free); ask support
  (`o-series.support@lightspeedhq.com`) to re-enable, or keep the back-office
  pull.

### Back-office login flow (the working path)

1. `GET /login` → parse `csrfTokenValue` from the page.
2. `POST /website/login` with `email`, `password`, `YII_CSRF_TOKEN` → session cookie.
3. `GET /profile` → company list.
4. `POST /profile/changecompany?id=<companyUUID>` → bind the session.

Other back-office pages (server-rendered, data embedded in HTML): `/features`
(add-ons), `/integrations`, `/site/update` (site info), `/product?page=N`
(products), `/tax`, `/pricelist`, `/site/registers`, `/site/printers`,
`/wastage`, `/promotion/scheduled`, `/inventory`, `/site/dashboard`.
Grid pages carry `totalItemCount="…"` on the grid div for pagination.

### Reports (already/partially used)

- `/sale` (Sales Feed CSV) and `/report/salesummarybyproduct` (product CSV) are
  already ingested via the scheduled-report webhook.
- **`/report/zreport`** — end-of-day Z-report; a daily total to check the
  transaction-level Sales Feed against. **Not yet ingested.**
- `/report/salesummary` (daily totals), `/report/refunds` (refund/void detail).
```

- [ ] **Step 4: Add a new MarketMan section.**

Add after the CTB section:

```markdown
## MarketMan

Inventory, purchasing, recipe-costing and menu-engineering system. Buyer portal
at `https://buyer.marketman.com`; Goldy's has been a customer since 2022-08-02.
Integrations: **Kounta** (POS) and **Deputy** (labour) — so some figures are
derived from those systems and are cross-checks, not original records.

**Status: history only.** MarketMan is kept for old (pre-CTB) numbers and only
needs to be pulled once, not ingested on a schedule. A future job is flagged to
determine when the business migrated from MarketMan to CTB.

- **Buyer-portal internal API** (what was captured): mostly
  `POST /api/<Controller>/<Action>` with the logged-in session cookie. Responses
  use the envelope `{IsSuccess, ErrorMessage, ErrorCode, ErrorMessages, …payload}`.
  Login is `POST /api/Auth/BuyerLogin`.
- **Official API v3:** `https://api.marketman.com/v3`. Get a token from
  `POST /buyers/auth/GetToken` with `APIKey` + `APIPassword` (issued by MarketMan
  support) and send it as the `AUTH_TOKEN` header. Not requested yet; the stable
  production path once keys are issued.
- **Key endpoints (high value):** `ItemsPurchases/GetItemsPurchasesData2`
  (purchase catalogue), `Vendors/GetVendorPricesInit2` (supplier prices),
  `Reports/GetPriceChangesReport` (price history), `Vendors/GetVendorListInit`
  (suppliers), `Orders/GetOrderHistoryInit_Not_HQ` + `Orders/GetReceiveOrderInit`
  (orders), `Docs/GetDocsListInit2` + `Docs/ScannedInvoicesInit` (invoices),
  `ItemsProductions/GetItemsProductionsInit` (prep/sub-recipes),
  `ItemsSales/GetMenuItemsWithModifiers` (menu items), `Inventory/GetInventoryValueReport`
  (stock on hand), `Inventory/GetWasteEventsDataInit` / `Reports/GetWasteReportInit`
  (waste), `ActualTheo/GetActualTheoCountsByBuyer` (actual vs theoretical),
  `Cogs/GetCOGSAndGPReporPOSCategoryInitMS` (COGS/GP), `DashBoardNew/GetPOSFeed`
  (sales as MarketMan received them from Kounta).
- **Blocker — recipe ingredient lines:** `GET /api/Items/GetItemDetails` (the
  per-item `Item.SubItems` ingredient list) returns
  `{"IsSuccess": false, "ErrorMessage": "Permissions denied", "ErrorCode": 57}`
  until an Admin grants the **View Recipes** permission (`CanViewRecipes`). This
  is a permission problem, not an auth or code problem.
```

- [ ] **Step 5: Update the Summary table** to add MarketMan (history only).

- [ ] **Step 6: Commit** (`docs/connectors/source-access.md`).

### Task 1.2: Update `docs/system-context.md` "Per-Source Ingestion Reality"

**Files:**
- Modify: `docs/system-context.md`

- [ ] **Step 1: Add a MarketMan (history-only) row; amend the CTB row.**

In the table under "Per-Source Ingestion Reality":

|Source|API access|Working ingestion path|
|---|---|---|
|**MarketMan (history only)**|Buyer-portal internal API (session cookie) + official API v3 (`api.marketman.com/v3`, keys from support) | One-time historical pull of pre-CTB numbers; not a recurring connector. Integrated with Kounta (POS) and Deputy (labour); customer since 2022 |
|**Cooking the Books (CTB)**|No public API | … (keep existing) — note: CTB is inventory/purchasing rather than accounting, adopted ~May 2026 |

- [ ] **Step 2: Add a one-line note** after the table that CTB is really inventory/purchasing (adopted ~May 2026), and that a future job is flagged to determine the MarketMan → CTB migration date.

- [ ] **Step 3: Commit.**

### Task 1.3: Add env var names to `.env.example`

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: Add commented, empty credential slots** for CTB, MarketMan, and Lightspeed (names exactly as in the research repo's `.env.example`):

```bash
# ---- Cooking the Books (CTB) ----
# Session credentials for the internal AJAX endpoints.
# CTB_EMAIL=
# CTB_PASSWORD=
# CTB_BASE_URL=https://web.cookingthebooks.com.au

# ---- MarketMan ----
# Buyer-portal session (email+password, or a browser-session cookie).
# MARKETMAN_EMAIL=
# MARKETMAN_PASSWORD=
# MARKETMAN_COOKIE=
# MARKETMAN_BASE_URL=https://buyer.marketman.com
# Official API v3 (keys from MarketMan support; not yet requested).
# MARKETMAN_API_KEY=
# MARKETMAN_API_PASSWORD=

# ---- Lightspeed O-Series / Kounta ----
# Back-office session + optional public REST API (paid add-on).
# LIGHTSPEED_EMAIL=
# LIGHTSPEED_PASSWORD=
# LIGHTSPEED_CLIENT_ID=
# LIGHTSPEED_CLIENT_SECRET=
```

- [ ] **Step 2: Commit.**

### STOP — Phase 1 gate

Show the doc diff and the PRD-contradiction list (below) before continuing.

**PRD statements these findings contradict (for owner decision):**
1. `docs/prd.md` Problem Statement calls CTB "accounting (Cooking the Books)". CTB is inventory/purchasing/recipes/revenue, not accounting; its `*/GetIntegrationConfiguration` endpoints are all empty (nothing exported anywhere). Accounting is out of scope (Xero dropped by owner), so the PRD's "accounting" role for CTB should just be relabelled to inventory/purchasing.
2. PRD Requirement 4 (and the `new-connector` skill) say CTB ingestion should use the self-serve Custom Invoice Export and that the internal endpoints are "schema-discovery only". The existing `CtbConnector` already ingests CTB via the internal endpoints (`Revenue/SearchRevenues`, `Sale/SearchSaleItemsByDateRange`) for production, and this work extends that further — so the "internal endpoints are discovery-only" stance is already superseded in code.
3. PRD implies a stable set of systems; the research shows a **CTB↔MarketMan migration in progress**. Per owner decision, CTB is the current/ongoing system and MarketMan is history-only (old numbers, pulled once). The remaining unknown is the **migration date** (a flagged future job) — not a contradiction, just an open item.

---

## Phase 2 — Extend the CTB connector (raw-ledger first; no new canonical types)

**Design decision (locked):** all new endpoints write to the raw ledger only, with a per-endpoint `fetcherIdentity`. No parser/canonical entity is added in this phase, because:
- `SearchStocktakes`/`SearchWastageRecords` return **header** rows (no per-product quantity), so the existing `CanonicalStockCount`/`CanonicalWastage` (which require per-product quantity) cannot be fed correctly.
- `SearchInvoices` is a second path for records already canonicalised from the CSV upload — canonicalisation is deliberately left unwired pending the double-count decision (Task 2.3).
- The remaining endpoints (recipes, stocks, suppliers, orders, statements, variance, missing-revenue, reference data) have no existing canonical type, and the PRD forbids guessing their shape.

`fetch()` in `CtbConnector` logs in once, then runs the existing revenue + sale-item pulls followed by the new pulls, each streaming pages to the sink.

### Task 2.1: Add permission-denied detection + new endpoint methods to `CtbClient`

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java`

**Interfaces:**
- Produces: `CtbClient` methods (below) returning the existing `CtbPage(String json, int totalCount)`; a package-private static `boolean isPermissionDenied(String body)`.

New client methods (each does `post(...)`, checks `isSuccess`, throws `CONNECTOR_PERMISSION_DENIED` on "You don't have authority", throws `CONNECTOR_FETCH_FAILED` otherwise, returns `CtbPage`):

- `CtbPage searchInvoices(int start, int limit)` — `POST /Invoice/SearchInvoices` `start`,`limit`.
- `CtbPage searchDistinctSaleItemsForLinking(int start, int limit)` — `POST /Sale/SearchDistinctSaleItemsForLinkingWithRecipes` `start`,`limit`.
- `CtbPage getAllRecipes()` — `POST /RecipeBook/GetAllRecipes` (no paging; `totalCount` = `data.size()`).
- `CtbPage searchStocks(int start, int limit)` — `POST /Stock/SearchStocks` `start`,`limit`.
- `CtbPage getAllSuppliers()` — `POST /Supplier/GetAllSuppliers` (no paging).
- `CtbPage searchStocktakes(int start, int limit)` — `POST /Stocktake/SearchStocktakes`.
- `CtbPage searchWastageRecords(int start, int limit)` — `POST /WastageRecord/SearchWastageRecords`.
- `CtbPage searchStockOrders(int start, int limit)` — `POST /StockOrder/SearchStockOrders`.
- `CtbPage searchStatements(int start, int limit)` — `POST /ProformaInvoice/SearchStatement`.
- `CtbPage getVarianceReportData(String fromDate, String toDate)` — `POST /Sale/GetVarianceReportData`.
- `CtbPage missingRevenueReport()` — `POST /Report/MissingRevenueReport`.
- `CtbPage getAllDepartments()` / `getAllActivities()` / `getAllStockCategories()` / `getAllUoms()` / `getAllSupplierMeasurements()` / `getAllMeasurementConversions()` — reference data (no paging).

A private `pageFrom(String body, JsonNode json)` helper centralises the `isSuccess` → `isPermissionDenied` → throw, and the `totalCount` fallback (`totalCount` else `data.size()`).

- [ ] **Step 1: Write failing tests** in `CtbClientTest` for `isPermissionDenied(String)` and the totalCount fallback logic (extract a package-private static `int totalCount(JsonNode json)` if it helps testability).
- [ ] **Step 2: Run to confirm they fail.**
- [ ] **Step 3: Implement** the helper + methods.
- [ ] **Step 4: Run to confirm they pass.**
- [ ] **Step 5: Commit.**

### Task 2.2: Add the new pulls to `CtbConnector`

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/ctb/CtbConnectorTest.java`

**Interfaces:**
- Consumes: the `CtbClient` methods from Task 2.1.
- Produces: raw-ledger rows with `sourceSystem=CTB`, `fetchMethod=API`, `contentType=application/json`, and per-endpoint `fetcherIdentity`.

`fetcherIdentity` per endpoint (documented in the code + `matching-and-identity.md`):

| Endpoint | fetcherIdentity |
|---|---|
| revenue + sale items | `ctb-revenue` (unchanged) |
| `Invoice/SearchInvoices` | `ctb-invoices-ajax` (distinct from the CSV upload's `ctb-invoices`) |
| `Sale/SearchDistinctSaleItemsForLinkingWithRecipes` | `ctb-sale-recipe-links` |
| `RecipeBook/GetAllRecipes` | `ctb-recipes` |
| `Stock/SearchStocks` | `ctb-stocks` |
| `Supplier/GetAllSuppliers` | `ctb-suppliers` |
| `Stocktake/SearchStocktakes` | `ctb-stocktakes` |
| `WastageRecord/SearchWastageRecords` | `ctb-wastage` |
| `StockOrder/SearchStockOrders` | `ctb-stock-orders` |
| `ProformaInvoice/SearchStatement` | `ctb-statements` |
| `Sale/GetVarianceReportData` | `ctb-variance` |
| `Report/MissingRevenueReport` | `ctb-missing-revenue` |
| reference data (all) | `ctb-reference-data` |

Pagination: `Search*` endpoints loop with `PAGE_SIZE=200` and `MAX_PAGES=250` (reuse the existing constants); `GetAll*`/reference endpoints are a single call.

- [ ] **Step 1: Write failing tests** in `CtbConnectorTest`: for a paginated pull, verify the connector pages until `start + PAGE_SIZE >= total` and writes each page to the sink with the correct `fetcherIdentity`; for a `GetAll` pull, verify a single call + one sink write; verify a `CONNECTOR_PERMISSION_DENIED` from the client propagates (mock client throws).
- [ ] **Step 2: Run to confirm they fail.**
- [ ] **Step 3: Implement** the pull methods (a shared `pullRaw(String fetcherIdentity, IntFunction<CtbPage> pageFn, IngestionSink sink)` helper for `Search*`, and a `pullSingle(...)` for `GetAll*`).
- [ ] **Step 4: Run to confirm they pass.**
- [ ] **Step 5: Commit.**

### Task 2.3: Document the invoice double-source path

**Files:**
- Modify: `docs/connectors/matching-and-identity.md`

- [ ] **Step 1:** Update the `Invoice metadata (canonical_invoice)` section to note there are now two CTB paths (CSV upload `FILE_EXPORT`, `fetcherIdentity=ctb-invoices`; and `Invoice/SearchInvoices` `API`, `fetcherIdentity=ctb-invoices-ajax`), both keyed on `invoice_number`. State that canonicalisation is not (yet) wired from the AJAX path — the two paths must be de-duplicated on `invoice_number` before either feeds `canonical_invoice`, so no silent double-count.
- [ ] **Step 2: Commit.**

### STOP — Phase 2 gate (canonical types)

No new canonical type is proposed in this phase. If the owner later wants canonical entities for recipes/suppliers/stock/orders/statements, each needs its own `V<n>__<domain>.sql` migration + the full `adding-a-domain.md` component checklist — that is a separate, gated piece of work, not part of this phase.

---

## Phase 3 — Lightspeed Z-report

**Flag before building (must be resolved with owner):** the platform has **no back-office scrape connector in code today**. The Lightspeed data path in `connectors/lightspeed/` is entirely webhook-based (Looker scheduled reports → `LightspeedIngestService`/`LightspeedProductIngestService` → CSV parse). `docs/connectors/source-access.md` describes a Playwright scrape as "working today", but no such component exists in the repo, and the backend has no browser-automation dependency. So "follow the existing Lightspeed connector's approach" is ambiguous:

1. If Z-report can be delivered as a scheduled Looker report, extend the existing webhook pattern (new endpoint + parser, like `LightspeedProductIngestService`).
2. If it must be scraped from `/report/zreport`, that requires building a **new** back-office scrape connector (Playwright or equivalent) — a substantially larger piece of work than Phase 3 implies, and one the repo has not started.

**Plan (pending the above decision):**
- **Option A (webhook):** add a `LightspeedZReportCsvParser` + `LightspeedZReportIngestService` + `/api/ingest/lightspeed-zreport` endpoint, mirroring `LightspeedProductIngestService`. No migration (raw-ledger + daily total cross-check projection if a domain needs it).
- **Option B (scrape):** new `LightspeedBackofficeConnector implements SourceConnector` doing the documented `csrfTokenValue → /website/login → /profile/changecompany → /report/zreport → export` flow, using a browser-automation lib. This needs a new dependency decision and its own plan.

---

## Phase 4 — MarketMan (history-only, one-time pull)

**Owner decision (2026-10-08):** MarketMan is kept for old (pre-CTB) numbers
only, and the data only needs to be pulled once. It is not a recurring
connector.

**Planned shape:** a one-time historical extraction of the "High value"
endpoints in `recommended-endpoints.md`, stored in the raw ledger
(byte-faithful, `source_system=MARKETMAN`, `fetch_method=SCRAPE`), using a
buyer-portal session (`MARKETMAN_COOKIE` or `MARKETMAN_EMAIL`/`MARKETMAN_PASSWORD`).
Skip `Items/GetItemDetails` (permission-blocked on "View Recipes"). Read-only
actions only — never Add/Save/Update/Delete/Approve/Transfer/MarkAsExported.

**Deferred:** the connector is not built this pass — the research recorded
endpoint URLs and response envelopes but **not the request bodies**, and the
email/password login fields are unconfirmed, so a correct connector would
require guessing. See `docs/adr-marketman-history.md`. Build it once there's a
live browser session to capture the POST bodies, or MarketMan API v3 keys.

**Future job (flagged):** determine when the business migrated from MarketMan
to CTB, so the historical pull's range is known and old numbers can be spliced
onto CTB's series without overlap.

---

## Out of scope

- Xero (dropped by owner — no accounting source in scope).
- Tenzo.
- Lightspeed internal POS register API.
- Pulling the full ~78k customer list from Lightspeed.

## Future job

- Determine when the business migrated from MarketMan to CTB (bounds the
  MarketMan historical pull and the splice point onto CTB's series).
