# 10 — Recommended architecture and additive migration strategy

## Decision recommendation

**Evolve the existing PostgreSQL + Spring modular monolith.** Add source-scoped entities/atomic facts and a
metadata-driven, authorised relational query layer beside the existing governed metric layer.
Current volumes and successful current-fact SQL recomputations do not justify a separate warehouse now.
This is an audit recommendation, not an approved implementation ADR or deployment plan.

Evidence: 34 tables, largest structured heaps ~4.3 MB, raw storage ~394 MiB, 31,684 canonical versions;
independent current resolved arithmetic passes. Problems are missing grain/identity/transformations/query contracts,
not measured PostgreSQL capacity. No representative concurrent analytical load test was performed.

## Option comparison

| Option | Benefits | Costs/limitations | Audit verdict |
|---|---|---|---|
| 1. Continue extending existing domain repositories only | Least immediate change; preserves modular ownership | Every new query becomes Java; weak generic relationships persist | Necessary for correctness/domain facts, insufficient alone for owner's flexible analysis |
| 2. Add relational metadata/query + declarative semantic definitions on PostgreSQL | SQL-like filters/joins/calculations, reuse across UI/AI/dashboard, existing ACID/evidence retained | Must model identities/cardinality/security/budgets, maintain typed planner/catalogue | **Recommended additive direction** |
| 3. Add analytical read store/warehouse | Isolates heavy scans, broad historical workloads, multi-venue scaling | Another pipeline, lag/reconciliation/security/cost, does not recover absent facts | Conditional future option, not justified by current runtime evidence |

Consider read replicas/materialised analytical views before a new engine if measured workloads need isolation.
Escalate only after profiling shows concurrent plans harm OLTP/ingestion or history growth demands partitioning/
columnar storage. Benchmark thresholds should be agreed from actual service SLOs, not invented here.

## P0 — Existing correctness and reliability

| Change | Limitation/evidence | Exact components | Additive/migration/backfill | Integrity/security | Effort / dependencies / capability |
|---|---|---|---|---|---|
| Resolve Sales trend consistently | 8/8 source-summed dates differ (T03/F01) | sales page, live API resolved trend / SalesReportingService | Small code change, no migration | Same metric path; keep source observations separate | 0.5–1.5 days; existing resolved route; consistent revenue |
| Dataset-stage run accounting | Push closes before parse; 99% run proxy (F06/F10) | ingestion run facade, callers, SFTP/pull, health | New dataset_attempt/stage/outcome/manifest/lineage tables; optional historical classification | Honest empty/partial/rejection; never retroactively claim historical parse success | 1–2 weeks; agreed grain controls; queryable-stage health/replay |
| Isolate CTB datasets and diagnose recipe response | Five non-JSON getAllRecipes failures (I01/F02) | CtbClient/CtbConnector/schedule | Per-dataset attempts; operator-authorised external contract check; no schema repair guessed | Sanitised HTTP status/type/action; no credential HTML leakage | 2–5 engineering days + external access uncertainty; downstream extraction can proceed |
| Repair invoice key foundation + reconciliation | 2 source supplier collisions; 97 header differences; sequence tail (F03–F05) | canonical invoice/line services/parser, InventoryProjector, graph | Source/supplier identity links and stable invoice/line keys; shadow reconstruction/backfill then compare | Preserve histories, quarantine ambiguous matches, do not delete repeat candidates blindly | 1–2 weeks; source mapping and stage ledger; trusted purchasing/entity joins |
| Domain freshness / total completeness | CTB CSV masks web PARTIAL; partial totals lack flags (F09/F21) | ConnectorHealthService/TrustService/GrainAggregator/reporting DTOs | Dataset window state; DTO status/coverage additions, possibly no fact backfill | Not-received ≠ zero; derived operand permission/completeness checks | 3–6 days after stage definitions; trustworthy freshness and ratios |

## P1 — Atomic facts, source identities and relationships

### Additive data shape

- Keep `raw_record` as immutable received evidence. Add source dataset/window/page metadata and parser-output
  records, not an overwrite of raw bytes or a forced JSON normalisation of PDFs.
- Introduce **source-scoped entity mapping**: venue/source/outlet, supplier/source-supplier, product/source-product,
  stock item/supplier SKU, invoice/source-invoice and versioned matching decisions with confidence.
- Give invoice lines stable invoice FK and source-line identity when available; if absent, preserve source row
  position and use a documented matching policy with ambiguity review, not pretend ordinals are immutable IDs.
- Add **facts at received grain**: CTB product-day by stock source ID with tax/discount/source-cost measures;
  sale-recipe-link facts with active/effective state; supplier/invoice source metadata.
- Add transaction/line/payment/refund facts only after real source inputs demonstrate them. The present
  aggregate latest Lightspeed reports cannot be split into invented transactions.
- Recipe ingredient composition and stock movement/count/wastage require actual verified dataset contracts;
  simply unblocking GetAllRecipes, documented as IDs/names, is insufficient for theoretical consumption.
- Employee/timesheet/reservation identities are ready-to-model domains after real delivery is obtained.
  Minimise guest/staff PII and gate wage/individual-detail access independently from aggregate sales.

| Slice | Evidence / tables/components | Migration/replay | Effort | New capability |
|---|---|---|---|---|
| Supplier/invoice IDs | 1,174 invoice IDs / 101 supplier IDs raw; canonical keys weak | New masters/maps/links, map AJAX↔CSV by supplier+number+date/status; ambiguity-aware backfill | 1–2 weeks, overlaps P0 invoice work | Source-stable purchases, reliable supplier history |
| CTB product stock/tax/discount/cost | 12,951 latest rows, 8,704 real nonzero costs dropped | New atomic source-product-day fact/columns + source/window; replay when date context recovered confidently | 4–8 days + context verification | Source-estimated product margin/discount/tax analysis |
| Sales→recipe links | 639 raw links, 502/502 code overlap | Add source-product/recipe refs + effective link facts; latest-snapshot replay; do not infer ingredient detail | 3–5 days | Product/recipe traversal, activity/conflict visibility |
| Full atomic POS sales | Current latest aggregate-only | New transaction/line/payment schema; new authorised feed/report setup; backfill only received real granularity | 2–4 weeks engineering, external contract unknown | Hour/service sales and atomic revenue drilldown |
| Recipes/ingredients/stock | No received composition/movements | Verify source schemas, canonicalise masters/composition/movements/counts; reconcile units | 2–4 weeks after real contracts, external delay unknown | Ingredient costing/theoretical vs actual consumption |
| Deputy timesheets | No deliveries; no parser/token found | Approved connector configuration + schema observation; employee/time/cost facts; replay if new retained samples | 1–2 weeks after contract | Staff-hour/service correlations; authorised payroll detail |
| OpenTable reservations | Parser exists, no delivery | Verify CSV headers/status/time policy; import window manifests/event identities; no fabricated guest data | 2–5 days after source access | Covers/service analysis; still requires POS time grain |
| Field/many-contributor lineage | PDF IDs absent; first-page aggregate pointer | Add observation_contributor and field_evidence, immutable parse outputs/extractor version | 4–8 days after stage ledger | Dashboard→all contributing evidence, reproducible enrichment |

Estimates are engineer working time ranges, not delivery promises; overlapping slices should not be summed blindly.
No migrations or backfills were executed during this audit.

## P2 — Authorised metadata-driven query and semantic layer

### Metadata contract

Define datasets for **current business facts** first, history/raw separate. Each descriptor contains:
dataset/version, fields/types/units/null semantics, grain, keys, source/venue scope, time semantics,
relationship/cardinality/fanout rules, permissions/classification, lineage and completeness status.
Do not derive authorisation from `information_schema` alone or expose passwords/conversation bodies as queryable tables.
Registry can begin with reviewed definitions for invoice lines/headers, product-days and resolved daily facts;
schema reflection validates their physical bindings. This differs from adding another Java method per question.

### Typed relational query plan

Plan: dataset, selected fields, allowlisted relationship joins, parameterised predicates, grouped dimensions,
aggregations, safe calculated expressions, ordering/row cap, requested current/as-of view, dataset version.
Compile to parameterised SELECT using curated bindings. Validate native type/units, join cardinality and
double-count risk before execution. A join from invoice headers to lines must not repeat header totals as a new sum.
Store expression/query definitions as versioned data with tests/control examples and dependency lineage.

Security/resource controls must be part of the backend:

- Authenticate user, authorise **every base field, dataset, join and operand**. Hide unauthorised metadata.
- Dedicated DB read-only role with only approved analytical views/tables, not the current production superuser.
- Read-only transaction, fixed statement/lock timeouts, row/response-byte/span limits, controlled work memory,
  concurrency/queue budget and cancellation. Cost checks use reviewed planner metadata/EXPLAIN, never unbounded ANALYZE.
- No user/LLM-controlled table/function names outside catalogue; no arbitrary SQL, system catalog exploration,
  volatile functions, data-modifying CTEs, unrestricted subqueries or credential-bearing raw retrieval.
- Structured audit record of query definition/version, access scope, input windows, lineage/control sums and outcome;
  do not log private result rows or model prompts containing guest/wage data.
- Saved queries/dashboard results are reauthorised at execution/render; permission changes invalidate cached grants.
  Aggregate fields require policy against small-group disclosure where staff/guest privacy is implicated.

### Semantic coexistence

Keep current governed metrics as named definitions with business contracts. Initially execute them using existing
Java while the planner supports exploratory datasets. Then selectively make formulas declarative over vetted
facts and share one unit/missingness/provenance implementation. User-defined metrics can be private/draft until
validated; governed metrics should not silently change when a user edits a formula.
Metric version identifiers should be meaningful, not every definition fixed at version `1`.

Core backend + metadata MVP **2–4 weeks**; basic relational UI another **1–2 weeks**; declarative metrics,
saved-query reuse and comprehensive lineage/access regression work **2–4 additional weeks** depending on scope.
These estimates assume P0 identity contracts and do not solve source input gaps.

## P3 — User-facing capabilities

Evolve Data Explorer into typed columns, global filters/current/as-of selection, authorised relationship navigation,
safe join/group/calculation builder and explicit quality/lineage. Save reusable dataset/query definitions and reference
them in dashboards. Add drilldowns with tax/unit/grain/status labels; make partial results and permission denial distinct.

AI tools should discover authorised dataset metadata, propose typed plans, validate then execute under the same
permissions/budgets as the UI. Model is a planner, not a database principal. It receives no unrestricted credentials.
Private raw PDFs/staff/guest data should not be automatically sent to an external model for generic exploration.

## Migration/rollout strategy for a separate implementation agent

1. Establish dataset controls and immutable outcomes; address chart/input-key correctness.
2. Add new entities/links/facts in parallel with legacy tables. Keep existing APIs operational with shadow checks.
3. Recover existing raw dataset transformations with confidence/window/identity metadata; explicitly mark unverifiable
   historical date mappings and unknown original parser results. Do not “backfill” missing source detail by estimation.
4. Recompute shadow purchasing/sales/product outputs and compare per-source/window control sums, collisions, lineage
   and business definitions. Quarantine unresolved identity/measurement cases.
5. Expose a minimal read-only query catalogue, then saved queries/calculations, then UI/AI reuse.
6. Make validated new facts authoritative incrementally; keep old reporting compatibility views/adapters and versioned
   definitions until consumers migrate. Planned changes require separate approval, implementation tests and release verification.

See `proposed-data-architecture.mmd` and report 11 for ordering. No wholesale architecture replacement is necessary.
