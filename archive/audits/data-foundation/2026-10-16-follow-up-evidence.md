# Follow-up evidence: coverage and product identity

16 October 2026. Code inspection on `codex/invoice-read-correctness`, based on repaired foundation
`0f88ebd`. This is the bounded discovery requested in follow-up plan items 2/3. No new production
sample access, ingestion, repair or deployment was performed. Historical audit counts are not
reasserted as current counts.

Implementation follow-up: [product, coverage and observations](2026-10-16-product-coverage-observations.md). The discovery below describes the earlier inspected revision.

## Coverage: evidence-to-qualification matrix

| Evidence | Existing path | Supported explanation | Unsupported inference |
|---|---|---|---|
| Requested dates and per-date resolved values | `semantic/catalog/GrainAggregator`, `SalesMetricExecutor`, `InventoryMetricExecutor` | Which requested calendar dates lack a known resolved value; known subtotal and its scope | That every absent calendar date was an open trading day or had missing revenue |
| Null resolved value versus absent row | `GrainAggregator.sum` distinguishes UNRESOLVED/NOT_RECEIVED for empty buckets | An unresolved stored value differs from an unobserved projection row | A missing projection row proves a source fetch failed |
| Raw receipt and processing stage | `ingestion/IngestionService.recordStage`, stage ledger and connector-run outcomes | This receipt failed parsing/canonicalization; successful receipt time is known | Receipt time is business coverage time; failed-run count measures lost sales |
| Known invoice headers and parent-scoped lines | New invoice details and shared `InvoicePurchaseBreakdown` | Known subtotal, header-only invoice count, unresolved supplier relationships, header/line difference, partial WET | Matching header/line totals prove a complete export; header-only invoice is zero purchases |
| Date-level metric notices and provenance | `PeriodMetricReader`, `MetricProvenance`, saved-widget rendering and existing sales reporting tool | Existing result qualifications reach summary/widget/tool output | Full contributor-level lineage, a retained issued report, or a calibrated missing-amount bound |
| Historical versus prospective inputs | Existing forecast snapshots and venue clock | Historical age alone is not expiry; stored forecast inputs are reproducible | A universal upcoming-booking TTL or no-show grace period has been selected |

A successful import and a fully populated projection are different claims. Current catalogue
`missingPeriods` describes the requested date grid, not an approved expected-trading population.
`SalesMetricExecutor.notices` currently labels all absent/null dates “unresolved”; this is a wording
and evidence-detail improvement candidate, not proof of how much activity is missing.

### Next coverage slice

1. Add an internal requested-date coverage result that separates observed values, stored nulls and
   absent resolved rows. Keep the current requested range and business calendar semantics.
2. Pass reason/count detail through existing metric notices/provenance, period summaries, saved
   widgets and fixed reporting tools. Prefer extending those contracts to building a second health
   dashboard. Keep parsing failures receipt-scoped unless the receipt's evidenced business window
   is known; otherwise show a separate source-processing qualification.
3. Characterize a three-date case: $100 resolved, absent row, stored null. API, saved widget and
   Ask Goldy's must show the same $100 known subtotal with distinct reasons and no zero fill.
   A ratio whose missing operands lack a validated bound remains unavailable with an explanation.
4. Test an explicit zero day and a historical complete input: zero must count as observed; age must
   not expire it. Test a connector failure outside the queried business window: it cannot silently
   change the subtotal or be assigned to the selected period.

Dependencies: a business trading calendar/expected population is needed before claiming complete
business activity. A validated statistical method, confidence convention and calibration evidence
are needed before publishing a five-percentage-point bound. No new threshold is invented here.
No source-wide completeness state or schema migration is selected by this discovery alone.

## Product identity: traced loss and proposed boundary

`connectors/ctb/CtbConnector.pullSaleItemsForDay` normalizes `stockDescription` and merges rows in
`Map<String, ProductSalesInput> byProduct`. It sums quantities and amounts; `mergeStockCode` cannot
retain both distinct codes in one name-keyed row. The raw pointer on the merged input remains the
first contributing raw record, so it does not identify every contributing page.

`canonical/CanonicalProductSalesService.recordAt` locks by product name/date/source. Its logical
UUID and source reference use name/date. `reconciliation/ProductSalesProjector` reconciles and
stores by name/date, and `classification/ResolvedProductCategoryQuery` groups/assigns by name.
Thus the new product hub is not the identity of the current daily revenue facts.

The separate `SourceProductSaleFactInput` retains code/date/source-group identity and financial
observations. It does not retain sold quantity or revenue amount, so it cannot alone reconstruct
name-merged daily sales. `ProductMappingIngest` now preserves manual mappings, but changing that
mapping cannot recover earlier name merges or automatically rebuild resolved totals.

| Representative record | Current effect | Required treatment in a later identity slice |
|---|---|---|
| Codes A/B share a normalized name on one day | Quantity/revenue merge before canonical write; merged code can become unknown | Preserve source-qualified rows before hub association; do not split an old aggregate by guess |
| Code A is renamed | A new name-keyed daily entity can coexist with old name history | Keep source identity independent of display aliases; reconstruct only where original evidence permits |
| Manual source→hub correction | Mapping decision survives refresh; old daily projection still uses names | Apply mapping revisions explicitly during projection, retaining definition/system-time lineage |
| Blank source code | Hub/source financial observation path may skip the item, while name aggregate remains | Preserve a clearly unresolved source item; no fabricated code or fuzzy equivalence |

### Proposed implementation sequence, subject to source-grain evidence

1. Inspect the exact CTB product-sales schema and a separately scoped representative source sample.
   Verify outlet/source scope, group-key meaning, missing-code population and corrected report
   semantics. Invoice CSVs are not evidence for this endpoint. Existing parser/tests establish a
   code contract, not production fitness. No ingestion spec is claimed complete without this step.
2. Define a source-qualified sale observation grain that retains quantity, revenue, units, source
   group, business-date evidence and contributor raw IDs. Decide whether existing canonical facts
   can evolve additively or need a separate source-fact table; choose only after the grain review.
3. Project source identities through explicit reviewed hub mappings. Keep unmapped activity in an
   unresolved bucket and preserve source totals. Do not sum duplicate representations from CTB
   and Lightspeed as independent revenue. Retain existing name-based consumers behind a clearly
   labelled compatibility projection while migrating their contracts.
4. Propose an additive migration, targeted raw replay and comparison report. Preserve old facts and
   manual decisions; do not infer historical aliases or rewrite canonical history in place. Replay
   and production migration each require separate approval. Reports/forecasts must retain their
   original definition and mapping basis when restatement is introduced.

Acceptance checks: two same-named codes remain distinct; renaming does not duplicate a stable
source item; mapping correction changes only the intended projection; unknown mappings remain
visible; source totals conserve quantity/revenue with compatible units; overlapping sources count
once; replay does not create artificial history. Product forecasting remains outside this slice.
