# Source Access & Ingestion Paths

How each Phase 1 source is reached and what data it exposes. This is the
discovery reference for the connector adapters — it records login flows,
endpoints, and export mechanics, **never credentials**. Credentials are supplied
as environment variables and are gitignored (see `.env.example`). All access is
read-only: the platform never writes back to a source system (per
`system-context.md`).

## Summary

| Source | Access | Auth | Ingestion |
|---|---|---|---|
| Lightspeed (O-Series / Kounta) | Browser scrape of the back office (no usable public API for the sales feed) | Email + password form | Deterministic Playwright: login → Sales Feed → export CSV |
| Cooking the Books (CTB) | Authenticated internal AJAX endpoints (ASP.NET MVC + ExtJS; no public API) | `POST /Account/Login` → session cookie | Session-authenticated POSTs to controller actions |
| MarketMan (history only) | Buyer-portal internal API + official API v3 (`api.marketman.com/v3`) | Session cookie (buyer portal) or `AUTH_TOKEN` header (v3) | One-time historical pull of pre-CTB numbers; not a recurring connector |

## Lightspeed (O-Series / Kounta)

Base: `https://my.kounta.com`

### Public REST API (plan-gated, not currently used)

- Base `https://api.kounta.com/v1/`; docs at `https://apidoc.kounta.com`.
- Auth: Basic `client_id:client_secret` (dev) or OAuth 2.0 (prod). Token at
  `POST https://api.kounta.com/v1/token`.
- Rate limits: 60 req/min (Basic), 180 req/min (OAuth); a 429 carries
  `X-Ratelimit-Reset`.
- Pagination: 25 per page; follow the `X-Next-Page` header (a full URL). List
  endpoints return abridged records; GET by id for the full record.
- **Plan gate:** "Raw API access" is a paid add-on at **+$169/month**. On this
  account `POST /addon/checknewapp` passes, but `GET /integration/newapp`
  returns **403 "You don't have access to this page!"**. The account predates
  the Lightspeed rebrand (when the Kounta API was free); ask support
  (`o-series.support@lightspeedhq.com`) to re-enable it, or keep the back-office
  pull below.

### Back-office session (the working path)

1. `GET /login`, then parse `csrfTokenValue` from the page.
2. `POST /website/login` with `email`, `password` and `YII_CSRF_TOKEN` → session cookie.
3. `GET /profile` → company list.
4. `POST /profile/changecompany?id=<companyUUID>` → bind the session to the company.

Other server-rendered pages (data embedded in the HTML): `/features` (add-ons),
`/integrations`, `/site/update` (site info), `/product?page=N` (products),
`/tax`, `/pricelist`, `/site/registers`, `/site/printers`, `/wastage`,
`/promotion/scheduled`, `/inventory`, `/site/dashboard`. Grid pages carry
`totalItemCount="…"` on the grid div for pagination.

- **Sales Feed** (raw sales, transaction-level) — `GET /sale`. Export via
  `#btnReportExport` → downloads `sales_feed_YYYYMMDD_export.csv`.
  Columns: `SaleID, SaleNo, SaleDate, SiteName, TerminalName, CustomerName,
  Operator, Notes, LinkedSaleID, Net Amount, Tax Amount, Tip, Total`.
  One row per transaction; `Total = Net + Tax + Tip` (Tip is 0 in current data).
- **Reports** (Reports menu):
  - `/report/salesummary` — Sales Summary (daily totals).
  - `/report/salesummarybyproduct` — "Sales By" (line items: product, qty, amount).
  - `/report/zreport` — "Reconciliation" (Z-report / end-of-day). Raw webhook
    ingest is wired (`POST /api/ingest/lightspeed-zreport`,
    `fetcher_identity=lightspeed-zreport`, delivered via a scheduled Looker
    report); the daily-total parser is deferred until the report's CSV columns
    are confirmed.
  - `/report/salescompare`, `/report/taxes`, `/report/refunds`, etc.
- **Date scoping** — the Sales Feed "Filter" (`#btnSearch`) sets the range; the
  default export is the current period.

Notes:

- The UI table shows a `Payments Surcharge` column the CSV export does not
  include; the CSV has `Notes`/`LinkedSaleID` (refund/void linkage) instead.
- CSV cells are quoted, and `Notes` can contain embedded newlines — a CSV
  parser must handle quoted multi-line fields.
- CTB has a Kounta/Lightspeed integration (see below), so POS sales may already
  flow into CTB — a cross-check target, not the source of truth.

## Cooking the Books (CTB)

Base: `https://web.cookingthebooks.com.au`

ASP.NET MVC + ExtJS single-page app. No documented REST API; every data call is
an MVC controller/action returning JSON.

- **Login** — `GET /Default/Login` (fields `#login-email`, `#login-password`,
  `#btn-login`), or programmatically `POST /Account/Login` with form fields
  `userEmail` + `userPassword`. Success sets an ASP.NET session cookie (no CSRF
  token observed). Subsequent calls need only the cookie plus headers
  `X-Requested-With: XMLHttpRequest`, `Referer: <base>/Default/Home2`,
  `Accept: application/json`.
- **Envelope** — most actions return
  `{"data": <payload>, "message": {"IsSuccess": bool, "Info": str}}`
  (some return `data` at top level, or a bare array for tree data). Requests
  are `application/x-www-form-urlencoded` POST (GET 302s/404s).
- **Pagination** — list endpoints take `start` + `limit` and return `totalCount`.

Key endpoints (from a full crawl: 1386 actions across 127 controllers):

| Domain | Endpoint | Returns |
|---|---|---|
| Daily revenue | `Revenue/SearchRevenues` (`start`,`limit`) | daily revenue entries ("SALES" summary) |
| Daily revenue detail | `Revenue/GetRevenueDetailByDateRange` (`startDate`,`endDate`) | revenue by day |
| POS sale items | `Sale/SearchSaleItemsByDateRange` (`fromDate`,`toDate`,`searchType`) | ingested POS line items (rolling 90d default) |
| Variance | `Sale/GetVarianceReportData` / `ExportVarianceReportToExcel` | POS-vs-expected variance (CTB already computes it) |
| Sales issues | `Sale/DetectSalesIssues` | detected sales problems |
| Invoices (purchases) | `Invoice/SearchInvoices` (`start`,`limit`) | supplier invoices (COGS) |
| Missing revenue | `Report/MissingRevenueReport` | dates with no revenue entry |

Reference/master data also pulled (105 endpoints): suppliers, stock, recipes,
stock orders, stocktakes, wastage, business departments, currencies, UoMs, etc.

### Endpoints to add next (inventory / purchasing / food cost)

List endpoints page with `start` + `limit` and return `totalCount`; `GetAll*`
endpoints return the full array in one call. Row counts are from the 2026-08-25
sample pull (verified live 2026-10-08; counts have grown since — see the note
below).

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
| Statements | `ProformaInvoice/SearchStatement` | `startDate`,`endDate` | supplier statements (152) — **not paged**, see note |

Reference/master data (small, slow-changing): `BusinessDepartmentActivity/GetAllDepartments`
(2), `BusinessDepartmentActivity/GetAllActivities` (8),
`StockCategory/GetAllStockCategories` (47), `UnitOfMeasurement/GetAllUOMs` (8),
`UnitOfMeasurement/GetAllDistinctSupplierMeasurements` (39),
`MeasurementConversion/GetAllMeasurementConversions` (64),
`RecipeCategory/GetAllRecipeCategories`, `Setting/GetCompanyInformation`.

Notes:

- **Verified live 2026-10-08** (counts have grown ~2 months since the Aug sample:
  invoices 784→1,132, recipes 486→661, stocks 1,030→1,175, suppliers 87→109,
  stocktakes 18→27, wastage 58→112, stock orders 219→452, statements 152→251).
- `ProformaInvoice/SearchStatement` ignores `start`/`limit` and returns the full
  date-range list in one response — it must not be paged.
- `Sale/GetVarianceReportData` returns a **server error** (`IsSuccess: false`,
  server-error HTML) for both a date range and an empty body — not ingested;
  params are unconfirmed and the endpoint may be broken for this account.
- `Report/MissingRevenueReport` returns a **report token** (a hex id in `Info`),
  not a list of missing dates — the actual missing-dates export is
  `Revenue/GenerateCSVMissingRevenueDates` (a CSV, not JSON). Not ingested as-is.

- CTB is owned by Quantaco (an analytics competitor) — no public/partner API is
  expected to be forthcoming; treat the internal AJAX endpoints as the ingestion
  path, behind the connector port so it can be swapped for a CSV/manual fallback.
- `Sale/SearchSaleItemsByDateRange` is the overlap with Lightspeed: CTB ingests
  POS sales (via its Kounta/Lightspeed integration) as sale items that can be
  matched to Lightspeed transactions — and it already produces a variance
  report, a useful cross-check for this platform's own reconciliation.
- **Permission-denied responses.** A failed permission check is still a JSON
  envelope, but with `IsSuccess: false` and `Info` (and `message.Info`) carrying
  the HTML string `"<p><b>You don't have authority to perform this action.</b></p>…"`.
  The connector must treat this as a distinct permission failure, not a parse
  error or a generic fetch failure.
- **No accounting export is configured.** Every `*/GetIntegrationConfiguration`
  (Xero, Square, NetSuite, Micropower, Neto, Shoebooks, Adept) returns an empty
  configuration, so CTB is not pushing invoices to any accounting system.

## MarketMan

Inventory, purchasing, recipe-costing and menu-engineering system. Buyer portal
at `https://buyer.marketman.com`; Goldy's has been a customer since 2022-08-02.
Integrations: **Kounta** (POS) and **Deputy** (labour) — so some figures are
derived from those systems and are cross-checks, not original records.

**Status: history only.** MarketMan is kept for old (pre-CTB) numbers and only
needs to be pulled once, not ingested on a schedule. It still showed live
orders and sales at the 2026-09-03 capture, so a future job is flagged to
determine when the business migrated from MarketMan to CTB (CTB was adopted
~May 2026; MarketMan goes back to 2022).

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
  `ItemsSales/GetMenuItemsWithModifiers` (menu items),
  `Inventory/GetInventoryValueReport` (stock on hand),
  `Inventory/GetWasteEventsDataInit` / `Reports/GetWasteReportInit` (waste),
  `ActualTheo/GetActualTheoCountsByBuyer` (actual vs theoretical),
  `Cogs/GetCOGSAndGPReporPOSCategoryInitMS` (COGS/GP), `DashBoardNew/GetPOSFeed`
  (sales as MarketMan received them from Kounta).
- **Blocker — recipe ingredient lines:** `GET /api/Items/GetItemDetails` (the
  per-item `Item.SubItems` ingredient list) returns
  `{"IsSuccess": false, "ErrorMessage": "Permissions denied", "ErrorCode": 57}`
  until an Admin grants the **View Recipes** permission (`CanViewRecipes`). This
  is a permission problem, not an auth or code problem.

## Security

- Credentials live in env vars (`LIGHTSPEED_EMAIL`, `LIGHTSPEED_PASSWORD`,
  `CTB_EMAIL`, `CTB_PASSWORD`, `MARKETMAN_EMAIL`/`MARKETMAN_PASSWORD` or
  `MARKETMAN_COOKIE`, `MARKETMAN_API_KEY`/`MARKETMAN_API_PASSWORD`) and are never
  committed.
- Access is read-only; no connector writes to a source system.
- Session cookies/state are ephemeral and never logged.
