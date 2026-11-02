# Separate Lightspeed financial and receipt evidence

Implementation only: this change does not edit Looks 9426/9515/9517, enable a schedule,
send a delivery, deploy a migration or repair production history.

## Import surfaces

Data health has **Import Lightspeed CSV evidence** with three datasets:

- **Merchandise financial lines**: the safe no-payment Insights CSV. This uses the existing
  sale-item parser/writer and financial projections. Payment-joined headers remain rejected.
  Site Numeric ID is retained when supplied; older unknown sites remain explicitly unknown.
- **Receipt-number mapping**: exactly the four mapping headers listed below. Declare the site ID,
  site name and inclusive export date window. Those selections are observations, not proof of coverage.
- **Sales Feed transactions**: the authenticated Back Office CSV, with the same declared selection.
  SiteName must equal the supplied site name. Numeric site identity is operator-supplied context,
  not a field invented from SiteName.

The browser uses session authentication and CSRF through the normal Api client.
All uploads require table-driven connectors WRITE permission. The endpoint contracts are:

| Operation | Endpoint |
| --- | --- |
| Financial CSV multipart file | POST /api/connectors/lightspeed-sale-items/upload |
| Mapping multipart file + siteNumber, siteName, from, to | POST /api/connectors/lightspeed-receipts/mapping/upload |
| Transactions multipart file + same selection | POST /api/connectors/lightspeed-receipts/transactions/upload |
| Qualified group, saleNumber + from + to | GET /api/sale-items/receipt-group |

Receipt-group reads require sale-items.metrics and payments.metrics READ.
Costing additionally requires recipes.cost. No new role grants are guessed.

A future scheduled mapping delivery may use
POST /api/ingest/lightspeed-receipt-mapping with the existing Lightspeed webhook token,
siteNumber and siteName query parameters, and the standard attachment.data CSV envelope.
The endpoint retains the complete original envelope. Its date bounds are derived only from
observed rows, never treated as a verified requested or complete window. Empty webhook reports
fail because they cannot establish a scope; declared-scope empty manual CSVs are retained as
empty observations. Do not configure this delivery until its actual scheduled export is verified.

## Schemas and validation

Mapping headers (order independent; extra measure columns rejected):

- Sales Data Receipt Line ID
- Saleline Payments Sale Number
- Site Numeric ID
- Sales Data Sale Closed Date

Sales Feed requires SaleID, SaleNo, SaleDate, SiteName, LinkedSaleID, Net Amount, Tax Amount,
Tip and Total. Original additional columns are accepted. UTF-8 CSV parsing supports quoted
embedded newlines. Duplicate headers, malformed rows, mixed sites, out-of-window dates,
conflicting same-ID rows within a transaction file and invalid money reject the import
before its canonical batch is published. Net + tax + tip must equal Total exactly.
Blank receipt numbers and unresolved LinkedSaleID targets remain unknown; no type is inferred.
Source-local SaleDate is stored as LocalDateTime; no UTC conversion or verified timezone is asserted.

CSV bytes (or webhook envelope) are stored first. A separate immutable raw observation records
the declared/observed scope and references the payload raw ID. Failed processing records a failed
stage. Successful evidence imports carry RECEIPT_EVIDENCE_UNVERIFIED, so a successful parse is
not displayed as proof of complete financial/source coverage.

## Identity, conflicts and read behavior

Financial rows, mapping observations and transaction observations remain independent.
CanonicalReceiptEvidenceBatch is an immutable bitemporal observation aggregate, identified
by source/kind/raw record. Its valid time is observation time; source-effective ordering is unknown.
The source-specific CSV does not become a staging table. Typed canonical observation lists
are projected into indexed, disposable materialized views after commit and rebuilt on startup.

Exact repeated mappings and transaction facts collapse in resolved views, including across
repeated imports. Raw deliveries and canonical batches still preserve their separate provenance.
Omission never erases an earlier observation. Different numbers or dates for one site/line are
held as MAPPING_CONFLICT. Different facts for a transaction are retained as TRANSACTION_CONFLICT;
there is no automatic latest-import-wins rule or conflict-resolution write API.

Mapping joins require source, site, line ID and observed sale-closed date. The existing financial
source/line identity is preserved: a known site collision is rejected rather than silently merged.
Scope is the application's configured Lightspeed company; no cross-company identity is certified.

Sale-item listing reads resolved_sale_line, including observed mapping receipt numbers.
Missing mappings, unknown sites, legacy numbers and conflicts have explicit row qualifications.
Conflicting rows remain searchable through their observed number claims but have no chosen number.
Historical receipt metadata is carried forward when absent in a financial observation; metadata
provenance remains in its canonical chain, not falsely attributed to the new financial report.

Receipt-number lookup is a group search, not a unique transaction lookup. Transaction observations
are considered across all imported dates to detect later refunds sharing a number.
Multiple site/transaction identities are ambiguous; differing observations for one identity conflict.
Linked transaction IDs are retained even if their targets have not been imported.
Neither number equality nor LinkedSaleID assigns a merchandise line to a SaleID.

No explicit line-to-transaction bridge has been verified. Therefore transaction-specific recipe
cost, profit, margin and completeness are withheld for all receipt groups. Per-line recipe estimates
remain visible with their existing recipe qualifications. Even one observed SaleID does not
establish identity or complete receipt coverage. A future bridge requires a separately tested contract.

## Evidence and measured hit rate

Fixtures: backend/src/test/resources/fixtures/lightspeed/separated-feeds/.
The bundle checksum is 56f89c8d16a01f21f756fa16f57e639aab72812352cd2c59ba779c9a4591f0ee.
The included manifest describes original source exports and minimization.

Real samples cover site 96181, 26 October–1 November 2026, Insights Australia/Sydney:

| Evidence | Source rows | Distinct identities | Parser/observed coverage |
| --- | ---: | ---: | --- |
| Merchandise | 9,405 | 9,405 line IDs | All parsed |
| Mapping | 9,483 | 9,405 line/number pairs | All merchandise IDs observed; 78 repeated pairs |
| Transactions | 5,415 | 5,415 SaleIDs | All parsed; repeated exports agree |

Financial net 142,516.63, tax 14,178.94 and gross 156,695.57 match the Sales Feed.
Three shared receipt-number groups affect 14 mapped lines. These are sampled hit rates,
not a completeness claim. LightspeedReceiptCsvParserTest checks the real parser inputs;
LightspeedReceiptEvidenceIntegrationTest exercises raw retention, resolved mappings and ambiguity
against disposable PostgreSQL 16.

The source grid reports 5,411 while stable exports contain 5,415. No explanation is claimed.
No-payment transactions, source timezone/boundary semantics, source maximum export size,
late source corrections and complete historical coverage remain unverified.

## Activation and recovery gates

Before deployment/activation, review migration V104 and the costing behavior change.
Independently verify the active Look/schedule/destination and scheduled all-results export behavior.
Check source financial totals and observed mapping coverage for each scoped delivery; a matching
population between two exports alone does not prove either source export complete.
Keep missing mappings visible while financial facts continue independently.
The indexed claim, mapping and transaction projections are refreshed concurrently and published
atomically after each evidence import, allowing existing readers to finish on their prior snapshot.
Use bounded manual windows and perform a volume check before large historical or frequent
scheduled imports; full rebuild cost grows with retained observations. No production-scale
performance claim is made.

Transaction imports are manual; no automated Back Office connector is claimed.
The operator must supply the real selected site/date context. Accept or resolve the grid/export
discrepancy explicitly. Cross-window linked targets and unseen transactions remain possible.

Historical repair needs its own approved date scope, safe financial exports, mapping/transaction
coverage checks and bounded correction procedure. Do not replay unsafe payment-expanded
financial payloads. This feature does not run historical recovery.
