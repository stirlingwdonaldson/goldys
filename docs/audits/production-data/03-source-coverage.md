# 03 — Source-to-database coverage

## Verified delivery matrix

All times below UTC. Full machine-readable coverage is `source-coverage.csv`.
`evidence/coverage.csv` C01–C06 records all retained delivery/run groups, not only recent successes.

| Source / dataset | Actual input | Raw payloads | Structured current facts | Latest arrival | Classification |
|---|---|---:|---:|---|---|
| Lightspeed Insights daily sales | Looker webhook JSON containing CSV | 24 | 8 daily source facts | Oct 8 19:09 | Partially canonicalised → resolved → API/UI code accessible |
| Lightspeed products | Product webhook implemented | 0 | 0 | — | Not received |
| Lightspeed Z-report | Raw-only webhook implemented | 0 | 0 | — | Not received |
| CTB revenue + product sales | Authenticated AJAX pulls | 973 shared-identity pages | 187 daily + 13,185 product-days | Oct 8 17:21 | Partially canonicalised; rich fields remain raw |
| CTB invoice metadata | AJAX `SearchInvoices` | 30 | 0 from this path | Oct 8 17:21 | Raw only |
| CTB sale-recipe links | AJAX distinct links search | 20 | 0 | Oct 8 17:21 | Raw only, joinable source IDs |
| CTB invoice CSV | SFTP/manual token drop | 14 | 1,167 headers + 6,156 lines across retained exports | Oct 9 00:24 | Canonicalised with field/identity loss; purchases resolved |
| CTB invoice PDF | SFTP drop | 2,654 | Partial enrichment, no PDF FK | Oct 8 18:30 | Raw evidence plus partially structured augmentation |
| CTB recipes/stocks/suppliers/counts/wastage/orders/statements/reference | Pull code exists, sequence blocked | 0 each | 0 | — | Not received in this ledger |
| Deputy | Token webhook only | 0 | 0 labour/shift | — | Not received; parser absent |
| OpenTable | Manual GuestCenter CSV/drop; UI upload | 0 | 0 reservations | — | Not received; parser available |

“UI accessible” denotes an implemented route/rendering path, not authenticated visual verification.
The only retained `source_system` values are **CTB and LIGHTSPEED**. No extra configured integration with
retained deliveries was found. OpenAI/Sentry are application services, not hospitality fact sources.

## Runtime cadence and outcome

- CTB web connector: 27 retained runs = 16 FAILED, 5 PARTIAL, 6 SUCCESS.
  Latest whole-web SUCCESS **2026-10-06 08:04**, before expanded inventory calls were reached in recent runs.
  Five recent expanded pulls **all PARTIAL**, latest started **Oct 8 17:00**, ended **17:21:51**.
  Cron is 04:00 Australia/Melbourne; the 17:00 UTC run agrees with that schedule.
  Other retained starts indicate manual/retry activity; the ledger does not store trigger kind.
- CTB SFTP poll: enabled, every 15 minutes (code/env verified). It processes files, not a run representing
  the whole poll. PDF/CSV arrivals have individual completed SUCCESS rows. Empty polls leave no ledger heartbeat.
  Latest six CSVs are one-line deliveries; their provenance and business purpose are **unknown**.
  Their arrival alone should not be used as evidence all CTB domains are fresh.
- Lightspeed daily: external scheduled push generally around 19:10/20:15 UTC, including paired duplicate
  bodies and manual-time reports. Source scheduler configuration was not accessed.
  All 24 raw-write runs SUCCESS; 8 reports empty at dated-row grain. Latest reports are aggregate grain.
- Deputy/OpenTable: no runs/bytes observed. A configured OpenTable token is not a configured source export.

## Real raw-only content: what can be recovered without a new source integration

Representative structures were inspected without exporting row values. `payload-shapes.json` covers 40
bounded payloads; `payload-analysis.json` covers all 24 Lightspeed, all 14 CTB CSV and the latest CTB web run
(145 non-PDF payloads, read in memory). Latest web-run counts are **snapshot counts**, not unique all-time entities.

### CTB invoices

Latest six pages contain **1,174 invoice IDs**, **101 supplier IDs**, and 1,172 invoice-number keys.
Fields: `invoiceId`, `invoiceNo`, `supplierId`, `supplierName`, `invoiceDate`, `receivedDate`,
`createdDate`, `businessDepartmentId`, `pdfFile`, `orderReferenceNo`, `totalAmount`, `totalTax`,
`totalDiscount`, `discount`, `freightIncludeGST`, currencies, status, credit-note/return/accounting flags.
Creator/input-person fields exist but were not exported. No invoice-line ingredient records were demonstrated here.

These stable invoice/supplier IDs are available now but are **not** used by canonical invoice identities.
Two invoice-number keys map to distinct supplier/invoice IDs. Five raw number keys have no current canonical header.
Investigate matching export selection/statuses rather than treating 1,167/1,174 as source completeness.

### CTB sale-to-recipe links

Latest four pages: **639 rows**, **601 sale stock codes**, **445 distinct nonzero recipe IDs**,
**474 active flags**, **7 sale stock codes with multiple recipe IDs**.
Fields: `saleItemRecipeId`, `saleItemStockCode`, `saleItemStockDescription`, `recipeId`, `recipeName`,
`recipeFolderName`, `isRecipeActive`.
All **502** stock codes in the latest sales snapshot overlap the link stock codes (R04).
This supports a source-scoped sales→recipe bridge now. Active/inactive/multiple mapping policy is still needed;
it does not supply ingredients or ingredient quantities.

### CTB product-sales payloads

Latest web run has **12,951 product observations**, plus **374 revenue rows** (two departments over 187 dates).
Product fields include stock and parent stock codes, quantity, amount, discounts, tax, unit price,
cost-per-unit, food cost, total food cost, profit, recipe name, portion weight/UOM, site number and grouping IDs.
**8,704** observations have nonzero total food cost; **8,709** nonzero unit cost; **11,707** nonzero discounts;
**11,683** nonzero tax. Costs are real populated source-calculated measures, not merely empty schema fields.
All product `saleDate` values in this run are null; dates are supplied by per-day request context.
Canonical ingestion retains only normalised name, daily quantity/amount and a raw FK.

### Recipes, stock and other expanded endpoints

**No retained payload** from these endpoints can be inspected. Stack frames verify `getAllRecipes`
fails on non-JSON; because calls are sequential, later datasets are not attempted to completion/persisted.
The failed non-JSON response itself is not retained in `raw_record` because `CtbClient` validates before `sink.accept`.
Do not claim recipes/ingredients/stocks are already in the database based on connector comments or research docs.

## Input paths established in code

- Lightspeed `POST /api/ingest/lightspeed`, `/lightspeed-products`, `/lightspeed-zreport`;
  token header/query gating: `api/LightspeedIngestController.java:46–84`.
  Z-report route is missing from permitAll/CSRF-ignore lists (`config/SecurityConfig.java:38–71`):
  token-only server delivery is blocked by the session/CSRF boundary in code (F18).
  No POST was made to verify that route at runtime.
- CTB web AJAX route contracts: `connectors/ctb/CtbClient.java:90–220`; input order:
  `CtbConnector.java:72–89`. CTB CSV token/manual path: `api/CtInvoiceIngestController.java`;
  SFTP path: `ingestion/CtbSftpPull.java:38–81`.
- OpenTable `POST /api/ingest/opentable` plus owner UI upload under connectors;
  `OpenTableCsvIngestService.java:11–15,29–51`.
- Deputy `POST /api/ingest/deputy`, deliberately unparsed: `api/DeputyIngestController.java:18–25,39–52`.
  No Deputy drop-token environment variable was found in effective container settings; unset default rejects all requests.

No HTTP endpoints above were invoked. No automatic OpenTable browser/API pull or Deputy API pull was found.
Direct Lightspeed O-Series transaction API access is **not demonstrated** by the current retained integration.
