# Handoff — Data-Foundation Query Coverage (updated 2026-10-15)

**Status:** query-coverage breadth gap **CLOSED**. `DatasetRegistry` now registers **11 datasets**
(9 resolved projections + 2 single-source canonical facts), up from 3. The generic relational query
engine is unchanged; only the allowlist and its tests grew. **Verified facts** are stated plainly;
**historical audit findings** are labelled and must be re-checked against current code before acting
on them.

---

## 1. Git state

- **Branch:** `feat/data-foundation`
- **HEAD:** `9c6e0df` — `feat(canonical): backfill legacy invoice_logical_id for the invoice-line join`
- **Milestone commits (oldest → newest):**
  1. `5cd39e7` — Phase 1 data completeness & query-readiness audit (docs + probe harness)
  2. `c5bea04` — Phase 2 entity identity, referential integrity, source-fact preservation
  3. `2db778a` — Phase 3 metadata-driven relational query backend
  4. `49a2319` — Phase 4 query builder UI, saved queries, dashboard reuse
  5. `5c3c7cc` — Phase 5 versioned connector output contract (CTB invoice CSV pilot)
  6. `9a24605` — register the eight eligible resolved projections (Batches A + B)
  7. `9c6e0df` — backfill legacy `invoice_logical_id` so the `invoice→line` join resolves

## 2. Completed architecture

```
source → raw_record (byte-faithful, append-only) → parser (contract-validated)
  → bitemporal canonical facts (Phase 2 identity) → resolved projections
    → DatasetRegistry (allowlist) → QueryCompiler → QueryExecutor (read-only) → QueryResult
    → governed MetricCatalog (compiled metrics, unchanged) → dashboards / Ask Goldy's / screens
    → RelationalQueryApplicationService → UI query builder / saved queries / dashboard "query" widget
```

Delivered: (Phase 1) reproducible audit + probes; (Phase 2) supplier/product/stock identities,
supplier-scoped invoice, content-keyed lines, reviewable `product_mapping`, `source_product_sale_fact`;
(Phase 3) versioned JSON `QueryPlan` + typed compiler + read-only executor + fanout/limit guards;
(Phase 4) Data Explorer "Query" tab, saved queries + revisions, dashboard reuse; (Phase 5)
`SourceDatasetContract` + `ContractValidator` (header drift + JSON-Schema conformance); (coverage)
all eight populated resolved projections registered as queryable datasets.

## 3. Key file locations

- **Dataset registry:** `backend/src/main/java/com/goldys/platform/query/DatasetRegistry.java`
- **Dataset/field/relationship metadata + query-plan DTOs (leaf):** `backend/src/main/java/com/goldys/platform/semantic/query/` (`DatasetDescriptor`, `FieldDescriptor`, `RelationshipDescriptor`, `QueryPlan`, `SelectItem`, `GroupByItem`, `Predicate`/`FilterNode`, `QueryResult`)
- **Compiler/executor/service:** `backend/src/main/java/com/goldys/platform/query/` (`QueryCompiler`, `QueryExecutor`, `RelationalQueryService`, `QueryJdbcConfig`, `SavedQueryService`)
- **REST:** `backend/src/main/java/com/goldys/platform/api/{RelationalQueryController,SavedQueryController}.java` + `application/{RelationalQuery,SavedQuery}ApplicationService.java`
- **Tests:** `backend/src/test/java/com/goldys/platform/query/{RelationalQueryIntegrationTest,ResolvedDatasetQueryIntegrationTest,ResolvedDomainBatchBQueryIntegrationTest,SavedQueryIntegrationTest}.java`, `application/DashboardQueryWidgetRenderTest.java`, `connectors/ctb/CtInvoiceCsvContractTest.java`
- **Frontend:** `frontend/components/query-builder/query-builder.tsx`, `frontend/app/(app)/data/page.tsx`, `frontend/components/dashboards/dashboard-editor.tsx`, `frontend/lib/api/{types,live,demo}.ts`
- **Source contract:** `backend/src/main/resources/connectors/ctb-invoice-csv.contract.json` + `backend/src/main/java/com/goldys/platform/ingestion/contract/`
- **Migrations:** `V74…V79` (additive)

## 4. Currently registered datasets (11)

Resolved projections (9): `resolved_daily_sales`, `resolved_product_sales`,
`resolved_reservation_day`, `resolved_payment_day`, `resolved_sale_item_day`,
`resolved_deleted_sale_day`, `resolved_recipe`, `resolved_recipe_ingredient`,
`resolved_inventory_day`.

Single-source canonical-direct (2): `invoice` (`canonical_invoice`),
`invoice_line` (`canonical_invoice_line`).

One relationship: `invoice → invoice_line` (on `logical_entity_id = invoice_logical_id`).

## 5. Datasets intentionally NOT registered (deferred)

- **`resolved_labour_day`** — populated read model but **0 rows** (no Deputy source delivery).
- **Reviews / classification** — no resolved projection (`canonical_review` + `review_classification`
  only; `ReviewMetricsQuery` reads canonical directly). Raw `comment`/`private_note`/`guest_name`/
  `tags` are privacy-sensitive; needs an explicit permission-boundary decision.
- **Stocktake / stock_count / wastage** — canonical tables empty (DF-03 backfill not run).
- **Raw-only masters** — stocks/suppliers/orders/statements/reference, menu_items/menus, invoice
  AJAX/PDFs, recipes-master, z-report (never register as resolved business truth).
- **Identity hubs (V75)** — `source_product`/`product`/`stock_item`/`product_mapping`/
  `source_product_sale_fact` (0 rows).
- **`recipe → recipe_ingredient` join** — real composition reference but not approved; deferred.

## 6. Remaining limitations

- **Source data missing:** Deputy (labour), Lightspeed products/product-catalog/z-report,
  hour/service-level sales grain.
- **Canonical modelling:** stock/wastage received-but-un-canonicalized (DF-03); guest↔reservation
  linkage unwired (0 linked); cross-source product margin not modelled.
- **Relationships:** only one registered join (`invoice→invoice_line`); no cross-domain joins.
- **Query language:** no calculated expressions; UI exposes AND filters only (backend supports OR/NOT).
- **Lineage:** query results carry grain/readMode/asOf, not per-row provenance.
- **Frontend demo mode:** `frontend/lib/api/demo.ts` still lists only the original three datasets;
  live mode discovers the full registry dynamically.

## 7. Verification commands and known results

- Backend: `cd backend && ./gradlew test spotlessCheck` → **BUILD SUCCESSFUL**, **1,493 tests / 0 failures**.
- Frontend: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build` → **582 tests / 0 failures**, build succeeds.

## 8. Pending migrations, uncommitted changes, unresolved defects

- **Migrations:** V74–V79 are committed and additive. V78/V79 extend the dormant `goldys_ro`
  read-only grant to the eight newly registered resolved tables.
- **Deferred backfills (implemented, not yet enabled in production):** the legacy
  `canonical_invoice_line.invoice_logical_id` re-key is now implemented as a flag-gated runner
  (`app.invoice-logical-id-backfill=true`, idempotent, bitemporal close-and-reinsert); it must be
  enabled in production to make the `invoice→line` join resolve for pre-V74 lines. Still deferred:
  stock/wastage canonicalization (DF-03).
- **Historical audit findings to re-verify against current code (not acted on):** DF-04 (CTB product
  cost/tax/discount — now partially preserved in `source_product_sale_fact`), DF-06 (invoice
  header/line mismatch), DF-07 (valid-time unimplemented), DF-11 (product name↔code ambiguity).
- **Noted metadata caveat:** count columns (`payment_count`, `deleted_count`, reservation counts) are
  `bigint` mapped to the engine's only integer type `FieldType.INTEGER`; filter coercion is 32-bit.
  `resolved_recipe_ingredient.id` is a surrogate UUID regenerated on reprojection (not a stable key).

## 9. Architectural invariants and production safety

- Raw ledger is append-only and byte-faithful (`payload_bytes` + sha256); never mutate.
- Canonical tables are bitemporal (close-and-reinsert, never `UPDATE`).
- Final business consumers read **resolved projections** (or owner-approved single-source canonical-direct), never raw or multi-source canonical.
- `PermissionService.require` is the only authorization point; deny = explicit, never filtered.
- Connectors are one-way; the AI assistant has no generic query-execution tool (unchanged; the AI query-planning ADR remains **Proposed, unapproved**).
- Production is a separate checkout; **never** run `down -v`, read `.env.prod`, or deploy/merge/push from this worktree. Read-only production inspection only via reviewed bounded probes.

## 10. Recommended next slices (ordered by dependency and risk)

1. ~~Backfill legacy `invoice_logical_id`~~ **DONE** — implemented as a flag-gated runner
   (`app.invoice-logical-id-backfill=true`); enable in production to resolve the join for legacy rows.
2. **Canonicalize stock/wastage** (DF-03) once source delivery is confirmed, then register
   `canonical_stocktake`/`stock_count`/`wastage` as canonical-direct datasets.
3. **Replay `ctb-revenue` raw into `source_product_sale_fact`** (DF-04: product cost/tax/discount) —
   unblocks margin analysis; highest remaining financial-data-loss risk.
4. **Backfill product/supplier identity hubs** (DF-11) — stable product identity + cross-domain joins.
5. **Resolve the reviews domain** (projection or owner-approved canonical-direct + PII boundary).
6. **Add calculated expressions + additional relationships** to the query language (Phase-3 deferred).
7. **Source feeds** (Deputy, Lightspeed products/catalog/z-report, hour grain) — external dependencies.
8. **Modelling** (guest↔reservation linkage, recipe↔ingredient join, PDF enrichment DF-10, invoice
   category/account/tax DF-05).

---

*This handoff records verified implementation facts; historical audit finding IDs (DF-xx) are from
the 2026-10-09 audit and must be re-verified against current code before any remediation is planned.*
