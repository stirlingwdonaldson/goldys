# Goldy's — Production data architecture audit

Audit: **2026-10-09**, production read-only; source checkout `f6140b8`, deployed release metadata `c3377bb`.
Start with [deployment state](01-deployment-state.md); evidence and bounded probes accompany this report.
Abbreviated code citations resolve through [the precise source index](code-reference-index.md).

## Verdict

**Goldy's has a useful PostgreSQL fact/provenance foundation, but currently functions as a
daily reporting and invoice-purchasing platform, not a general relational business-data platform.**
The main barriers are demonstrable ingestion boundaries, missing structured identities and atomic
facts, and compiled query contracts. They do **not** justify a wholesale rewrite or warehouse today.
Extend PostgreSQL and the existing Spring modular monolith, repair correctness first, then add
authorised metadata-driven exploration alongside the governed metric catalogue.

### What production actually contains

| Dataset | Current structured population | Business coverage |
|---|---:|---|
| CTB daily sales | 187 source-days | 2026-04-02–2026-10-08; 3 absent calendar dates, expected trading calendar unknown |
| Lightspeed daily sales | 8 source-days | 2026-09-26–2026-10-08; retained reports are often empty or very small |
| CTB product sales | 13,185 product-days, 520 normalised names | 2026-07-08–2026-10-08; daily product grain, not sale-line grain |
| CTB invoices | 1,167 headers; 6,156 current lines | 164 purchase dates; includes one 1989 invoice with 12 lines requiring investigation |
| Invoice PDF enrichment | UOM on 4,032 lines; unit quantity on 955; pack size on 331; WET on 178 | Partial structured enrichment; PDF evidence is not linked to enriched fields |
| Reservations / labour / stock counts / wastage / atomic sale items / shifts | **0 rows in each** | No Deputy or OpenTable raw deliveries retained; CTB downstream pull blocked |
| Resolved models | 187 sales days; 13,185 product-days; 164 purchasing days | All inspected values match independent current-canonical recomputation |
| Saved analytical configuration | 4 dashboards, 7 widgets, 4 revisions | Stores predefined metric queries, not new metric formulas or arbitrary datasets |

These are **verified production facts** (P01, I02, I05, T06). “Current” means `superseded_at IS NULL`.

## Most important findings

1. **Sales chart double-counts competing observations (F01, High/P0).**
   `frontend/app/(app)/sales/page.tsx:55–62` sums CTB and Lightspeed instead of selecting the
   resolved value. All **8/8 multi-source dates** differ from the resolved dashboard series (T03).
   The code and live input facts are verified; authenticated pixels were not observed.
2. **CTB's expanded pull is consistently blocked at recipes (F02, High/P0).**
   Five recent PARTIAL runs fail on a **non-JSON response in `CtbClient.getAllRecipes`** (I01).
   Recipes, stock, suppliers, counts, wastage, orders, statements and reference calls after it
   never reach raw persistence. The reason the remote endpoint returned non-JSON is **unknown**;
   no external requests were made to investigate it.
3. **Invoice identity collisions are real (F03, High/P0).**
   Latest CTB AJAX snapshot: 1,174 stable invoice IDs, 1,172 invoice-number keys, with **2 keys
   used by different suppliers** (R01). CSVs also contain two cross-supplier keys across exports.
   Canonical identity collapses this distinction to invoice number; line identity is number + sequence.
4. **Purchasing totals need reconciliation even though their projection arithmetic matches (F04–F05).**
   **97/1,167 invoices (8.31%)** have header-ex-tax versus line-sum differences above one cent;
   freight does not explain those differences. There are **79 repeat-line candidates** and one
   current sequence-tail line beyond its latest parseable CSV length. These are candidates and
   discrepancies, not proof all repeats should be deleted.
5. **Push “SUCCESS” accounts for storage, not completed transformation (F06, High/P0).**
   `IngestionService.ingestPush:111–124` closes runs before Lightspeed/CSV parsing or PDF enrichment.
   All 24 Lightspeed runs say SUCCESS; **8 deliveries have no dated CSV rows**. Successful push
   statuses therefore cannot establish queryable data. Connector completeness would round to **99%**
   despite all five expanded CTB pulls being partial.
6. **Useful cross-domain facts already exist in raw data (F07–F08, P1).**
   30 invoice AJAX pages retain `invoiceId`/`supplierId`; 20 sale-recipe-link pages retain recipe IDs.
   Latest link snapshot: **639 links, 445 distinct recipe IDs**, 474 active flags.
   All **502** latest CTB sales stock codes join to these links. Latest product payloads contain
   **8,704 nonzero food-cost observations**, but stock codes, costs, discounts, tax and recipe names
   are dropped from canonical product sales. These can support additional analysis through replay;
   ingredient quantities are still absent.

## Answers to the owner's twelve questions

1. **Information held:** daily source sales, daily CTB product amounts/quantities, invoice headers/lines,
   partial PDF measurements; source invoice identities and recipe links raw-only. No received Deputy/OpenTable facts.
2. **Raw-only extent:** 3,715 payloads. **3,590 (96.64%) have no canonical-history FK reference**,
   but this is **not a loss percentage**: it includes 2,654 PDF enrichments, duplicate deliveries,
   empty reports and idempotent repeats. Unequivocal dedicated raw-only datasets are **50 AJAX/link pages**.
3. **Partial/incorrect transformations:** CTB pull stops at recipes; CSV drops supplier IDs/taxes/discounts
   and conflates invoice identities; Lightspeed discards available transaction numbers and changes report grain;
   frontend Sales adds alternative-source totals.
4. **Missing atomic structure:** transactions, identifiable sale lines/payments, inventory movements,
   recipe ingredients, supplier/stock/product masters, employees/timesheets and reservation events.
   Some are missing inputs; others are existing raw fields awaiting modelling.
5. **Weak relationships:** invoice-number-only headers/lines, supplier names, normalised product names,
   and calendar-date joins. Only **1 name** overlaps between 520 sale products and 946 purchased-product names.
6. **Hidden useful data:** stable CTB invoice/supplier IDs, 639 recipe links, source-calculated food cost,
   discounts and tax remain accessible only as raw evidence. General screens cannot join or analyse them.
7. **Metric accuracy:** current projections match their canonical inputs; business truth remains qualified by
   source disagreement, purchasing mismatches, missing days, approximate UOM blends and missing cost domains.
   Dashboard resolved sales and Sales-page trend are inconsistent. Purchase spend is labelled “COGS/food cost”
   without consumption or category accounting. See reports 07–09.
8. **General relational analysis:** possible for existing invoice facts and daily summaries through SQL,
   but entity identity and atomic grain are insufficient for the requested broad analysis.
9. **Difficulty of SQL-like exploration:** additive, moderate engineering once identity/metadata are defined.
   A bounded query-plan backend and basic explorer are approximately **3–6 engineer-weeks**;
   production-ready calculated measures/saved queries/permission-aware lineage take additional work.
   Ingestion and source-access work is separate and cannot be estimated from code alone.
10. **Direction:** retain raw evidence and canonical histories; add atomic facts, master identities and links;
    introduce a read-only analytical catalogue/query planner using PostgreSQL; keep governed metrics reusable.
11. **First / second / third:** (1) source-summing, ingestion-stage accounting, CTB failure isolation and
    invoice collision/reconciliation; (2) retain IDs/cost fields and canonicalise existing raw links while
    obtaining verified atomic/recipe/shift inputs; (3) metadata-driven query/metric capability, then UI/AI drilldown.
12. **Unverified:** external source population/completeness; exact cause of recipe response failure;
    authenticated API payloads and rendered pages; full PDF extraction accuracy; full deployed build-to-Git
    correspondence; how much date disagreement is legitimate trading-session policy versus wrong attribution.

## Evidence standard and limits

- **Production fact:** measured database/container/retained payload results, labelled by probe ID.
- **Code behaviour:** source paths and line ranges; tests inspected, no production-connected tests run.
- **Documented intention:** designs/handoffs, not credited as working capability.
- **Inference:** explicitly conditional interpretations (e.g. duplicate candidates, likely undercoverage).
- **Unknown:** expected external record populations and authenticated rendering.

The desktop browser was disconnected; unauthenticated GETs verified health and login redirects only.
No login, ingestion, repair, migrations, restarts, source-system calls or production file edits were performed.
All deliverables live in the isolated audit worktree. [Report 12](12-reproducible-checks.md) contains
query scope, resource limits, evidence mappings and an operator-safe authenticated follow-up.
