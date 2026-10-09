# 08 — Query flexibility, Data Explorer and real business questions

## Governed metrics versus relational exploration

**Code behaviour:** `MetricCatalog.java:90–335` defines **26 compiled metrics** (15 base, 11 derived).
Definitions include source domain, formula strings, permission, dimension/grain allowlists and version `1`.
Actual formulas execute in Java; formula text is descriptive, not interpreted. New metric definitions/formulas
require code/build/deploy. PostgreSQL dashboard JSON stores selected queries, not a dynamic metric registry.

| Capability | Current support |
|---|---|
| Base facts | Sales gross/net/GST; reservations booking/attended/covers/no-show; labour scheduled/actual/cost; purchases/wastage/stock; product quantity/amount |
| Derived | 11 fixed rates/ratios/variance/top-seller metrics |
| Time grains | DAY/WEEK/MONTH, stock closing DAY-only; no hour/service sales grain |
| Dimensions | Product, department, reservation service period where allowlisted |
| Filters | Date range, dimension selection/comparison; no arbitrary value predicates |
| Cross-domain calculations | Fixed ratios such as gross/covers and purchases/gross |
| Group/rank | Predefined dimension queries and ranking service; not arbitrary joins |
| Saved reuse | Dashboard widgets with metric query JSON, filters, revisions/sharing |
| New calculated fields / user metric / saved SQL datasets | Absent |
| Schema/FK discovery to AI | Not in current tool registry |

`MetricQuery.java:6–15` explicitly excludes column/SQL/filter surface. `MetricQueryServiceImpl:34–49`
validates catalogue/grains/dimensions and routes executors. `TimeRange:7–15` orders dates but provides no
hard span cap; a bounded query shape is not a runtime resource budget.
Ask Goldy's exposes **10 fixed tools** (`reporting/ToolId.java:4–14`, `ToolRegistry.java:12–22`), including
get metric, comparisons, rank, top products and dashboard drafts. It cannot invent missing facts or SQL joins.
This is a useful governed semantic layer; preserve it alongside a relational query layer.

## Data Explorer capability audit

| Owner capability | Today | Exact boundary |
|---|---|---|
| Discover datasets | Yes, 10 canonical and 5 resolved registries | `CanonicalBrowseQuery:59–70`, `DataExplorerQueryImpl:78–84` |
| Browse raw | Yes, source/fetcher/method/fetched-time filters, pagination | `/api/data/raw`; CSV/PDF detail base64, not structured grid |
| Discover all fields/types/constraints | No | Descriptor only ID/label/placeholder; fields from returned page values |
| Know null-only fields | No reliable UI discovery | Null columns omitted; all-null category/UOM details can disappear |
| Follow relationships | Gross provenance→raw links; specific invoice graph | No general FK metadata/traversal |
| Join/filter across entities | No | Canonical/resolved endpoints accept entity ID + page/size only |
| Numeric global sort | No guarantee | Generic values are strings; table sorts current page, server sorts recorded/date fields |
| Calculated columns / GROUP BY / aggregation | No | No query-plan interface |
| Save/reuse explored query in dashboard | No | Saved widgets use compiled metrics, not Data Explorer result definitions |
| Complete calculated lineage | No | Current sales-only single-date drilldown; PDF/many-contributor gaps |

Code: `api/DataExplorerController.java:37–84`, `CanonicalBrowseQuery.java:73–128`,
`frontend/app/(app)/data/page.tsx:94–117,260–335`, `components/data-explorer/generic-table.tsx:9–25`.
Canonical registry returns **all versions**, not just current; without current filter users can double count history.
Cap = 200 rows/request, frontend page = 50. Explorer permission is blanket `connectors` READ
(`application/DataExplorerService:18–25,35–62`), not dataset/field permissions.
Production only OWNER has that grant (T05); no current non-owner raw exposure demonstrated.

## Real questions: required grain, live availability and verdict

Previous Friday relative to venue audit date Oct 9 is **2026-10-02** (T08).
Queries below are representative; executed verification probes return counts/deltas, not private row values.

### 1. Individual sales contributing to last Friday revenue

Required: transaction ID, trading-session/date, transaction monetary/refund facts, lines, provenance.
Production Oct 2: **one CTB daily fact and 174 CTB product-days**, no structured sales/lines (T08).
Lightspeed detailed historical CSV retains some Sale Numbers, but Oct 2-arriving report is reconciliation
Oct 3 with only 11 dated rows. No completeness contract links it to all Oct 2 revenue.
**Not answerable completely by current API or structured SQL.** Existing raw partial transactions can be
inspected with the read-only CSV parser, but a new verified atomic source feed/report and modelling are needed.

### 2. Sales by product, hour and service

Product by **day** is directly queryable; hourly/service facts are absent. Product names, quantity/amount/date
exist; no transaction timestamps or service assignments. Safe existing probe:

```sql
SELECT trading_date, count(*) AS product_rows,
       sum(quantity_sold) AS units, sum(amount) AS product_amount
FROM public.canonical_product_sales
WHERE superseded_at IS NULL AND trading_date BETWEEN DATE '2026-10-01' AND DATE '2026-10-08'
GROUP BY trading_date ORDER BY trading_date;
```

Ranking/top-product backend supports period totals, not product/hour/service. Hour requires new input grain.
Do not parse an hour from source date-only Sale Opened Date.

### 3. Product gross margin after ingredient costs

Required: product→recipe→ingredient quantities/base UOM→effective purchase cost; sales price/tax/discount policy.
No ingredient composition or product/stock masters. However latest raw CTB contains **8,704 nonzero food-cost**
observations and 502/502 codes map to recipe links (R03/R04).
**Source-estimated product margin is recoverable with additive raw replay/context**, but verified ingredient
margin is blocked by missing composition/units/effective pricing. Existing API drops the raw cost fields.

### 4. Supplier price change for the same stock item

Existing headers + lines supply supplier name, stock code, invoice date, unit cost. R05 executed:
513 repeated-date supplier/stock pairs, 183 changing-cost candidates. Representative diagnostic SQL:

```sql
WITH candidates AS (
 SELECT h.supplier_name,l.stock_code,
        count(DISTINCT l.invoice_date) AS purchase_dates,
        count(DISTINCT l.unit_cost) AS observed_costs
 FROM public.canonical_invoice_line l JOIN public.canonical_invoice h USING(invoice_number)
 WHERE l.superseded_at IS NULL AND h.superseded_at IS NULL AND l.stock_code IS NOT NULL
 GROUP BY h.supplier_name,l.stock_code
)
SELECT count(*) AS changing_candidates FROM candidates
WHERE purchase_dates>1 AND observed_costs>1;
```

For operator drilldown, add date/UOM/pack/quantity and `lag(unit_cost)` ordered by date and stable line ID.
No current API provides price evolution; raw ID modelling and UOM validity are prerequisites for trustworthy comparisons.

### 5. Recipe ingredients and purchase cost

Raw recipe IDs/names and sales→recipe bridge exist; **no ingredient/recipe-ingredient dataset** retained.
No SQL join can produce ingredient quantities. Recover links now; verify/ingest ingredient-detail contract
separately. `GetAllRecipes` comment promises IDs/names only, not composition.

### 6. Theoretical versus actual consumption

Needs opening/closing counts, receipts/movements, wastage/transfers and sold-recipe ingredients in consistent units.
Current counts/wastage zero rows, movement tables absent. Invoice purchases alone do not measure consumption.
**Blocked** in SQL/API; source coverage and additive movement/recipe facts required.

### 7. Staff hours/shifts associated with highest revenue

Needs staff/timesheet identity and interval boundaries plus atomic timestamped sales/service allocations.
No Deputy raw or labour/shift facts. No current SQL answer. A future daily aggregate join can correlate
days but cannot allocate revenue to staff causally; define interval overlap/service attribution explicitly.

### 8. OpenTable covers versus POS transactions by service

Reservation tables empty; current sales grain daily. No service counts can be computed now.
Future relation is venue/service-time bucket (not inferred guest→sale identity without an actual source link).
Reservations CSV contract must be verified with real exports; timestamped POS input remains necessary.

### 9. Supplier purchasing contribution by category

Supplier purchasing totals are queryable via current number-key join and exposed in Kitchen/graph API.
All **6,156 line category values null**, so category breakdown is blocked. Raw CSV GL codes and raw CTB
businessDepartmentId are possible mapping inputs, not verified category semantics. Safe availability probe:

```sql
SELECT count(*) AS lines,count(*) FILTER(WHERE category IS NOT NULL) AS category_available
FROM public.canonical_invoice_line WHERE superseded_at IS NULL;
```

Result: **6,156 / 0** (I02). Add source-category/master mapping after identity repair.

### 10. Completely new cross-domain metric without Java

**No** through Goldy's UI/API today. An operator can write safe SQL over existing grains, but formulas/executors
are compiled and saved dashboards only choose existing IDs. Add a validated expression/query-plan registry.

### 11. Dashboard value → all contributing records

Gross/net/GST single-date source/raw drilldown exists in code; exact projections independently match.
No universal derived/product/purchase lineage; PDF fields have no PDF FK and raw row positions are absent.
Current answer is **partial**, not end-to-end atomic traceability. Source records can be inspected manually,
not automatically traversed from every dashboard value.

### 12. AI schema discovery and novel authorised joins

**No** in present tool registry. Metric catalogue supports related-metric chaining, not tables/relationships.
Add authorised `describe_dataset`, `describe_relationships`, `validate_query_plan`, bounded `execute_query_plan`
and `explain_lineage` interfaces. Model supplies a typed plan, never privileged SQL credentials.
Saved results must reauthorise constituent fields/joins on every read/render.

## Recommended query-backend shape

Metadata-driven dataset definitions should include native types, null/missing meaning, current/as-of semantics,
source/business grain, keys/cardinality, relationship confidence, permitted fields and measure aggregation rules.
Accept a typed relational plan: dataset IDs, allowlisted fields, validated relationship joins, parameterised
predicates, grouping, safe arithmetic/functions, order/limit and optional as-of. Compile parameterised SQL
with source-scoped keys and fanout checks. Support dates as predicates, not Java domain-method proliferation.

Enforce operand-level permissions, protected employee/guest columns, read-only DB role, time/row/byte/work-memory
budgets, join depth, concurrency, cancellation and safe cost checks. No arbitrary functions/system tables,
data-modifying CTEs or LLM-controlled SQL. A public metadata interface must hide unauthorised fields/relations.
See reports 10–11 for additive phases and estimates.
