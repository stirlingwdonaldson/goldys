# Network Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable Obsidian-style force-directed network graph and ship two uses of it: a
platform map on Data health (sources → domains → metrics → dashboards, with freshness "blast
radius") and a Menu affinity page (products linked by how often they sell together).

**Architecture:** A generic `NetworkGraph` type, pure helpers and a `NetworkCanvas`/`NetworkExplorer`
pair in `frontend/components/network/`, fed by one pure builder per use. The platform map is
composed in `application` from existing catalogue, config, connector health and saved dashboards.
Affinity adds three disposable resolved projections (V42) built by a `reconciliation` projector from
`canonical_sale_item`, read through a `semantic` `ProductAffinityQuery`.

**Tech Stack:** Java 25 + Spring Boot 3.5 (JUnit 5, AssertJ, Mockito, ArchUnit, Testcontainers
Postgres 16), Next.js 15 + React 19 + TypeScript (Bun, Vitest + Testing Library),
`react-force-graph-2d` (new), `@xyflow/react` types for `FlowTone` reuse.

**Spec:** `docs/superpowers/specs/2026-10-11-network-graph-design.md`

## Global Constraints

- Business consumers (`api`, `application` read paths) never read `canonical`; only
  `ProductAffinityProjector` (in `reconciliation`) does. `ArchitectureBoundariesTest` must pass and
  is extended, not worked around.
- No new permission resources and no permission seed migration. Platform map: `connectors` READ.
  Affinity: `sale-items.metrics` READ. Denials throw `AccessDeniedException`; never filter a
  response down to what the caller may see.
- Every frontend `Api` method gets a live and a demo implementation (`frontend-api-method` skill).
- `react-force-graph-2d` is pinned to an exact version (no `^`), loaded only through
  `next/dynamic` with `ssr: false`.
- The canvas is `aria-hidden`; every network view also renders `NetworkList` from the same data.
- Affinity constants live once: `MAX_BASKET_PRODUCTS = 25` (projector),
  `AffinityOptions.DEFAULTS` (query). Clamp ranges are enforced in `AffinityOptions`'s compact
  constructor.
- Schema changes go through Flyway (`add-migration` skill). Never edit an applied migration.
- Phase B (affinity) does not start until Task 0's gate passes.

## Review Focus

The input classes most likely to break this, by risk, and the task that pins each with a test:

1. **Huge baskets (table tabs, functions)** — must be excluded and counted, not paired. → Task 10.
2. **Refund / void lines (quantity ≤ 0) and null `sale_number`** — must not create baskets or
   pairs. → Task 10.
3. **Lift arithmetic and the per-product edge cap** — a pair kept by only one end must survive;
   isolated best sellers stay as nodes; option values outside range are clamped. → Task 11.
4. **Tone propagation** — a `fail` source must turn every downstream domain, metric, derived metric
   and dashboard `fail`, and a derived metric with mixed inputs takes the worst. → Task 5.
5. **Local graph on an unknown or removed node id** — returns empty, never throws. → Task 1.
6. **A catalogue domain missing from `goldys.freshness.sources`** — fails the build. → Task 3.

---

## Phase 0 — Gate

### Task 0: Measure basket sizes in production (read-only)

**Files:** none committed; record the result in the PR description and, if the cap changes, in
spec §5.1 via a follow-up note (don't rewrite the dated spec; add an "Addendum" line).

- [ ] **Step 1:** Open a read-only session (`SET default_transaction_read_only = on;`) against the
  production database as `docs/audits/production-data/` describes. Do not touch `.env.prod`; ask the
  user for access if it isn't already set up.
- [ ] **Step 2:** Run:

```sql
WITH b AS (
  SELECT trading_date, sale_number,
         count(DISTINCT lower(trim(item_name))) AS products
  FROM canonical_sale_item
  WHERE superseded_at IS NULL AND sale_number IS NOT NULL AND quantity_sold > 0
  GROUP BY 1, 2)
SELECT count(*) AS baskets,
       percentile_cont(ARRAY[0.5, 0.9, 0.99]) WITHIN GROUP (ORDER BY products) AS p50_p90_p99,
       max(products) AS max_products,
       count(*) FILTER (WHERE products > 25) AS over_25
FROM b;
```

(`superseded_at IS NULL` is the system-time "current" predicate per V1; confirm against
`BitemporalRepository` before running.)

- [ ] **Step 3: Decide.** Median ≤ 12 → keep `MAX_BASKET_PRODUCTS = 25` (or set it near p99) and
  proceed with Phase B. Median > 12 → stop Phase B and report back: the basket definition needs
  rethinking. Phase A does not depend on this.

---

## Phase A — Component + platform map

### Task 1: Network types and pure helpers

**Files:**
- Create: `frontend/components/network/types.ts`
- Create: `frontend/components/network/graph-ops.ts`
- Test: `frontend/components/network/graph-ops.test.ts`

**Interfaces:** Produces `NetworkKind`, `NetworkNode`, `NetworkEdge`, `NetworkGraph` (spec §3.1),
and `neighbourhood`, `filterKinds`, `seededRandom`, `positionCacheKey`, consumed by Tasks 2, 5, 13.

- [ ] **Step 1: Write failing tests** covering:
  - `neighbourhood(g, "a", 0)` → only `a`, no edges.
  - `neighbourhood(g, "a", 1)` on `a–b, b–c, a–d` → `{a, b, d}`, edges `a–b, a–d` (edges are
    undirected; `c` excluded; edge `b–c` excluded because `c` is not kept).
  - `neighbourhood(g, "a", 2)` → adds `c` and `b–c`.
  - `neighbourhood(g, "missing", 2)` → `{ nodes: [], edges: [] }`.
  - `filterKinds(g, new Set(["metric"]))` drops other kinds and any edge touching them.
  - `seededRandom(42)` returns the same first five values on two calls; all in `[0, 1)`.
  - `positionCacheKey("platform", g)` is unchanged when node order changes and changes when a
    node is added.
- [ ] **Step 2: Run** `cd frontend && bun run test components/network` — expect failures.
- [ ] **Step 3: Implement.** Reference implementation for the two non-obvious helpers:

```ts
/** Nodes within `depth` undirected hops of `focusId`, and only the edges between them. */
export function neighbourhood(graph: NetworkGraph, focusId: string, depth: number): NetworkGraph {
  if (!graph.nodes.some((n) => n.id === focusId)) return { nodes: [], edges: [] };
  const adjacent = new Map<string, string[]>();
  for (const e of graph.edges) {
    adjacent.set(e.source, [...(adjacent.get(e.source) ?? []), e.target]);
    adjacent.set(e.target, [...(adjacent.get(e.target) ?? []), e.source]);
  }
  const kept = new Set([focusId]);
  let frontier = [focusId];
  for (let hop = 0; hop < depth; hop++) {
    const next: string[] = [];
    for (const id of frontier) {
      for (const other of adjacent.get(id) ?? []) {
        if (!kept.has(other)) {
          kept.add(other);
          next.push(other);
        }
      }
    }
    frontier = next;
  }
  return {
    nodes: graph.nodes.filter((n) => kept.has(n.id)),
    edges: graph.edges.filter((e) => kept.has(e.source) && kept.has(e.target)),
  };
}

/** Park–Miller LCG: reproducible jitter for d3-force's randomSource, so layouts are stable. */
export function seededRandom(seed: number): () => number {
  let state = seed % 2147483647 || 1;
  return () => {
    state = (state * 48271) % 2147483647;
    return (state - 1) / 2147483646;
  };
}
```

- [ ] **Step 4: Run tests** — expect pass. `bun run typecheck && bun run lint`.
- [ ] **Step 5: Commit** `feat(network): graph types and pure helpers`.

### Task 2: `NetworkCanvas`, `NetworkList`, `NetworkExplorer`

**Files:**
- Modify: `frontend/package.json`, `frontend/bun.lock` (`bun add react-force-graph-2d@<exact>`;
  pick a release at least two weeks old and write it without `^`)
- Create: `frontend/components/network/network-canvas.tsx`
- Create: `frontend/components/network/network-list.tsx`
- Create: `frontend/components/network/network-explorer.tsx`
- Create: `frontend/components/network/layout-cache.ts` (try/catch-wrapped `localStorage` read/write
  of `{ id: { x, y } }` keyed by `positionCacheKey`)
- Test: `frontend/components/network/network-list.test.tsx`,
  `frontend/components/network/network-explorer.test.tsx`,
  `frontend/components/network/layout-cache.test.ts`

**Interfaces:** `NetworkCanvas` props per spec §3.3; `NetworkExplorer` props:
`{ graph, graphKey, ariaLabel, kinds: { kind: NetworkKind; label: string }[], focusPanelExtra?: (node) => ReactNode }`.

- [ ] **Step 1: Failing tests.**
  - `NetworkList` renders one row per node, grouped by `kind`, each row showing label, subtitle and
    a tone badge; a row's button calls `onFocus(id)`.
  - `NetworkExplorer` (mock `next/dynamic` so the canvas renders a stub): toggling a kind hides its
    rows in the list; typing in search and pressing Enter focuses the first label match; the "Local
    graph" toggle with depth 1 shows only the focused node and its neighbours in the list; the focus
    panel lists neighbours sorted by edge weight descending.
  - `layout-cache`: a throwing `localStorage` (getter throws) returns `undefined` and does not throw.
- [ ] **Step 2: Implement `NetworkCanvas`.**
  - `const ForceGraph2D = dynamic(() => import("react-force-graph-2d"), { ssr: false });`
  - Seed: on mount, apply cached positions as `fx/fy`-free initial `x/y`; set
    `d3Force("charge").strength(-60)`, `d3Force("link").strength((l) => 0.2 + 0.6 * l.weight)`;
    `warmupTicks={60}`, `cooldownTicks={200}`; pass `seededRandom(1)` through the simulation's
    `randomSource` via the `ref` (`ref.current.d3Force` exposes the simulation forces; set it before
    the first tick). Save positions to the cache in `onEngineStop`.
  - `nodeCanvasObject`: circle radius `3 + node.size * 1.4`, fill from `colourFor(node)`, a 1.5px
    ring in the tone colour when tone isn't `neutral`/`ok`; label drawn when `globalScale > 1.6`, the
    node is hovered, or it is focused.
  - Hover dimming: keep a `Set` of highlighted ids (hovered + neighbours) in state; draw others at
    `globalAlpha = 0.15`; same for links.
  - Colours: read `--muted-foreground`, `--status-*`, `--destructive`, `--primary` with
    `getComputedStyle(document.documentElement)` on mount and when `prefers-color-scheme` or the
    theme class changes.
  - Wrapper `div` has `role="region"` + `aria-label`; the canvas element is `aria-hidden`.
  - Wheel zoom: `enableZoomInteraction` is `false` until the canvas receives a click, then `true`
    until pointer leave.
- [ ] **Step 3: Implement `NetworkList` and `NetworkExplorer`** (spec §3.4). Below `md`
  (the existing `useIsMobile` hook in `frontend/components/hooks/use-mobile.tsx`) render only the list.
- [ ] **Step 4:** `bun run test && bun run typecheck && bun run lint` — pass.
- [ ] **Step 5: Commit** `feat(network): force-directed canvas, list and explorer`.

### Task 3: Platform map DTOs and service (backend)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/NetworkGraphDto.java`
  (`record NetworkGraphDto(List<NetworkNodeDto> nodes, List<NetworkEdgeDto> edges)`)
- Create: `.../semantic/NetworkNodeDto.java`
  (`record NetworkNodeDto(String id, String kind, String label, String subtitle, String tone, int size, String group, String href)`)
- Create: `.../semantic/NetworkEdgeDto.java`
  (`record NetworkEdgeDto(String source, String target, double weight, String label)`)
- Create: `.../application/PlatformMapService.java`
- Test: `backend/src/test/java/com/goldys/platform/application/PlatformMapServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/application/PlatformMapCompletenessIntegrationTest.java`

**Interfaces:**
- Consumes: `PermissionService.require`, `MetricCatalog` (`ids`, `definition`, `constituents`),
  `FreshnessProperties` (`sources`, `thresholds`), `ConnectorHealthQuery.health()`,
  `SavedDashboardApplicationService.list(role, email)` and `get(role, email, id)` for widget metrics.
- Produces: `NetworkGraphDto map(UserRole role, String email)` consumed by Task 4.

- [ ] **Step 1: Failing unit tests** (Mockito for collaborators, a real `MetricCatalog`):
  - Without `connectors` READ → `AccessDeniedException`, and no collaborator other than
    `PermissionService` is called.
  - With it: one `source:` node per distinct source in `FreshnessProperties.sources()`; one
    `domain:` node per catalogue `sourceDomain`; a `metric:`/`derived-metric:` node per catalogue id
    (kind from `constituents(id).isEmpty()`); `permission:` nodes per distinct `requiredPermission`.
  - Edges: `source:CTB — domain:resolved_daily_sales`; `domain:resolved_daily_sales —
    metric:sales.gross`; `derived-metric:labour.cost_per_cover — metric:labour.cost` and `—
    metric:reservations.covers`; `metric:sales.gross — permission:reconciliation.sales`.
  - Source tone: `FAILED`/`PARTIAL` → `fail`; last run older than the domain threshold (use the
    smallest threshold among the domains it feeds; default 1 day as `DataQualityService` does) →
    `warn`; null `lastRunAt` → `missing`; otherwise `ok`. Use an injected `Clock`.
  - Dashboard with two widgets on `sales.gross` and one on `labour.cost` → edges with weights
    `0.666…` and `0.333…`; `href` = `/dashboards/{id}`.
  - Non-source nodes are sent with tone `neutral` (propagation is the frontend's job, spec §4.3).
- [ ] **Step 2: Implement.** Keep node-id prefixes as constants. Labels: source ids title-cased
  (`LIGHTSPEED` → "Lightspeed", `CTB` → "Cooking the Books", `OPENTABLE` → "OpenTable",
  `DEPUTY` → "Deputy"; unknown ids fall back to the raw id); domain labels from a small map with a
  fallback that strips `resolved_` and `_day` and title-cases the rest. Metric `href` =
  `/data-health?metric={id}`; comment why (the provenance sheet opens from there).
- [ ] **Step 3: Completeness guard.** `PlatformMapCompletenessIntegrationTest` boots the Spring
  context (Testcontainers, as other `*IntegrationTest`s do) and asserts every
  `catalog.definition(id).sourceDomain()` is a key in `FreshnessProperties.sources()`. **This fails
  today**: `resolved_payment_day`, `resolved_deleted_sale_day` and `resolved_sale_item_day` (all
  Lightspeed-only) have no entry. Add each to `backend/src/main/resources/application.yml` under
  `goldys.freshness.sources` with `- LIGHTSPEED`, in a separate commit
  (`fix(config): declare sources for Lightspeed-only domains`), since it also changes
  `DataQualityService`'s attribution for those metrics. Leave their thresholds at the 1-day default.
- [ ] **Step 4:** `./gradlew test --tests '*PlatformMap*' spotlessCheck` — pass.
- [ ] **Step 5: Commit** `feat(platform-map): compose dependency graph from catalogue and config`.

### Task 4: Platform map endpoint and boundaries

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/api/PlatformMapController.java`
  (`@GetMapping("/api/platform-map")`, identity + delegation, same shape as `InventoryController`)
- Test: `backend/src/test/java/com/goldys/platform/api/PlatformMapControllerTest.java`
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`

- [ ] **Step 1: Failing tests.** Controller: 200 with the service's DTO as JSON; 403 body matches
  the existing "not permitted" shape when the service throws `AccessDeniedException`. Architecture:
  `PlatformMapService` and (later) `MenuAffinityService` do not depend on `..canonical..`; the
  `Network*Dto` records reside in `..semantic..`.
- [ ] **Step 2: Implement**, run `./gradlew test spotlessCheck`, **commit**
  `feat(platform-map): GET /api/platform-map`.

### Task 5: Frontend API method and `buildPlatformMap`

**Files:**
- Modify: `frontend/lib/api/types.ts` (`NetworkGraphDto` mirror types), `frontend/lib/api/index.ts`
  (`getPlatformMap(): Promise<NetworkGraphDto>` on `Api`), `frontend/lib/api/live.ts`,
  `frontend/lib/api/demo.ts` (fixture: 4 sources with CTB `fail`, the real catalogue domains, ~10
  metrics, 3 derived, 2 dashboards)
- Create: `frontend/components/platform-map/platform-map-graph.ts`
- Test: `frontend/components/platform-map/platform-map-graph.test.ts`, extend
  `frontend/lib/api/demo.test.ts`

Follow the `frontend-api-method` skill for the Api wiring.

- [ ] **Step 1: Failing tests** for `buildPlatformMap(dto): { graph: NetworkGraph; impact: Map<string, string[]> }`:
  - `source:CTB` `fail` → `domain:resolved_daily_sales` `fail` → `metric:sales.gross` `fail` →
    `derived-metric:sales.average_spend_per_cover` `fail` → a dashboard using either `fail`.
  - A derived metric with one `ok` and one `warn` input → `warn`.
  - Tone order is `fail > missing > warn > info > ok > neutral`; a node with no upstream source
    stays `neutral`.
  - `impact.get("source:CTB")` lists every downstream node id exactly once.
  - Permission nodes never inherit tone (they are not downstream of data).
  - Size and kind pass through; unknown `kind` strings from the API are dropped, not crashed on.
- [ ] **Step 2: Implement.** Direction for propagation comes from kind order
  (`source → domain → metric → derived-metric → dashboard`), not from edge order, because edges are
  undirected in the DTO. Derived-of-derived works because the pass repeats until no tone changes
  (bounded by node count).
- [ ] **Step 3:** `bun run test && bun run typecheck && bun run lint`; **commit**
  `feat(platform-map): api method and tone-propagating builder`.

### Task 6: Platform map on Data health and in the provenance sheet

**Files:**
- Create: `frontend/components/platform-map/platform-map-view.tsx`
- Modify: `frontend/app/(app)/data-health/page.tsx` (a "Dependencies" section)
- Modify: `frontend/components/trust/provenance-sheet.tsx` (a "Related" tab: `neighbourhood` of
  `metric:{id}` at depth 2 rendered with `NetworkExplorer`)
- Test: `frontend/components/platform-map/platform-map-view.test.tsx`

- [ ] **Step 1: Failing tests.** The view shows the loading, denied (existing denied-state component)
  and loaded states; with the demo fixture, focusing `source:CTB` shows an impact summary
  "Cooking the Books is failing → N domains, M metrics, K dashboards affected" and the list of
  affected items.
- [ ] **Step 2: Implement.** Kind filters default to all on except `permission`. The provenance
  "Related" tab fetches once per sheet open and reuses the Data health query if the app's data layer
  caches it.
- [ ] **Step 3:** `bun run test && bun run typecheck && bun run lint && bun run build` (not
  concurrently with typecheck). **Commit** `feat(platform-map): dependencies view and related tab`.
- [ ] **Step 4: See it working.** `bun run dev` in demo mode; open `/data-health`, check light and
  dark themes and a 375px-wide viewport (list only). Screenshot for the PR.

### Task 7: Phase A docs

- [ ] Update `docs/architecture/current-state.md`: add `PlatformMapService` to "Application
  services" and the `Network*Dto` records to the semantic list.
- [ ] Add a frontend note to `docs/frontend/` (whichever file indexes components) for
  `components/network/`.
- [ ] **Commit** `docs: platform map and network component`.

---

## Phase B — Menu affinity (only after Task 0 passes)

### Task 8: Basket lines from the canonical facade

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/BasketLineView.java`
  (`record BasketLineView(LocalDate tradingDate, String saleNumber, String itemName, String categoryName, BigDecimal quantitySold, BigDecimal amount)`)
- Modify: `.../canonical/CanonicalSaleItemQuery.java` (add
  `List<BasketLineView> currentBasketLinesForDates(Collection<LocalDate> dates)`, built from the same
  repository call `currentSaleItemsForDates` uses)
- Test: extend the existing `CanonicalSaleItemQuery` test (create
  `CanonicalSaleItemQueryTest.java` if none exists)

- [ ] Failing test → implement → `./gradlew test --tests '*CanonicalSaleItemQuery*' spotlessCheck`
  → **commit** `feat(canonical): expose basket lines for affinity projection`.

### Task 9: V42 migration

Use the `add-migration` skill.

**Files:** Create `backend/src/main/resources/db/migration/V42__product_affinity_read_model.sql`

```sql
-- Product-affinity read model: disposable daily basket and pair counts.
-- Reconstructed from canonical_sale_item by ProductAffinityProjector. Never a source of truth.
-- Single source (Lightspeed); counts are additive so any date range is a SUM.

CREATE TABLE resolved_basket_day (
    trading_date date PRIMARY KEY,
    baskets integer NOT NULL,
    excluded_large_baskets integer NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE resolved_product_basket_day (
    trading_date date NOT NULL,
    product_key text NOT NULL,
    product_label text NOT NULL,
    category_name text,
    baskets integer NOT NULL,
    quantity numeric(14,4) NOT NULL,
    amount numeric(14,4) NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, product_key)
);

CREATE TABLE resolved_product_pair_day (
    trading_date date NOT NULL,
    product_a text NOT NULL,
    product_b text NOT NULL,
    baskets integer NOT NULL,
    resolved_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (trading_date, product_a, product_b),
    CONSTRAINT resolved_product_pair_day_ordered CHECK (product_a < product_b)
);
```

- [ ] Confirm V42 is still the next free number (`ls backend/src/main/resources/db/migration`);
  renumber if main moved. Run `./gradlew test --tests '*Flyway*'` or any integration test to apply
  it. **Commit** `feat(db): V42 product affinity read model`.

### Task 10: `ProductAffinityProjector`

**Files:**
- Create in `backend/src/main/java/com/goldys/platform/reconciliation/`:
  `ResolvedBasketDay.java`, `ResolvedProductBasketDay.java`, `ResolvedProductPairDay.java` (JPA
  entities, `ddl-auto: validate` must accept them), their repositories (each with
  `deleteByTradingDateIn(Collection<LocalDate>)`), and `ProductAffinityProjector.java`
- Modify: `.../reconciliation/SaleItemProjectionListener.java` (call
  `affinityProjector.recompute(date)` after `projector.recompute(date)`),
  `.../reconciliation/StartupProjectionSeeder.java` (`affinityProjector.recomputeAll()`)
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ProductAffinityProjectorTest.java`
  (unit, mocked facade + repositories) and
  `ProductAffinityProjectorIntegrationTest.java` (Testcontainers round-trip)

- [ ] **Step 1: Failing unit tests.** For one date:
  - Baskets `S1 {pie, guinness}`, `S2 {pie, guinness, chips}`, `S3 {chips}` → `baskets = 3`;
    product baskets `pie 2, guinness 2, chips 2`; pairs `(guinness, pie) 2`, `(chips, guinness) 1`,
    `(chips, pie) 1` (keys ordered so `product_a < product_b`).
  - Two lines of `Pie` and `pie ` in one basket count the product once (normalised key).
  - A line with `quantity_sold = -1` (refund) or `0` is ignored; a basket left empty isn't counted.
  - A null `sale_number` line is ignored.
  - A basket with 26 distinct products adds 1 to `excluded_large_baskets` and nothing else.
  - The same `sale_number` on two different dates is two baskets.
  - `product_label` is the most frequent raw `item_name` for the key (ties → alphabetical, for
    determinism); `category_name` likewise.
- [ ] **Step 2: Implement.** `MAX_BASKET_PRODUCTS = 25` as a documented constant (cite Task 0's
  measurement in the comment). Products per basket are a sorted `TreeSet`, so pair generation is
  `for i < j` and the CHECK constraint holds by construction.
- [ ] **Step 3: Integration test:** insert canonical sale items through the real ingest facade,
  trigger `recompute`, assert table contents; then supersede one line and recompute, assert counts
  change (proves the projection reads current facts only).
- [ ] **Step 4:** `./gradlew test spotlessCheck`. **Commit**
  `feat(affinity): project daily basket and pair counts`.

### Task 11: `ProductAffinityQuery`

**Files:**
- Create in `semantic`: `ProductAffinityQuery.java`, `AffinityGraph.java`, `AffinityProduct.java`,
  `AffinityPair.java`, `AffinityOptions.java` (with `DayFilter` nested enum, `DEFAULTS`, and a compact
  constructor that clamps: `minTogether` 2–100, `minLift` 1.0–10.0, `maxEdgesPerProduct` 1–15,
  `maxProducts` 20–300)
- Create in `reconciliation`: `ResolvedProductAffinityQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/reconciliation/ResolvedProductAffinityQueryTest.java`

- [ ] **Step 1: Failing tests** with hand-computed numbers:
  - `N = 100`, `n_pie = 20`, `n_guinness = 25`, `n_ab = 10` → lift `= 10·100/(20·25) = 2.0`,
    confidence pie→guinness `0.5`, guinness→pie `0.4`.
  - A pair with `together = 4` is dropped at `minTogether = 5`; a pair with lift `1.1` is dropped at
    `minLift = 1.2`.
  - Per-product cap: product X with 8 qualifying partners keeps its top 6 by lift; a pair that is in
    X's bottom 2 but in its partner's top 6 survives.
  - A top-20 product with no surviving pairs remains in `products`; a non-top-20 one is dropped.
  - `DayFilter.WEEKENDS` sums only Saturday/Sunday dates (`DayOfWeek` of `trading_date`).
  - `new AffinityOptions(ALL, 0, 0.5, 99, 5)` clamps to `(ALL, 2, 1.0, 15, 20)`.
  - Empty range → `AffinityGraph(0, 0, [], [])`, not an exception.
- [ ] **Step 2: Implement.** Sum per product and per pair with repository aggregate queries over
  `trading_date BETWEEN :from AND :to` (JPQL `GROUP BY`), apply the day filter in SQL with
  `EXTRACT(ISODOW FROM trading_date)` via a native query or in Java over the per-day rows; choose
  the native query if the per-day pair rows for a 90-day range exceed ~100k (measure with Task 0
  numbers). Then the five-step pass from spec §5.3, with a comment on step 4 explaining why "either
  end keeps it".
- [ ] **Step 3:** `./gradlew test spotlessCheck`. **Commit**
  `feat(affinity): lift-ranked affinity query over resolved counts`.

### Task 12: Menu affinity service and endpoint

**Files:**
- Create: `.../application/MenuAffinityService.java` (`permissions.require(role, new ResourceKey("sale-items.metrics"), PermissionAction.READ)` then delegate)
- Create: `.../api/MenuAffinityController.java`
  (`GET /api/menu-affinity?from&to&days=ALL&minTogether&minLift`; `days` bound as the enum so an
  unknown value is a 400; missing thresholds take `AffinityOptions.DEFAULTS`)
- Test: `MenuAffinityServiceTest.java`, `MenuAffinityControllerTest.java`,
  `MenuAffinityIntegrationTest.java` (403 without the resource for a non-owner role; 200 for
  `ALL × OWNER`)
- Modify: `ArchitectureBoundariesTest` (Task 4's assertion now covers `MenuAffinityService`;
  add that `Affinity*` records live in `..semantic..`)

- [ ] Failing tests → implement → `./gradlew test spotlessCheck` → **commit**
  `feat(affinity): GET /api/menu-affinity`.

### Task 13: Frontend API method and `buildAffinityNetwork`

**Files:**
- Modify: `frontend/lib/api/types.ts`, `index.ts`, `live.ts`, `demo.ts` (`getMenuAffinity(params)`;
  demo fixture of ~40 pub products in 5 categories with three clear clusters: roast + red wine +
  sticky date pudding; burger + chips + lager; oysters + sparkling — plus one isolated best seller)
- Create: `frontend/components/affinity/affinity-graph.ts`
- Test: `frontend/components/affinity/affinity-graph.test.ts`, extend `frontend/lib/api/demo.test.ts`

- [ ] **Step 1: Failing tests:** node size is `1 + 9·√(baskets/maxBaskets)` rounded to 1 dp;
  `group` = category (null → "Uncategorised"); edge weight = `lift / maxLift`; edge label text
  matches `"lift 2.0 · 10 sales together · 50% of Pie also had Guinness"`; `pairsFor(graph, key)`
  returns partners sorted by lift desc with confidence from the focused product's side.
- [ ] **Step 2: Implement**, `bun run test && bun run typecheck && bun run lint`, **commit**
  `feat(affinity): api method and network builder`.

### Task 14: Menu affinity page

**Files:**
- Create: `frontend/app/(app)/menu-affinity/page.tsx`
- Create: `frontend/components/affinity/affinity-view.tsx`,
  `frontend/components/affinity/pairs-panel.tsx`
- Modify: `frontend/lib/nav.ts` (Performance group: `{ title: "Menu affinity", href: "/menu-affinity", icon: Network, keywords: ["pairs", "basket", "sells together", "combos", "upsell"] }`),
  `frontend/lib/nav.test.ts`
- Test: `frontend/components/affinity/affinity-view.test.tsx`

- [ ] **Step 1: Failing tests:** loading / denied / empty ("No sales in this range") / loaded
  states; changing the days toggle or a slider refetches with the new params; focusing a product
  shows the pairs panel; the footer shows basket count, excluded large baskets and
  "Lightspeed only (single source)".
- [ ] **Step 2: Implement.** Reuse the date-range control the Sales screens use. The pairs panel is
  passed as `focusPanelExtra` to `NetworkExplorer`. Debounce slider refetches (300 ms).
- [ ] **Step 3:** `bun run test && bun run typecheck && bun run lint`, then `bun run build`.
  **Commit** `feat(affinity): menu affinity page`.
- [ ] **Step 4: See it working** in demo mode: clusters visibly separate, hover dims, local graph
  works, phone width shows the list. Screenshots for the PR.

### Task 15: Phase B docs and index

- [ ] `docs/connectors/matching-and-identity.md`, sale item section: add the basket key
  `(trading_date, sale_number)`, the product key `ProductNameKey.normalize(item_name)`, the
  quantity > 0 rule and the large-basket exclusion.
- [ ] `docs/architecture/current-state.md`: `MenuAffinityService`, `ProductAffinityQuery`, the three
  `resolved_*` tables.
- [ ] `docs/superpowers/README.md`: add the 10-11 row (Outcome: "Implemented (V42)" once merged).
- [ ] **Commit** `docs: menu affinity`.

---

## Final verification (both phases)

- [ ] `cd backend && ./gradlew test spotlessCheck build`
- [ ] `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`
  (sequentially)
- [ ] Re-read the diff against the Global Constraints: no `canonical` imports outside
  `reconciliation`/`canonical`/`connectors`, no new permission seeds, no `^` on
  `react-force-graph-2d`, demo implementations for both Api methods.
- [ ] One PR per phase. Phase A can merge on its own.
