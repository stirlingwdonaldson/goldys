# 05 — Field preservation, aggregation and loss

`field-preservation.csv` is the field-level matrix. Raw bytes remain available; “dropped” below means
absent from structured/semantic data, not necessarily erased from the ledger.

## Lightspeed: changing source grain, then daily aggregation

**Production:** all 24 retained envelopes were inspected structurally and by aggregate-only CSV parsing.
There are **8 empty dated reports**. Earlier reports include sale number/type/order type and **date-only**
opened/reconciliation boundary fields. One Oct 6 manual-time report contains **372 dated rows with 372
distinct sale numbers for reconciliation date Oct 5**. Other distinct detailed report bodies contain only
11, 12, 11, 21 or 1 sale rows. Paired arrivals repeat content.
The latest Oct 6–8 reports have **one dated aggregate row each, no Sale Number**, plus a CSV totals row.

`LightspeedInsightsCsvParser.java:35–52` keeps only reconciliation date, total-inc-tax, tax and adjustments;
blank-date totals rows are skipped. `LightspeedIngestService.java:77–86,104–110` sums by reconciliation date,
adds adjustment amounts/tax and computes net = gross − tax. Negative totals exist in retained detailed reports;
they contribute to aggregates, but refund/event identity and reason disappear.

| Source field | Raw availability | Structured outcome |
|---|---|---|
| Sale Number | Available on detailed historical reports | Dropped; no sale entity |
| Sale Type / Order Type | Available in detailed reports | Dropped; no filter or refund classification |
| Sale Opened Date | Available in detailed reports, date-only | Dropped; no timestamp/hour can be reconstructed from it |
| Reconciliation Date / Start / End Date | Available, sometimes different | Only reconciliation date survives |
| Total Inc Tax / Tax / adjustments | Available and populated | Summed by date; four decimal storage |
| Revenue / cost / sales-count fields | Available headers in reports | Dropped |
| Product IDs / sale lines / payment method / hour timestamp | Not demonstrated in received reports | Missing inputs, not proven parser loss |

Transaction-level **partial historical reconstruction** from retained CSV is possible for detailed bodies.
Complete current transaction→line→product reconstruction is **not** possible from latest aggregate reports.
Do not infer Lightspeed API access or source-wide transaction completeness from a Sale Number header.
Report reconciliation date can differ from sale-opened date; venue trading-session policy is needed before
asserting the calendar mapping is wrong. Example: Oct 2 arrival carries reconciliation date Oct 3.

### Lightspeed product date attribution (F11)

`LightspeedProductIngestService.java:52–71` assigns `LocalDate.now()` (server date) and groups by normalised
product name. Parser ignores possible report-window metadata and missing Quantity/Sale Amount becomes zero
(`LightspeedProductCsvParser:35–47,82–85`).
**Production:** zero product deliveries and zero Lightspeed canonical product rows. Therefore misattribution
is a **verified code risk, not an observed production product-date defect**. Require explicit report business
date/window before enabling historical imports or delayed delivery. No existing product report contents can verify it.

## CTB daily and product facts

Revenue source: two department rows/day, 374 latest source rows → 187 canonical days. This is legitimate
aggregation; R02 independently reproduces all gross/GST/net values **exactly**, no rounding delta at this grain.
Department ID/name, outlet, revenue ID, discount and created-date distinctions are not retained in daily facts.
The source field `kitchenRevenueTotal` is used as net; current sums satisfy gross = net + GST (I02).
Its business definition still needs a verified source contract before interpreting it as universally comparable net.

CTB products: daily request context → stock-description normalisation → name/day sum.
`CtbSaleItemParser.java:29–34` reads stockCode but `CtbConnector.java:149–161` drops it from canonical input.
Source `costPerUnit`, `foodCost`, `totalFoodCost`, `profit`, `discountedAmount`, `taxAmount`, `unitPrice`,
parent code, recipe name and portion measures never enter the intermediate model.
Latest raw includes 8,704 populated nonzero food costs and all 502 stock codes link to raw recipe mapping.
These are useful source-calculated estimates; their methodology, units and temporal costing basis are **unknown**.
They could be exposed as explicitly source-estimated margin, not promoted to audited ingredient margin.

Date loss is especially important: product `saleDate` is null; request window is not stored. The canonical day
exists but raw replay needs missing context. One raw FK anchors aggregate output; multi-page/all-contributor
lineage is not generally represented, although current latest revenue days do not span pages (R02).

## CTB invoice CSV

Received full exports have 35 headers (payload-analysis), including stable **SupplierCode**, outlet codes,
supplier/stock GL codes, invoice tax flags, freight-ex-tax, document type, source created/updated dates,
line tax/discount and other fields. Parser reads a selected subset and groups solely by invoice number.

Preserved: invoice number, supplier name, invoice/due date, PO, PDF filename (first), header total/ex-tax/GST/freight,
stock code/description, coarse quantity, ex-tax unit cost, ex-tax line amount.
Not structured: supplier code, outlet identity, source GL/category hints, line tax/discount/tax flag,
freight-ex-tax/tax distinctions, document/credit-note semantics, source update timestamps.
`CtInvoiceCsvIngestService.java:62–67,84–90` explicitly writes account/tax/freight-GST/category/enrichment nulls.
Category is labelled PDF-only in comments but **no audited enrichment path writes category**; it is null on all lines.

### Quantity fallback: measured, not assumed

`CtInvoiceCsvIngestService.java:123–134` takes the first whitespace token parseable as BigDecimal; otherwise 1.
All **14 retained CSVs**, including 8 full exports, were inspected for this rule:
**0 unparseable eligible quantities**. One blank-description row appears in each of three repeated exports;
these are deliberately excluded. Thus fallback exists but **no measured production usage** in retained inputs.
`quantity=1` (2,546 current lines) is not proof of fallback. No field records whether fallback was used.

79 current lines have `abs(quantity*unit_cost-line_total)>0.01` (1.28%); discounts, tax basis, rounding or coarse
quantity may explain some. Missing LineUnitCostExTax defaults to zero; six recent minimal CSVs omit that header,
while their canonical outcome is unaccounted. Current unit-cost zero count = 22, not all attributable to fallback.
No exact-unit conversion can rely on first numeric token alone: package sizes/UOM are separate.

## PDF enrichment

PDFs can yield quantity, UOM, unit quantity, pack size and WET in `PdfExtractedLine`; the enrichment service
passes only UOM/unitQuantity/packSize/WET (`InvoicePdfEnrichmentService:51–60`). CSV quantity/cost/total are
kept authoritative; extracted quantity does not correct them. A category or supplier identity is not passed.
Some PDF fields are retained as new canonical versions; **4,032 UOMs** demonstrate real enrichment.
However successor rows keep the CSV `raw_record_id` (`CanonicalInvoiceLineEnrichment:23–25,71–90`), so there
is no direct field-level PDF ID, extractor version/confidence or immutable parse-result record.
2,654 PDF raw records therefore have **zero canonical FK references**, despite successful enrichment.
This is missing traceability, not proof no PDF information was used.

Flags: 910 PDF_ONLY_LINE across 540 invoice numbers, 6 MISSING_PDF across 6 keys. These are deduplicated
anomaly identities (type/invoice/PDF/stock code), not an exhaustive count of unique unmatched source lines.
AMBIGUOUS is returned but not flagged by the caller.
No quantified extraction accuracy or full CSV/PDF amount reconciliation is available.

## Deputy / OpenTable

Deputy: 0 raw; 0 labour/shift. Controller stores arbitrary bytes but has no parser. Scheduled/actual hours,
cost, employee references and time boundaries are **not verified received fields**. Existing labour columns
are model capacity only.
OpenTable: 0 raw; 0 canonical. Contract parses ID/date/time/party size/status/table/source/guest name;
normalises known statuses and retains unknown uppercase statuses (`OpenTableCsvParser:43–74,103–115`).
Reservation projector counts unknown status only as booking (`ReservationProjector:139–155`).
These are **code behaviours**, not evidence of actual OpenTable export compatibility.
No source event/history, cancellation timing or service link is demonstrated.
