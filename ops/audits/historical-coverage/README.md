# Historical coverage and recovery plan

Checked 26 November 2026, Australia/Melbourne. **Production historical backfill is not complete.**
The retained 2025 Lightspeed backfill contains 369,513 sale-line IDs missing from reporting.
CTB has a separate gap in detailed product facts for 8–24 July 2026. The missing Lightspeed
population is retained; the earlier conclusion that only an incomplete source export existed
was incorrect.

This audit performed no production writes, replays, deployments, source pulls or configuration
changes. All database sessions forced `default_transaction_read_only=on`. The main aggregate
snapshot used a repeatable-read transaction at 15:53 Melbourne. Subsequent source inspections
used separate read-only sessions; these files are evidence, not an executable approval manifest.

## Evidence and scope

- [Aggregate snapshot](baseline-2026-11-26.json): all canonical/resolved table counts and date
  spans, raw-source inventory, latest runs, historical failures and sale-line projection parity.
  Unversioned recipe child-table counts include children of superseded snapshots.
- [Original Lightspeed backfills](lightspeed-2026-11-26.json): all 14 distinct original
  sale-item payload hashes captured before 11 October. Repeated identical deliveries are
  inspected once. Missing-key counts overlap between reports and must not be added.
- [Annual sales comparison](sales-years-2026-11-26.json): payment, merchandise and headline
  presence, with exact decimal amount strings. Payments and merchandise are different measures;
  equality alone would not establish completeness or correct sales quantities.
- [CTB dated reports](ctb-2026-11-26.json), [missing detail dates](ctb-missing-detail-2026-11-26.json),
  [dated candidates](ctb-candidates-2026-11-26.json), and
  [all candidates and real parser results](ctb-all-candidates-2026-11-26.json).
  [Separate dated-sample parser results](ctb-parser-results-2026-11-26.json) validate later copies
  of the six July 19–24 reports. These are alternate evidence copies, not extra sales.

Observed date spans do not establish complete source populations. Missing dates are not
automatically missing trading days. Raw-only datasets are not automatically failed imports.
This audit did not independently enumerate every record in the external source systems.

## Findings by source

| Source/domain | Observed production state | Recovery implication |
| --- | --- | --- |
| Lightspeed sale lines | 1,529,341 current lines, 23 November 2020–25 November 2026. Zero missing, orphaned or differing resolved line IDs/quantities/amounts/dates. | Rebuilding projections cannot restore absent canonical facts. |
| Lightspeed 2025 | Original backfill has 410,558 rows, 409,565 IDs and 369,513 IDs absent from current canonical state. Current 2025 lines total $185,640.24 against $6,440,790.89 in payments. | Retained recovery candidate, held for payment-join validation; not approved for blind replay. |
| Other original Lightspeed years | No missing IDs in the successfully inspected original 2020–2024 and 2026 exports. One original payload is malformed JSON; another has no sale rows. All inspected line exports have unsafe joins. | Presence is demonstrated only for parseable retained populations, not all external history or correct measures. 31 December 2023 has payments but no merchandise lines. |
| CTB product sales | 32,165 current product/day totals from 8 July–25 November; detailed source facts begin 25 July. | 2,401 product/day totals across 8–24 July have no corresponding day population in `source_product_sale_fact`. Existing revenue/product totals must not be added again. |
| CTB later product gaps | All six dates in the 2 November recovery plan now have detailed facts. Seven free modifiers explain the observed source-row count differences. | Do not replay these as missing financial sales or invent product codes for modifiers. |
| CTB invoices | 1,569 current headers and 7,892 current lines. Both `5772205` and `INV102334` are present. | Earlier missing-header target is no longer missing. PDF enrichment/identity/amount flags require individual evidence review. |
| CTB stock and recipes | 34 stocktake headers, 14,417 stock-count lines, 933 wastage lines, 674 recipe definitions. Stocktake dates start 25 May; wastage starts 10 September. | These are observed inventories, not an external all-history reconciliation. Do not infer missing snapshots from a date span. |
| OpenTable | 25,444 reservations dated 5 December 2020–9 May 2027; 1,250 reviews dated 5 March 2021–12 November 2026. Latest source capture 12 November. | Future reservations do not prove freshness. Verify source session and full-history capture, then catch up changes since the last run. |
| Workforce | No canonical labour entries or shifts; no retained Tanda/Deputy raw source population. | Not recoverable from the current ledger. Tanda account capture and validated mappings are prerequisites. |
| MarketMan | No retained MarketMan raw population. | Requires source access and the pre-CTB cutover boundary; replay cannot create absent history. |

### What happened to Lightspeed 2025

Raw IDs `154d8cef-a26d-4a86-acd5-832b913d73b2` and
`4005cbc2-6464-43d5-a979-b58782c454c3` have identical retained bytes, SHA-256
`c3bda72763ffa8638d1608309a685e008e376cc20f75a28abc394e2ccf7cb182`, length 235,903,051.
Both have a failed parsing stage from 10 October and no linked canonical versions.
The export's observed dates end 30 December; a separate 31 December export is published.

The [23 October recovery plan](../../recovery/2026-10-23/README.md) already identified this
population. Its 376,533 missing unambiguous keys plus 11 held missing keys, less the 7,031
subsequent five-day repair inserts, equals today's 369,513 missing keys. This is consistent
with an incomplete recovery, not evidence that a fully published 2025 dataset was later erased.
Only 19 superseded 2025 sale-line versions remain; they do not contain the missing population.
The database alone cannot rule out an earlier destructive operation outside retained history.

The current parser rejects the export's `Saleline Payments`/`Payment Data` joins. The
diagnostic comparison finds 13 IDs with differing quantity/amount/tax observations. More
importantly, unique IDs do not prevent measures already multiplied by joins. The older
insert-only recovery plan predates the stricter source-grain checks and must not bypass them.

## Recovery sequence and acceptance

### 1. Validate Lightspeed merchandise independently

Obtain or locate a no-payment-join financial export for the 2025 date window, using the
existing [separated-feed contract](../../../docs/operations/lightspeed-separated-feeds.md).
The source must identify site, line ID, closed date, product, quantity, amount and tax.
Receipt mapping and payments remain separate evidence. Preserve the retained original bytes.

Compare candidate IDs and measures to both the large retained export and current canonical
versions. The preview must enumerate missing inserts, unchanged rows, conflicts, source-only
and canonical-only IDs by date, and independently checked quantities/financial totals. Do
not divide by payment count or select an arbitrary duplicate. Preserve the existing five-day
repairs. Retain unresolved rows as explicit holds. The 369,513 count is a baseline, not a
required insertion count after source correction.

Before production publication, rehearse with the normal canonical ingest facade against an
isolated PostgreSQL 16 database, verify idempotence, raw provenance, version preservation and
resolved equality. Production acceptance requires a reviewed bounded application operation,
post-write comparison against the independent source, and rerunning the parma question.
No direct canonical SQL insertion, whole-ledger replay or extra Spring process against
production is authorized by this document.

### 2. Recover the bounded CTB detail population

The 18 payloads in [all candidates](ctb-all-candidates-2026-11-26.json) represent 17 days and
2,401 rows. The current real Java parser/batch accepted **2,401/2,401 rows** in isolated JVMs.
Dates for older undated raw records come from their existing canonical provenance, never
capture time. Later explicitly dated copies establish the same six July 19–24 days.

Six dated single-page candidates separately passed **768/768 rows** and match existing
product totals of $92,260.50. Recovering their source facts adds detail, not revenue. The
18 July payloads contain 200 and 17 rows of an advertised 217: per-page parser success is
not sufficient evidence of one complete report. Validate their common capture/run membership,
nonoverlapping row identities and aggregate control totals before including that date.

Use a bounded selection of reviewed raw IDs/hashes and complete report groups through
`SourceProductSaleFactIngest`; preserve existing product totals and later source facts.
The legacy backfill runner traverses the whole ledger and has no bounded selection/dry-run
contract. Do not enable it merely to repair these dates. A bounded preview/recovery interface
and isolated idempotence/identity tests are still required before a production write proposal.
Acceptance: expected detailed IDs/amounts/quantities present, no duplicate product/day revenue,
unchanged existing totals, and qualified costs/tax visible to the relevant semantic queries.

### 3. Close the other source-specific gaps

- Invoice flags currently include 106 extraction failures, 987 PDF-only-line flags, 19
  amount-basis flags, six missing PDFs, three unparseable PDFs and one ambiguous PDF.
  These are flag counts, not distinct missing invoices or approved insert counts. Revalidate
  document links and current source facts using the
  [invoice procedure](../../../docs/operations/ingestion-recovery-2026-11-25.md).
- OpenTable: establish an authenticated assisted session, check full-history pagination and
  requested windows, and catch up since 12 November. Do not infer an expired session from
  the age of the last capture alone.
- Tanda: obtain the account capture and mapping evidence in
  [Tanda setup](../../../docs/connectors/tanda.md). Its 366-day capture limit is not all-history
  coverage. Earlier workforce history needs a verified bounded source extraction contract.
- MarketMan: verify access, historical request bodies and the cutover boundary in the
  [history-only decision](../../../docs/decisions/marketman-history-only.md). Do not double-count
  the overlap with CTB.
- Stocktakes, wastage, invoices, reservations and reviews require source-provided population
  counts or exports for the intended historical windows before they can be certified complete.

### 4. Make agent qualifications reflect current evidence

Two unconditional instructions need replacement together:

1. `GetMerchandiseHistoryTool.result` appends the 12 November historical audit warning to
   every result, regardless of requested date or subsequent recovery.
2. `agent-worker/src/main.ts` instructs the model to follow quantities with an
   "incomplete-period caveat" even when no current completeness determination is available.

The existing coverage query supplies only first/last date, observed-day count and row count.
Those fields cannot establish source completeness. A missing dish on a date is not evidence
that the whole date is missing; a record on every date is not evidence that all dishes exist.

The replacement should report three distinct states for the requested dataset/site/window:
**verified complete** (source-population and measure checks passed), **known incomplete**
(specific current unresolved gaps), and **not verified** (insufficient completeness evidence).
Attach the source/manifest and verification time, invalidate stale assessments after changed
inputs, and expose them through the existing semantic/permission boundary. Report observed
quantities in all applicable states. Never turn a failed query into an empty success, unknown
into zero, an old audit into a current gap, or current uncertainty into a claim of completeness.

Acceptance tests need complete/incomplete/unverified windows, a repaired previously incomplete
window, modifiers versus dishes, pagination, denied access, and invalidation after new evidence.
No agent prompt or tool behavior has been changed or deployed by this audit.

## Reproduction and validation

From the repository root, explicitly choose the container:

```bash
python3 ops/audits/historical-coverage/audit.py --container goldys-prod-postgres-1
python3 ops/audits/historical-coverage/lightspeed_backfills.py --container goldys-prod-postgres-1
python3 -m unittest discover -s ops/audits/historical-coverage -p 'test_*.py' -v
```

The scripts do not read environment files or credentials and emit only aggregate diagnostics.
The Lightspeed inspection streams immutable CSVs and never marks a report safe to replay.
Unreadable/unsupported reports remain unknown. A snapshot does not replace external control
totals or source-specific identity validation. CTB payloads were additionally passed in memory
to the existing [database-free parser probe](../../recovery/2026-10-23/RetainedPayloadProbe.java)
compiled against current backend classes; no raw payload files were saved in this checkout.

The backend compiled successfully for that probe. All five Python safety/diagnostic tests passed.
Report links and skill-discovery paths were checked. The plan preserves the SRS distinction
between missing, unknown and zero, raw-ledger provenance, source-grain validation and production
approval boundaries. Ripwire found no pre-existing quality regressions or incompatible callers
for the new inspection function. It did flag new evidence-file verbosity, test-method discovery
and the 69-line inspection function; these are not claimed as a warning-free result.
No backend/frontend application code or schema changed, so their full test suites were not run.
External source completeness, recovery writes, deployment and post-recovery acceptance remain
outstanding. There is no production-ready blanket backfill or "all data present" certification.
