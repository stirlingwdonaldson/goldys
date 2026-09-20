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

## Lightspeed (O-Series / Kounta)

Base: `https://my.kounta.com`

- **Login** — `GET /login` → fill `input[name=email]`, `input[name=password]`,
  submit "Log in" (`button[type=submit]`); success lands on `/profile`.
- **Sales Feed** (raw sales, transaction-level) — `GET /sale`. Export via
  `#btnReportExport` → downloads `sales_feed_YYYYMMDD_export.csv`.
  Columns: `SaleID, SaleNo, SaleDate, SiteName, TerminalName, CustomerName,
  Operator, Notes, LinkedSaleID, Net Amount, Tax Amount, Tip, Total`.
  One row per transaction; `Total = Net + Tax + Tip` (Tip is 0 in current data).
- **Other reports** (Reports menu):
  - `/report/salesummary` — Sales Summary (daily totals).
  - `/report/salesummarybyproduct` — "Sales By" (line items: product, qty, amount).
  - `/report/zreport` — "Reconciliation" (Z-report / end-of-day).
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

Notes:

- CTB is owned by Quantaco (an analytics competitor) — no public/partner API is
  expected to be forthcoming; treat the internal AJAX endpoints as the ingestion
  path, behind the connector port so it can be swapped for a CSV/manual fallback.
- `Sale/SearchSaleItemsByDateRange` is the overlap with Lightspeed: CTB ingests
  POS sales (via its Kounta/Lightspeed integration) as sale items that can be
  matched to Lightspeed transactions — and it already produces a variance
  report, a useful cross-check for this platform's own reconciliation.

## Security

- Credentials live in env vars (`LIGHTSPEED_EMAIL`, `LIGHTSPEED_PASSWORD`,
  `CTB_EMAIL`, `CTB_PASSWORD`) and are never committed.
- Access is read-only; no connector writes to a source system.
- Session cookies/state are ephemeral and never logged.
