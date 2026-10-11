# Network Graph (Obsidian-style) — Design

**Status:** Draft
**Date:** 2026-10-11
**Scope:** A reusable, force-directed network graph component and its first two uses: a
**platform map** (sources → domains → metrics → dashboards, coloured by freshness) and a
**menu affinity map** (which products sell together). Design only; the executable plan is
`docs/superpowers/plans/2026-10-11-network-graph.md`.

---

## 1. Objective

The three graphs that already exist (pipeline map, provenance graph, invoice drill graph) are
**column layouts** of a flow: `layoutColumns` places nodes deterministically, left to right. They
answer "how does data flow from here to there?".

An Obsidian-style graph answers a different question: "**what is related to what?**" It has no
columns. Nodes settle under physics, related nodes pull together, and the reader looks for
**clusters, hubs and loners**. This spec adds that view as a sibling of `components/flow/`, not a
replacement for it.

Two first uses, chosen because one needs no new data and the other is the most valuable to the
business:

1. **Platform map**: "CTB is stale. Which metrics and dashboards does that affect?" and "What is
   food cost % built from?" Built entirely from structures that already exist.
2. **Menu affinity map**: "What sells with what?" Products are nodes; two products are linked when
   they appear on the same sale more often than chance. Clusters are natural combinations, which
   feed bundles, menu layout and upsell prompts.

## 2. Decisions

| # | Decision | Choice |
|---|---|---|
| A | Library | **`react-force-graph-2d`** (canvas + `d3-force`), loaded with `next/dynamic` and `ssr: false`. Not `@xyflow/react` (DOM nodes, no force layout) and not `sigma.js` (WebGL; only worth it beyond ~5k nodes). Expected sizes: platform map < 150 nodes, affinity map ≤ 250 nodes. |
| B | Component shape | A generic `NetworkGraph` type + `NetworkCanvas` component in `frontend/components/network/`, and one **pure, deterministic builder per use** (`buildPlatformMap`, `buildAffinityNetwork`), mirroring `buildInvoiceGraph`. |
| C | Determinism | The simulation uses a **seeded random source** (`d3-force` `randomSource`) and a fixed warm-up, so the same graph draws the same picture. Settled positions are cached per graph key in `localStorage` (a per-viewer convenience, wrapped in try/catch; the graph renders correctly without it). |
| D | Obsidian features copied | Hover highlights a node's neighbours and dims the rest; click focuses a node and opens its **local graph** (N hops, depth slider 1–3); filter by node kind; search-to-focus; colour groups with a legend. |
| E | Accessibility and mobile | The canvas is decorative to screen readers (`aria-hidden` + region label, as `FlowCanvas` does). Every network view renders a **companion list** from the same builder output: the accessible view everywhere and the only view below the `md` breakpoint. |
| F | Where edges are computed | **Backend**, read through `semantic` queries. Business consumers never derive relationships from canonical rows. Endpoints return graphs already capped (§5.3, §6.4). |
| G | Platform map data | Composed in `application` from the `MetricCatalog` (definitions + `constituents`), `FreshnessProperties.sources()` (domain → source systems), `ConnectorHealthQuery`, and saved dashboards the caller can see. No new tables. |
| H | Affinity data | New **resolved projections** built by a `ProductAffinityProjector` in `reconciliation` from `canonical_sale_item` (which carries `sale_number`). The existing `resolved_sale_item_day` is day × category, so the basket is already gone there. |
| I | Authorization | **No new resources.** Platform map: one gate, `connectors` READ (the operator gate `DataQualityService` already uses); it shows catalogue *metadata* (names, formulas, dependencies), never values. Per-metric filtering is deliberately not done: a denied check must throw, never return a silently partial graph (`.claude/rules/architecture-invariants.md`). Dashboards are those `SavedDashboardApplicationService.list` already returns to the caller (visibility, not permission filtering). Affinity: `sale-items.metrics` READ (same underlying data as the sale-items metrics). |
| J | Placement | Platform map: a "Dependencies" section on **Data health**, plus a "Related" tab in the provenance sheet (local graph of one metric). Affinity: a new **Menu affinity** page under Performance (`/menu-affinity`). |
| K | Out of scope (v1) | Supplier ↔ product network, recipe edges, staff ↔ product graph (blocked on the field-to-role open question), an Ask Goldy's `network` widget (widget schema v3), service-period filtering (sale items carry no time of sale). §9 records them. |

## 3. The shared component

### 3.1 Types (`frontend/components/network/types.ts`)

```ts
export type NetworkKind =
  | "source" | "domain" | "metric" | "derived-metric" | "dashboard" | "permission" // platform map
  | "product";                                                                    // affinity

export interface NetworkNode {
  id: string;            // stable, namespaced: "metric:sales.gross", "product:guinness"
  kind: NetworkKind;
  label: string;
  subtitle?: string;     // one short line in the tooltip / list
  tone: FlowTone;        // reuse the flow vocabulary: ok | warn | fail | missing | info | neutral
  size: number;          // relative, 1..10; the canvas maps it to a radius
  group?: string;        // colour group (category for products, kind for the platform map)
  href?: string;         // where "Open" goes from the focus panel
}

export interface NetworkEdge {
  source: string;
  target: string;
  weight: number;        // 0..1, drives stroke width and link strength
  label?: string;        // tooltip text, e.g. "lift 2.4 · 61 sales together"
  tone?: FlowTone;
}

export interface NetworkGraph { nodes: NetworkNode[]; edges: NetworkEdge[] }
```

`FlowTone` is imported from `components/flow/types.ts` so both families share one status vocabulary
and the design-system colours.

### 3.2 Pure helpers (unit-tested, no React)

- `neighbourhood(graph, focusId, depth)` → the local graph: BFS over undirected edges, keeping only
  edges between kept nodes. Depth 0 returns the node alone; an unknown id returns an empty graph.
- `filterKinds(graph, kinds)` → drops nodes of hidden kinds and their dangling edges.
- `seededRandom(seed)` → a small LCG returning `() => number` in `[0, 1)`, fed to
  `d3-force`'s `randomSource` so initial jitter is reproducible.
- `positionCacheKey(graphKey, graph)` → `graphKey` + a hash of sorted node ids, so a cached layout is
  reused only when the node set is unchanged.

### 3.3 `NetworkCanvas` (`frontend/components/network/network-canvas.tsx`)

Props: `graph`, `graphKey`, `ariaLabel`, `height`, `focusId?`, `onFocus?(id)`, `colourFor(node)`.

- Wraps `ForceGraph2D` loaded via `next/dynamic(() => import("react-force-graph-2d"), { ssr: false })`.
- Custom `nodeCanvasObject` draws a filled circle (radius from `size`) with the tone ring and, past a
  zoom threshold or on hover, the label in the app font.
- Hover: the hovered node, its neighbours and their edges keep full opacity; everything else drops
  to 15%.
- Scroll-wheel zoom stays **off** until the canvas is clicked (as `FlowCanvas` keeps it off), so the
  page still scrolls.
- Colours are read from CSS variables at mount and on theme change, so light and dark both work.

### 3.4 `NetworkExplorer` (`frontend/components/network/network-explorer.tsx`)

Composes the canvas with the Obsidian controls: kind filter toggles, search box (focuses the first
match), "Local graph" toggle + depth slider, a legend, and a focus panel (label, subtitle, the
node's neighbours ranked by edge weight, and an "Open" link to `href`). Below `md`, renders only the
companion list (`NetworkList`).

## 4. Use 1: platform map

### 4.1 Nodes

| Kind | One per | Label | Size | Tone |
|---|---|---|---|---|
| `source` | source system in `FreshnessProperties.sources()` values | `LIGHTSPEED` → "Lightspeed" | 6 | from connector health: `FAILED`/`PARTIAL` → `fail`, stale → `warn`, ok → `ok`, never run → `missing` |
| `domain` | `sourceDomain` used by any catalogue metric | e.g. "Daily sales" | 5 | worst tone of its sources |
| `metric` | base catalogue metric | catalogue `name` | 3 | inherited from its domain |
| `derived-metric` | derived catalogue metric | catalogue `name` | 3 | worst tone of its constituents |
| `dashboard` | saved dashboard visible to the caller | dashboard title | 4 | worst tone of its metrics |
| `permission` | `requiredPermission` of any metric | resource key | 2 | `neutral` |

The endpoint requires `connectors` READ; without it the caller gets the standard "not permitted"
response and the Data health section shows the existing denied state.

### 4.2 Edges

`source — domain` (from `FreshnessProperties.sources()`), `domain — metric` (`sourceDomain`),
`derived-metric — constituent` (`MetricCatalog.constituents`), `dashboard — metric` (each saved
widget's `MetricQuery.metric`), `metric — permission`. Weight 1 except `dashboard — metric`, which is
`widgetsUsingMetric / widgetsOnDashboard`.

### 4.3 Tone propagation (the "blast radius")

The backend sends **raw** tones on `source` nodes only. The pure frontend builder `buildPlatformMap`
propagates downstream (source → domain → metric → derived → dashboard), each node taking the worst
of its upstream tones (`fail` > `missing` > `warn` > `info` > `ok` > `neutral`). Keeping this in the
builder makes it unit-testable and lets the view say plainly: "CTB is failing → 2 domains, 9 metrics,
3 dashboards affected", listing them in the focus panel.

### 4.4 Completeness guard

A backend test asserts every `sourceDomain` in the catalogue has an entry in
`FreshnessProperties.sources()` (loaded from `application.yml`). Adding a domain without saying
where its data comes from fails the build instead of drawing an orphan node.

### 4.5 API

`GET /api/platform-map` → `NetworkGraphDto` (records in `semantic`: `NetworkGraphDto`,
`NetworkNodeDto(id, kind, label, subtitle, tone, size, group, href)`,
`NetworkEdgeDto(source, target, weight, label)`). Served by `PlatformMapService` in `application`
through a thin `PlatformMapController` in `api`. No caching; the whole graph is assembled in memory
from small structures.

## 5. Use 2: menu affinity map

### 5.1 What "sells together" means

- A **basket** is one `(trading_date, sale_number)` from current `canonical_sale_item` facts. Lines
  with a null `sale_number` are excluded (they cannot be grouped).
- A basket's **products** are the distinct `ProductNameKey.normalize(item_name)` values among its
  lines with `quantity_sold > 0` (refund and void lines don't count as buying something).
- Baskets with more than **`MAX_BASKET_PRODUCTS = 25`** distinct products are excluded and counted
  separately. A pub tab or function booking can hold a whole table's order; those baskets link
  everything to everything and drown real pairings. The constant is provisional and must be checked
  against production data (§8) before the screen ships.
- For a date range with `N` eligible baskets, product `a` in `n_a` baskets, `b` in `n_b`, and both in
  `n_ab`:
  - **support** = `n_ab / N`
  - **confidence(a→b)** = `n_ab / n_a`
  - **lift** = `n_ab · N / (n_a · n_b)` — 1.0 means "together exactly as often as chance".

Lift is the edge measure because raw co-counts just rediscover the best sellers (every basket has a
pint in it).

### 5.2 Resolved projections (V42)

Additive daily counts, so any date range is a `SUM` and lift is computed at query time:

```sql
resolved_basket_day        (trading_date PK, baskets int, excluded_large_baskets int, resolved_at)
resolved_product_basket_day(trading_date, product_key, product_label, category_name,
                            baskets int, quantity numeric, amount numeric, resolved_at,
                            PK (trading_date, product_key))
resolved_product_pair_day  (trading_date, product_a, product_b, baskets int, resolved_at,
                            PK (trading_date, product_a, product_b), CHECK (product_a < product_b))
```

`product_label` is the most frequent raw `item_name` for the key that day (display only).
`category_name` is the most frequent category. All three tables are disposable and single-source
(Lightspeed); no `resolution_type` column, because there is nothing to reconcile, and the API says
`single-source` explicitly.

Size check: ~300 baskets/day × ~6 pairs per basket gives at most ~1,800 pair rows/day before
de-duplication, ~650k/year. A range query sums an index range on the PK; acceptable for Postgres 16
at this volume.

### 5.3 Query and caps

`ProductAffinityQuery` (in `semantic`), implemented by `ResolvedProductAffinityQuery`
(`reconciliation`):

```java
AffinityGraph affinity(LocalDate from, LocalDate to, AffinityOptions options);

enum DayFilter { ALL, WEEKDAYS, WEEKENDS }  // ISO day of week, venue dates
record AffinityOptions(DayFilter days, int minTogether, double minLift,
                       int maxEdgesPerProduct, int maxProducts) {}
record AffinityGraph(int baskets, int excludedLargeBaskets,
                     List<AffinityProduct> products, List<AffinityPair> pairs) {}
record AffinityProduct(String key, String label, String category,
                       int baskets, BigDecimal amount) {}
record AffinityPair(String a, String b, int together, double lift,
                    double confidenceAB, double confidenceBA) {}
```

Defaults (`AffinityOptions.DEFAULTS`): `days = ALL`, `minTogether = 5`, `minLift = 1.2`,
`maxEdgesPerProduct = 6`, `maxProducts = 200`. The pass is:

1. Sum the three tables over the range, keeping only dates that match `days`.
2. Keep the top `maxProducts` products by `baskets`.
3. Compute lift for pairs between kept products; drop pairs under `minTogether` or `minLift`.
4. For each product keep its strongest `maxEdgesPerProduct` pairs by lift (a pair survives if
   either end keeps it). This is the anti-hairball step.
5. Drop products left with no pairs, unless they are in the top 20 by baskets (a best seller that
   pairs with nothing is a finding, so it shows as an isolated node).

Options are clamped server-side (`minTogether` 2–100, `minLift` 1.0–10, `maxEdgesPerProduct` 1–15,
`maxProducts` 20–300) so no request can ask for an unbounded graph.

### 5.4 Projection lifecycle

`ProductAffinityProjector.recompute(dates)` deletes and rebuilds the three tables for those dates
from `CanonicalSaleItemQuery.currentBasketLinesForDates(dates)` (a new method returning
`BasketLineView(tradingDate, saleNumber, itemName, categoryName, quantitySold, amount)`; the existing
`SaleItemView` stays as is). It is called from the existing debounced
`SaleItemProjectionListener.flushPendingDates()` alongside `SaleItemProjector`, and from
`StartupProjectionSeeder` via `recomputeAll()`.

### 5.5 API

`GET /api/menu-affinity?from=&to=&days=&minTogether=&minLift=` → `AffinityGraph` JSON, served by
`MenuAffinityService` (`application`, authorizes `sale-items.metrics` READ) through
`MenuAffinityController` (`api`). Only `days`, `minTogether` and `minLift` are exposed to the UI in v1; `days` is an enum, so an unknown value is a 400.

### 5.6 Frontend

`buildAffinityNetwork(graph)` (pure) maps products to `product` nodes (size ∝ √baskets scaled to
1..10, `group` = category, tone `neutral`) and pairs to edges (weight = lift normalised against the
graph's max lift, label = "lift 2.4 · 61 sales together · 38% of X also had Y").

The page (`/menu-affinity`) has: the date range picker used elsewhere, a "all / weekdays / weekends"
toggle (the `days` parameter), the two threshold sliders,
`NetworkExplorer`, and a **"Pairs best with"** panel for the focused product (its partners ranked by
lift with confidence). A footer states the basket count, how many large baskets were excluded, and
"Lightspeed only (single source)".

## 6. Architecture fit

- **Read direction:** the projector (in `reconciliation`) is the only code reading canonical sale
  items for affinity; `api`/`application` read through `ProductAffinityQuery`. The platform map
  reads only catalogue, config, connector health and saved dashboards.
- **`ArchitectureBoundariesTest`:** no new packages. Add assertions that
  `ProductAffinityQuery`/`AffinityGraph` live in `semantic` and that `MenuAffinityService` and
  `PlatformMapService` do not depend on `canonical`.
- **Permissions:** no new resources, no seed migration (§2 I).
- **Identity:** extend `docs/connectors/matching-and-identity.md` (sale item section) with the
  basket key `(trading_date, sale_number)` and the product key `ProductNameKey.normalize(item_name)`.

## 7. Testing

- Backend unit: lift/caps/clamping in `ResolvedProductAffinityQuery` (fixed small baskets with
  hand-computed lift); projector excludes null sale numbers, zero/negative quantities and baskets
  above the cap; platform map throws `AccessDeniedException` without `connectors` READ and builds
  the expected nodes/edges with it; the completeness guard (§4.4).
- Backend integration (`*IntegrationTest`, Testcontainers Postgres): V42 applies; projector
  round-trip from canonical rows to the three tables; `GET /api/menu-affinity` denied without
  `sale-items.metrics`.
- Frontend (Vitest): `neighbourhood`, `filterKinds`, `seededRandom` reproducibility; both builders
  including tone propagation; `NetworkList` renders the same nodes as the canvas; demo fixtures for
  both `Api` methods.

## 8. Risks and things to check first

1. **Large baskets.** If Goldy's runs most sales as table tabs, baskets are tables, not people, and
   pairings blur. Before building the page, run a read-only production query of the distribution of
   distinct products per `(trading_date, sale_number)` and set `MAX_BASKET_PRODUCTS` from it. If the
   median basket is above ~12 products, the affinity map needs a different basket definition and
   Phase B pauses.
2. **Product name drift.** Renamed menu items split one product into two nodes. `ProductNameKey`
   normalisation handles case and whitespace only; a rename shows as two nodes until a product
   master exists. The page footer says so.
3. **Force layout readability.** Mitigated by the per-product edge cap, kind filters and local
   graph; the companion list always gives a readable answer.

## 9. Later (not in this spec)

- **Supplier ↔ product network** from invoice lines (single-supplier risk, overlap); then
  ingredient → dish edges once Recipes is connected, for price-shock tracing.
- **Ask Goldy's `network` widget** (widget schema v3) backed by `ProductAffinityQuery`, e.g. "what
  goes with the Guinness pie?". Needs a bounded, enum-validated tool per
  `docs/contracts/ai-tool-boundary.md`.
- **Staff ↔ product graph**: blocked on the field-to-role permission mapping (PRD open question).
- **Service-period filter**: needs time of sale on sale items, which the current Lightspeed report
  does not carry.
