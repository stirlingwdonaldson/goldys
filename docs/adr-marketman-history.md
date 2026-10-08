# ADR — MarketMan is history-only (one-time pull, pre-CTB numbers)

Status: accepted (decision recorded; connector deferred pending a live session or API keys)

## Context

MarketMan (buyer portal `https://buyer.marketman.com`) is an inventory/purchasing/recipe-costing
system Goldy's has used since 2022-08-02. Cooking the Books (CTB) was adopted around May 2026 and
is now the ongoing inventory/purchasing system. The two overlap heavily (inventory, purchasing,
recipes, waste), so keeping both as live sources would double-report the same business facts.

The research repo (`stirlingwdonaldson/goldys-data-sources`) captured MarketMan's buyer-portal
internal API: 78 distinct endpoints across 327 calls (2026-09-03), plus a compiled Excel export. It
still showed live orders and sales at capture time, so the cut-over from MarketMan to CTB is not a
clean single date.

## Decision

1. **MarketMan is history-only.** It supplies old (pre-CTB) numbers only, and the data only needs to
   be pulled **once** — it is not a recurring connector.
2. **CTB is the ongoing system** for inventory/purchasing/recipes; MarketMan's ongoing overlap is
   intentionally not ingested.
3. **A future job is flagged** (in `docs/system-context.md`) to determine when the business migrated
   from MarketMan to CTB, so the one-time MarketMan pull's range is bounded and its series can be
   spliced onto CTB's without overlap.

## Access mechanics (buyer-portal internal API)

- **Auth:** logged-in browser session. Two ways:
  - `MARKETMAN_COOKIE` — a raw `Cookie:` header copied from devtools (preferred; avoids passwords),
    or
  - `MARKETMAN_EMAIL`/`MARKETMAN_PASSWORD` → `POST /api/Auth/BuyerLogin` (field names
    `userEmail`/`userPassword` are a **best guess**, not confirmed — prefer the cookie).
- **Requests:** `POST https://buyer.marketman.com/api/<Controller>/<Action>` (mostly; some GETs).
- **Envelope:** `{IsSuccess, ErrorMessage, ErrorCode, ErrorMessages, ...payload}`.
- **High-value endpoints** (per `docs/recommended-endpoints.md`): `ItemsPurchases/GetItemsPurchasesData2`,
  `Vendors/GetVendorPricesInit2`, `Reports/GetPriceChangesReport`, `Vendors/GetVendorListInit`,
  `Orders/GetOrderHistoryInit_Not_HQ`, `Orders/GetReceiveOrderInit`, `Docs/GetDocsListInit2`,
  `Docs/ScannedInvoicesInit`, `ItemsProductions/GetItemsProductionsInit`,
  `ItemsSales/GetMenuItemsWithModifiers`, `Inventory/GetInventoryValueReport`,
  `Inventory/GetInvCountsEasy`, `Inventory/GetWasteEventsDataInit`, `Reports/GetWasteReportInit`,
  `ActualTheo/GetActualTheoCountsByBuyer`, `Cogs/GetCOGSAndGPReporPOSCategoryInitMS`,
  `Reports/GetMenuProfitabilityInit`, `DashBoardNew/GetPOSFeed`, plus reference data
  (`Inventory/PostGetStorages`, `Groups/PostGetGroups`, `WeeklyOrder/GetWeeklyOrders`,
  `Buyers/GetBuyerDetailInit`).

## Blockers that gate building the connector

1. **Request bodies are unrecorded.** The research captured response envelopes and endpoint URLs,
   but not the POST bodies; the bulk-capture script was not kept. The connector cannot be built
   correctly by guessing empty bodies. Needs a live browser session to capture the POST bodies, or
   the official API v3 (`api.marketman.com/v3`, keys from MarketMan support) which has documented
   request/response contracts.
2. **Email/password login is unconfirmed** (see above) — the cookie path is the only confirmed auth.
3. **`Items/GetItemDetails` is permission-blocked** — per-item ingredient lines return
   `{"IsSuccess": false, "ErrorMessage": "Permissions denied", "ErrorCode": 57}` until an Admin
   grants the **View Recipes** permission. Recipe *headers* are available; itemised ingredients are
   not, until that grant.

## Consequences

- No MarketMan connector is built in this pass; nothing is ingested from MarketMan until the blockers
  above are cleared. All MarketMan findings are recorded in `docs/connectors/source-access.md` and
  the research repo remains the reference for endpoint/response shapes.
- When built, the one-time pull is a `SourceConnector` behind the port, raw-ledger-first
  (`source_system=MARKETMAN`, `fetch_method=SCRAPE`), read-only, starting with the high-value
  endpoints and skipping `Items/GetItemDetails`.
- The migration-date job is the prerequisite for knowing how far back the pull must reach.
