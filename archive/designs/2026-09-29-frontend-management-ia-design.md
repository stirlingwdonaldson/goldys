# Goldy's Frontend Management IA Design

**Status:** In review
**Date:** 2026-09-29
**Scope:** Frontend information architecture, navigation, and screen design only.
No backend changes.

## 1. Objective

Reposition the Goldy's frontend from a **platform-operations** tool to a
**pub-management** tool, without changing the backend.

Today the application shell presents four technical pages — Dashboard,
Reconciliation, Connectors, Settings — and the Dashboard is entirely
software-centric: ingestion completeness, time-to-detect-failure, connector
health, and run activity. That is the right information for the people who
maintain the platform, not the people who run the pub.

This design:

1. Replaces the technical dashboard with a **business dashboard** for managers.
2. Moves all diagnostics (connector health, ingestion, run activity) to a
   dedicated **Data health** page that any user can still open.
3. Introduces the management sub-pages managers expect — **Sales**,
   **Staff & Labor**, **Reservations**, **Kitchen**, and **Recipes** — designed
   now, populated later as data connects.

The design is deliberately frontend-only. Where a page maps to data that does
not yet exist, it renders a consistent, clearly-marked placeholder rather than
fabricated numbers or a mock presented as operational data.

## 2. Sources of Truth and Current Baseline

Architecture and invariants come from `docs/system-context.md`; scope and
acceptance criteria from `docs/prd.md`; visual/interaction treatment from
`docs/design/design-system.md`. This doc changes only the frontend information
architecture and screen inventory; it does not alter those sources.

Current baseline (verified against the tree on this branch):

- **Sidebar** (`components/app-shell/app-sidebar.tsx`): a single flat group —
  Dashboard, Reconciliation, Connectors, Settings.
- **Dashboard** (`app/(app)/dashboard/page.tsx`): four technical stat cards
  (ingestion completeness, open conflicts, time-to-detect-failure, manual
  overrides), a connector-health strip, a run-activity chart, and an open-conflicts
  list.
- **Live frontend API surface** (`lib/api/types.ts`) is reconciliation/ingestion
  only: `getDashboardSummary`, `getDashboardActivity`,
  `listReconciliationExceptions`, `listProductExceptions`,
  `getReconciliationRecord`, `getProductRecord`, `listConnectorStatuses`,
  `runConnector`, `uploadOpenTableCsv`, `saveOverride`, `saveProductOverride`.
  There are **no** business-reporting endpoints (no sales totals, labor %, or
  covers).
- **Canonical entities that exist** (backend): `CanonicalSaleItem`,
  `CanonicalDailySales`, `CanonicalProductSales`, `CanonicalShift`,
  `CanonicalReservation`. **Not ingested at all:** inventory/stock, wastage, and
  recipe costing (these exist only as unbuilt Lightspeed reference data and CTB
  line-item fields `recipeName`/`stockCode`).

Consequence: of the six dashboard areas below, only **"Needs a decision"** is
backed by live data today. The rest are forward-looking placeholders.

## 3. Scope

### In scope

- New grouped sidebar navigation (Business / Data / Settings).
- Rewritten, business-first Dashboard.
- New `Data health` page consolidating the current Connectors page and the
  technical dashboard content; `/connectors` redirects to it.
- New placeholder-backed business sub-pages: Sales, Staff & Labor, Reservations,
  Kitchen, Recipes.
- A reusable `AwaitingData` placeholder pattern and a `NeedsDecisionBand` hero.
- `docs/design/design-system.md` screen-inventory and navigation updates.

### Out of scope

- Any backend change: no new endpoints, DTOs, canonical entities, connectors, or
  Flyway migrations.
- Populating business pages with real data (blocked on future reporting
  endpoints and, for Kitchen/Recipes, on connectors that do not exist).
- Conversational BI, Smart Exporter, Automation Hub, or Phase 2 rule engine.
- Re-architecting authentication, permissions, or the demo/live API split.

## 4. Information Architecture

Three sidebar groups (shadcn `SidebarGroup`, collapsible `icon` retained):

| Group | Items | Live today |
|---|---|---|
| **Business** | Dashboard · Sales · Staff & Labor · Reservations · Kitchen · Recipes | Dashboard partially; others placeholder |
| **Data** | Reconciliation · Data health | Both live |
| *(footer)* | Settings | Live |

Routing:

- New: `/sales`, `/staff`, `/reservations`, `/kitchen`, `/recipes`, `/data-health`.
- `/connectors` is removed as a route and redirects to `/data-health`.
- `/dashboard`, `/reconciliation`, `/settings` keep their paths.

Icons (lucide-react): `LayoutDashboard`, `TrendingUp`, `Users`, `CalendarDays`,
`ChefHat`, `BookOpen`, `Scale`, `Activity`, `Settings`.

### 4.1 Group rationale

- **Business** — what managers open to *see* their numbers.
- **Data** — what managers open to *verify* their numbers (Reconciliation) and
  to check the *plumbing* (Data health). Diagnostics remain visible to every
  user; they are simply no longer the landing surface.

## 5. Cross-cutting patterns

### 5.1 `AwaitingData` (placeholder)

One reusable component (`components/states/awaiting-data.tsx`) replaces ad-hoc
empty screens. It renders:

- the area's **label + icon**;
- a one-line *"what you'll see here"* description;
- a muted reason line (*"Awaiting Deputy data"*, *"Inventory not yet
  connected"*);
- **no fabricated numbers or charts.**

It is distinct from the existing `EmptyState`: `EmptyState` means "the data
exists but is empty right now"; `AwaitingData` means "this surface is not wired
to data yet." This honors the design-system rule against presenting mock data as
operational.

### 5.2 Permission handling

Reuse `PermissionDenied` (full lock state, never a partial view). Wage and
labor-cost figures remain Owner-only per the permission model; non-Owner roles
see a lock on those surfaces (the Staff & Labor page, and the Labor dashboard
tile).

### 5.3 Status colors

Reuse the design-system semantic tokens: `--status-success` (reconciles),
`--status-warning` (needs a decision), `--status-missing` (no data from source),
and `--destructive` (a genuine connector/system failure — never a conflict).

## 6. Screen designs

### 6.1 Dashboard (`/dashboard`)

Approach A — trust first, then business.

1. **Header** — "Dashboard", subtitle "Your numbers, verified.", and a period
   chip (e.g. "Today · trading week 40").
2. **`NeedsDecisionBand`** (live) — full-width card:
   - open conflicts present: warning tint, *"**N** numbers need a decision —
     these figures will disagree in your reports until resolved."*, button
     **"Review in Reconciliation"** → `/reconciliation`;
   - none: success tint, *"All numbers reconcile."*, muted link to
     `/reconciliation`.
   - Data: `getDashboardSummary().openConflicts` + `listReconciliationExceptions()`
     + `listProductExceptions()` — all existing endpoints.
3. **Business KPI grid** — five `AwaitingData` tiles:

   | Tile | "What you'll see" | Reason |
   |---|---|---|
   | Sales | Net sales today & this week vs. last week | Awaiting sales-reporting endpoint |
   | Labor cost % | Scheduled vs. actual hours and labor % of sales | Awaiting Deputy reporting (Owner-only) |
   | Covers today | Today's covers, bookings, no-shows | Awaiting OpenTable reporting |
   | Top sellers | Best-selling items and revenue mix | Awaiting product-sales reporting |
   | Food cost | Cost of goods, stock, and wastage | Inventory not yet connected |

   Responsive grid (`sm:grid-cols-2` → `xl:grid-cols-5`). No technical cards
   appear on the dashboard.

### 6.2 Sales (`/sales`)

Header + `AwaitingData`. Destination content: period picker (day/week/month),
net-sales trend, vs-prior-period deltas, product-mix table. Backed later by
`CanonicalDailySales` + `CanonicalProductSales`.

### 6.3 Staff & Labor (`/staff`)

Header + `AwaitingData`. Destination content: roster/shifts, scheduled vs.
actual hours, labor % of sales. Wage figures Owner-only (lock for other roles).
Backed later by `CanonicalShift` (Deputy).

### 6.4 Reservations (`/reservations`)

Header + `AwaitingData`. Destination content: today's covers, bookings list,
no-shows, walk-ins. Backed later by `CanonicalReservation` (OpenTable).

### 6.5 Kitchen (`/kitchen`)

Header + `AwaitingData`. Destination content: food cost %, stock levels,
stocktakes, wastage. **Not ingested** — forward-looking placeholder; reason line
is explicit that inventory is not yet connected.

### 6.6 Recipes (`/recipes`)

Header + `AwaitingData`. Destination content: recipe list, per-dish ingredient
cost, gross margin. **Not ingested** — forward-looking placeholder (Lightspeed
recipes + CTB `recipeName`/`stockCode`).

### 6.7 Reconciliation (`/reconciliation`)

Unchanged in function. Repositioned under Data as the "verify my numbers" page
that the dashboard hero links into.

### 6.8 Data health (`/data-health`)

Consolidates the current Connectors page and the technical half of the current
dashboard:

- connector status list (source, last run, `ConnectorStatusBadge`) with the
  manual "Run now" / OpenTable "Upload CSV" actions;
- ingestion completeness, time-to-detect-failure, and manual-override stat cards;
- run-activity chart (`ActivityChart`).

Retains the design-system distinction between failure (`--destructive`) and
"no new data" (muted).

### 6.9 Settings (`/settings`)

Unchanged.

## 7. Component and file structure

New/changed under `frontend/`:

```text
app/(app)/
  dashboard/page.tsx            (rewrite)
  data-health/page.tsx          (new; absorbs connectors/)
  sales/page.tsx                (new, placeholder)
  staff/page.tsx                (new, placeholder)
  reservations/page.tsx         (new, placeholder)
  kitchen/page.tsx              (new, placeholder)
  recipes/page.tsx              (new, placeholder)
  connectors/                   (removed; redirect → /data-health)
components/
  app-shell/app-sidebar.tsx     (rewrite: three groups)
  states/awaiting-data.tsx      (new)
  dashboard/needs-decision-band.tsx (new)
  dashboard/business-kpi-grid.tsx  (new)
  dashboard/connector-health-strip.tsx (removed; superseded by the Data health connector list)
```

Existing reusable pieces stay: `ConnectorStatusBadge`, `ActivityChart`,
`StatCard`, `EmptyState`, `PermissionDenied`, `ErrorState`, `LoadingState`,
`ScreenErrorBoundary`.

## 8. Testing strategy (frontend)

- Component tests for `AwaitingData` (renders label, description, reason; never
  renders fabricated values) and `NeedsDecisionBand` (open vs. zero conflict
  states).
- Keep `activity-chart.test.tsx` and `reconciliation-logic.test.ts` green.
- Update any test that asserts the old flat sidebar or old dashboard sections.
- Verification gates: `bun run typecheck`, `bun run lint`, `bun run build`.

## 9. Commands

Unchanged (see `docs/operations/testing.md` and the Phase 1 spec §12):

- Install: `bun install`
- Dev: `bun run dev`
- Build: `bun run build`
- Lint: `bun run lint`
- Type check: `bunx tsc --noEmit`
- Test: `bun run test`

## 10. Commit discipline

Atomic Conventional Commits; one logical change per commit. Suggested sequence:

```text
feat: regroup sidebar into Business / Data / Settings navigation
feat: replace technical dashboard with business dashboard
feat: add awaiting-data placeholder and business KPI grid
feat: add needs-a-decision dashboard band
feat: add data-health page and redirect connectors
feat: add sales, staff, reservations, kitchen, recipes placeholder pages
docs: update design-system screen inventory and navigation
test: cover awaiting-data and needs-decision band
```

## 11. Boundaries

### Always

- Honor `docs/design/design-system.md`: shadcn defaults, light mode, calm/trustworthy,
  semantic status colors.
- Never present fabricated business numbers as operational data.
- Use `PermissionDenied` for restricted surfaces; never a partial view.
- Distinguish "no new data" from a connector failure.
- Keep the frontend data contracts in `lib/api` (demo + live) in sync.

### Ask first

- Add a production dependency or change the pinned stack.
- Introduce a page or KPI beyond this spec's inventory.
- Touch authentication, permissions, or the demo/live API split.

### Never

- Change the backend.
- Wire a placeholder page to fake data to make it look complete.
- Read reporting data from raw or canonical tables (the reporting path stays a
  backend concern, out of scope here).

## 12. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Dashboard looks empty (5/6 areas placeholder) | Managers perceive low value | Trust-first hero gives a real, actionable signal; placeholders name what's coming |
| Placeholders read as bugs, not intentional | Confusion | Single `AwaitingData` pattern with a consistent reason line |
| `/connectors` removal breaks bookmarks/links | Broken deep links | Redirect `/connectors` → `/data-health` |
| Page set diverges from PRD screen inventory | Design drift | Update `docs/design/design-system.md` in the same change |

## 13. Success criteria

- The Dashboard shows business concerns only; no ingestion/connector/run
  diagnostics appear on it.
- Diagnostics are reachable by any user on `Data health`, and `/connectors`
  redirects there.
- The six management sub-pages exist with correct headers and `AwaitingData`
  placeholders; live pages (Reconciliation, Data health) still function.
- `AwaitingData` never renders fabricated values.
- The Labor surfaces lock for non-Owner roles via `PermissionDenied`.
- `bun run typecheck`, `bun run lint`, and `bun run build` pass.
