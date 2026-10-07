# Goldy's Dashboards Design

**Status:** Draft for review
**Date:** 2026-10-07
**Scope:** The saved-dashboard subsystem (PRD "Dashboards" section): templates, safe editing,
constrained layout, reusable filters, refresh semantics, versioning, sharing + permission
enforcement, AI integration, and library UX — built on the existing semantic metric catalogue and
the shared widget renderer. Design only; implementation follows in a plan.

## 1. Objective

Extend the existing saved-dashboard subsystem from a minimal "list + render" feature into a full
dashboard product: users can create, edit, lay out, filter, version, share, and favourite dashboards;
templates give a starting point; Ask Goldy's can draft dashboards declaratively. Everything renders
through the existing shared `WidgetRenderer`/`WidgetRegistry` — no new BI frontend framework, no
separate hardcoded rendering path.

Two invariants dominate the design:

1. **Widgets store semantic queries, not snapshots and not code.** A saved dashboard persists
   bounded `MetricQuery`s (from `semantic.catalog`) plus a render type and layout. Rendering re-runs
   those queries, so a reopened dashboard always shows current resolved data.
2. **Authorization happens at render time, per metric, independently of document sharing.** Sharing
   a dashboard can never grant a viewer access to a metric their role is not permitted to see.

## 2. Sources of truth & baseline

- `docs/prd.md` — the platform PRD (roles, resolved-views, "same permission model, multiple
  enforcement points").
- `docs/architecture/current-state.md` — dependency direction and the shared widget/dashboard
  runtime (widget contract v2, `saved_dashboard` table, `GET /api/dashboards/{id}/render`).
- `docs/contracts/widget-spec.schema.json` (v2) and `dashboard-document.schema.json` (v1) — the
  machine-readable contracts.
- `docs/superpowers/specs/2026-10-06-semantic-metric-catalogue-design.md` and its implementation —
  `semantic.catalog` (`MetricId`, `MetricDefinition`, `MetricQuery`, `MetricQueryService`,
  `TimeRange`/`TimeGrain`/`Dimension`/`Comparison`, `MetricResult`).
- `docs/superpowers/specs/2026-10-02-reporting-tool-framework-design.md` and
  `2026-10-02-conversational-bi-design.md` — `ReportingTool`/`ToolDispatcher` and the SSE Ask
  Goldy's loop.

**Baseline (in tree):** `semantic.catalog` is fully implemented (27 metrics, base + derived, each
carrying a `requiredPermission` ResourceKey). The four `ReportingTool`s already build a `MetricQuery`
(or read `*MetricsQuery`) and wrap the result in a widget spec — they are thin renderers over the
catalogue already. `saved_dashboard` (V18) persists `{id, title, description, layout:"grid",
widgets:[{id, tool, input}], created_by, created_at, updated_at}` and renders via `ToolDispatcher`.
Saved-dashboard access is Owner-only (`dashboards` resource, ALL×OWNER). Metric permissions are
Owner-only (V6/V13/V23).

## 3. Scope

### In scope

- Widget query model: `MetricQuery[]` + render type + layout, replacing `tool`+`input`.
- A shared `MetricQuery[] + renderType → WidgetSpec` renderer, reused by tools and dashboard render.
- Constrained layout model (order + width/height spans, breakpoint-aware collapse).
- Dashboard-level filters (date range, comparison period, dimension group-bys).
- Versioning (snapshot-per-revision, restore).
- Sharing/visibility (PRIVATE / SHARED-with-roles / ORG_WIDE) with per-metric render-time auth.
- Nine templates as a code-based catalogue + instantiation.
- AI dashboard drafts (declarative, validated, draft→confirm-save).
- Library UX (list cards, recent, pin/favourite, descriptions, empty/error states) + a dashboard
  editor.
- Schema v2, data migration, and the full PRD test list.

### Out of scope

- Snapshot (point-in-time result) storage — deferred (see §18).
- Venue and product-category filters — no catalogue dimension yet (see §18).
- Free-form x/y drag placement — the layout model is order + spans (see §6).
- Free-form filter expressions or arbitrary CSS/component code — structurally impossible by design.
- Populating the BOH/FOH field-to-role permission matrix — that is the PRD's standing stakeholder
  open question; this design only provides the mechanism (see §10).

## 4. Architecture

No new packages. The existing dependency direction is preserved:

```
api                      delivery (thin controllers)
conversational           SSE + AI tools (drafts, no persistence)
application               use-case services: composition + authorization + response mapping
reporting                tool dispatch + shared MetricQuery[] → WidgetSpec renderer
semantic.catalog         metric catalogue (leaf)
dashboard                saved-dashboard entity, widget/document types, template catalogue
widget                   widget-spec schema records (leaf)
auth                     permissions
```

New/changed responsibilities:

- **`dashboard.SavedWidget`** changes shape to `{id, renderType, queries: List<MetricQuery>,
  layout}` (see §5). `dashboard` already depends on `semantic.catalog` types (allowed — `dashboard`
  is not in any leaf exclusion list).
- **`reporting.WidgetRenderer`** (new) — the single place a `List<MetricQuery>` + render type
  becomes a `WidgetSpec`. Lives in `reporting` because it bridges `semantic.catalog` (results) and
  `widget` (specs), both of which `reporting` may depend on. The four existing tools are refactored
  to use it, so composite tables (labour, food cost) and single-metric charts share one path.
- **`application.SavedDashboardApplicationService`** grows: create/update now validate the document,
  write a revision, and manage visibility/pinning; `render` authorizes per metric and uses the
  shared renderer (no `ToolDispatcher`).
- **`conversational`** gains declarative dashboard-draft tools that validate against the catalogue
  and return an unpersisted `DashboardDocument` draft (§11). It keeps the "never reaches persistence"
  rule: it depends on a validation/`dashboard` document types port, not on repositories.
- **`dashboard.DashboardTemplateCatalog`** (new) — the nine templates as `DashboardDocument`s,
  mirroring `MetricCatalog`.

### ArchUnit rules to add

1. `dashboard` must not depend on `reconciliation`, `canonical`, `ingestion`, or `api` (it is a
   document/entity layer over `semantic.catalog` + `widget`).
2. The shared renderer (`reporting.WidgetRenderer`) depends only on `semantic.catalog` + `widget`.
3. `conversational` must not reach `dashboard` repositories (drafts are unpersisted); it may depend
   on `dashboard` document/type records.

## 5. Widget query model

A saved widget is a bounded, re-runnable query plus a rendering choice and a layout slot:

```java
public record SavedWidget(
    String id,
    String renderType,                 // stat | time-series | bar-chart | table | ranked-list
    List<MetricQuery> queries,         // 1..4 metrics; each MetricQuery is bounded
    WidgetLayout layout) {}            // { w: 1..12, h: 1..4 }

public record WidgetLayout(int w, int h) {}
```

`MetricQuery` (already in `semantic.catalog`) is `{metric, TimeRange, TimeGrain, Set<Dimension>,
Comparison}` — no column/SQL/filter surface. `queries` is a list (not a single metric) because three
current tools are composite: labour = {scheduled, actual, cost, variance}, food cost = {purchases,
wastage}, reservation summary = {bookings, attended, covers, no_shows, no_show_rate, conversion}.

### Render mapping (enforced by the shared renderer + document validation)

- **N = 1, `TimeSeriesResult`** → `time-series`, `bar-chart`, `stat` (sum or latest), or `table`.
- **N = 1, `RankedListResult`** → `ranked-list`.
- **N > 1 (all `TimeSeriesResult`, same grain)** → `table` (one column per metric, summed over the
  range) or `bar-chart`/`time-series` (one series per metric, when units are compatible).

Validation rejects a `renderType`/`queries` combination the mapping does not support, and rejects
any metric not in the catalogue. Render-time authorization is per metric: if any metric in a
widget's `queries` is not permitted for the viewer, the whole widget renders as an explicit denial
(never a partially-filtered result — see §10).

### Bridge for existing AI single-answer saves

The AI "answer a question" path still emits `WidgetSpec`s whose re-runnable `WidgetQuery` is
`{tool, input}` (the `widget` package stays a leaf and cannot import `semantic.catalog`). To save one
of those answers as a dashboard widget, each `ReportingTool` exposes the `MetricQuery[]` it already
computes internally (an extracted `List<MetricQuery> toMetricQueries(ToolInput)` method). The
dashboard create path converts via that method once, at save time; afterwards the widget is rendered
purely from its stored `MetricQuery[]`. The composite reservation tool exposes the subset of its
columns that are catalogue metrics (`cancelled` and `walkIns` have no metric — see §18).

## 6. Layout model

Constrained, data-only, no free-form positioning:

- A dashboard is a single 12-column grid (`layout: "grid"`, unchanged from V18).
- Each widget declares `w` (1..12 columns) and `h` (1..4 row units). **Placement is the widget's
  position in the ordered `widgets` list** — there is no arbitrary `x`/`y`, which keeps the model
  overlap-free and drag-engine-free.
- Breakpoint collapse is a frontend concern derived from the same data: 12 columns on `lg`, 2 on
  `md`, 1 on `sm`. The stored `w`/`h` are honored on `lg`; smaller breakpoints stack or halve.
- Layout is persisted as JSON on each widget, entirely separate from executable frontend code.

"Placement" and "size" editing = reorder (move a widget up/down) and change `w`/`h`. This satisfies
the PRD's "widget placement / widget size" and "grid position / width / height / breakpoint-aware
constraints" while staying constrained.

## 7. Filter model

Dashboard-level reusable filters, stored in a `filters` object and merged into each widget's
`MetricQuery` at render:

```java
public record DashboardFilters(
    TimeRange dateRange,            // from, to, calendar (restriction)
    Comparison comparison,          // null or PREVIOUS_WEEK, SAME_WEEKDAY_LAST_WEEK, ...
    Set<Dimension> dimensions) {}   // group-bys: SERVICE_PERIOD, DEPARTMENT, PRODUCT
```

- **date range** → overrides each widget's `TimeRange`.
- **comparison period** → sets each widget's `Comparison`; the existing `ComparisonService`
  computes the reference range and the renderer emits the delta.
- **dimensions** → applied as group-bys **only to widgets whose metric declares that dimension
  valid** (the `MetricCatalog` already declares valid dimensions per metric). A metric that does not
  declare `DEPARTMENT` is unaffected by a `DEPARTMENT` filter.

This is the "only expose filters supported by the underlying semantic queries" principle applied
literally: the filter UI/API enumerates its options from the catalogue (available comparisons, the
dimension set), and there is no free-form filter expression — values are bounded enums.

## 8. Refresh semantics

Live semantic queries only. `render` re-runs each widget's `MetricQuery[]` through
`MetricQueryService` on every open. No snapshot storage (deferred; if snapshots are ever added, the
revision table is the natural host, and a `refresh: live|snapshot` mode becomes explicit — see §18).

## 9. Versioning

Snapshot-per-revision (not event sourcing — no event log, no replay):

- New table `saved_dashboard_revision {id, dashboard_id, revision int, document jsonb, created_by,
  created_at}`.
- Every create writes revision 1; every update writes the next revision as a full `DashboardDocument`
  snapshot. `saved_dashboard` holds the current document (and points at its latest revision number).
- `restore(revision)` copies that revision's document into `current` and writes a **new** revision
  (a restore is itself a versioned edit, so nothing is lost).
- Retention: keep-all for now; a cap is a future enhancement (§18).

## 10. Sharing & permissions

Two orthogonal axes, matching the PRD's "shared visibility" and "metric-level permissions":

### Document visibility (who can open/list a dashboard)

`saved_dashboard.visibility ∈ {PRIVATE, SHARED, ORG_WIDE}`, plus `saved_dashboard_share
{dashboard_id, department, seniority}` for the SHARED role list.

- `PRIVATE` — the creator only.
- `SHARED` — the creator plus any role (department × seniority) listed in `saved_dashboard_share`.
- `ORG_WIDE` — any authenticated user with `dashboards` READ.

Edit (update/delete/restore/share) is restricted to the creator or a user holding `dashboards`
WRITE. Viewing a SHARED/ORG_WIDE dashboard still requires `dashboards` READ. "Shared with permitted
staff" is modeled as *roles*, never individual user grants — consistent with the PRD's no-per-user
non-goal.

### Data access (enforced at render, per metric)

`render` evaluates every metric in every widget through
`permissions.require(role, metric.requiredPermission(), READ)` using the metric's declared
`requiredPermission` from the catalogue (the same ResourceKeys `ToolDispatcher` uses). This happens
**at execution/rendering time, not at save time** — so a dashboard saved while the sharer had access
still denies restricted widgets to a viewer who lacks the metric permission. A denied widget renders
as an explicit "not permitted" placeholder, never a partial or silently-filtered widget.

**Sequencing dependency (explicit):** metric permissions are currently Owner-only (V23). The sharing
mechanism and its enforcement are complete and fully testable, but BOH/FOH staff will see denied
widgets in shared dashboards until the stakeholder field-to-role matrix is seeded. Populating that
matrix is the PRD's standing open question, deliberately not invented here.

## 11. AI integration

Ask Goldy's gains declarative **dashboard-draft** tools alongside the existing read-only reporting
tools. The invariant is unchanged: the model emits bounded, declarative JSON — never JSX, SQL, CSS,
or component names.

- Tools: `create_dashboard_draft`, `add_widget_to_dashboard`, `modify_dashboard_filters` (and
  layout-suggestion folded into `create_dashboard_draft`). Their inputs are typed records, so Spring
  AI derives bounded schemas and out-of-whitelist `MetricId`/`Dimension`/`Comparison`/renderType
  values fail deserialization before any execution — the same boundary as `get_sales_by_period`.
- **Flow (draft → validate → confirm):** the tool validates the draft against the catalogue and the
  document schema and returns it in the SSE `answer` payload **unpersisted**. The drawer renders a
  preview with a "Save dashboard" / "Apply to dashboard" action. On confirm, the frontend calls the
  normal `POST /api/dashboards` or `PUT /api/dashboards/{id}` — so validation, versioning, sharing,
  and permission checks all run through the same create/update path as manual edits. No auto-persist.
- `add_widget_to_dashboard`/`modify_dashboard_filters` receive the current dashboard document (or its
  id + the change) and return a proposed full document; the confirm still routes through the normal
  update endpoint.

## 12. Templates

A code-based `DashboardTemplateCatalog` (mirrors `MetricCatalog`) defines the nine templates as
`DashboardDocument`s built from `MetricId`s — so templates cannot reference a nonexistent metric and
are valid by construction:

1. Daily Management — today's sales, covers, labour, conflicts.
2. Weekly — Front of House — covers/reservations/service labour for the week.
3. Weekly — Back of House — inventory/food-cost/kitchen labour for the week.
4. Inventory — purchases, wastage, stock-on-hand, food-cost %.
5. Sales Performance — gross/net/GST trend, average spend per cover.
6. Labour — scheduled/actual/cost/variance, labour %.
7. Reservations — bookings/covers/no-shows, no-show rate, conversion.
8. Food Cost — purchases, wastage, food-cost %.
9. Owner Overview — headline sales, labour %, food-cost %, covers.

Templates use the same `DashboardDocument` format and render through the same path; instantiation
(`POST /api/dashboards/from-template/{id}`) copies a template into a new PRIVATE dashboard owned by
the caller. No separate hardcoded rendering path.

## 13. UX

- **Library/list:** card per dashboard — title, description, creator, last-updated, pinned state,
  visibility badge. Sorted by `updated_at` desc.
- **Recent:** the same list sorted by recency (top-N by `updated_at`); no separate store.
- **Favourites/pinned:** `saved_dashboard.pinned` boolean with a toggle; pinned dashboards sort first.
- **Editor:** title, description, filters, add/remove/duplicate/reorder/resize widgets, visibility
  + share roles, revision history + restore. Duplicate copies a widget's `queries` + `renderType` +
  `layout` with a fresh id.
- **States:** reuse the existing `EmptyState`, `ErrorState`, `LoadingState`, `PermissionDenied`;
  add a per-widget "not permitted" placeholder for denied widgets in a shared dashboard.

## 14. Data model & migrations

Next migration(s) after V24 (`V25__dashboard_documents_v2.sql`, split as needed):

- **Alter `saved_dashboard`:** add `filters jsonb NOT NULL DEFAULT '{}'`,
  `visibility varchar(16) NOT NULL DEFAULT 'PRIVATE'`, `pinned boolean NOT NULL DEFAULT false`,
  `current_revision integer NOT NULL DEFAULT 1`. `widgets` jsonb changes shape (§5) — migrated in
  the same script.
- **`saved_dashboard_revision`** (new, §9).
- **`saved_dashboard_share`** (new, §10), with a unique key on `(dashboard_id, department,
  seniority)`.

**Data migration of existing `widgets` (`{tool, input}` → `{renderType, queries, layout}`):** map by
tool — `GET_SALES_BY_PERIOD` → `time-series` + `[sales.<metric>]` (metric from input);
`GET_LABOUR_COST` → `table` + `[labour.scheduled_hours, labour.actual_hours, labour.cost,
labour.hours_variance]`; `GET_FOOD_COST` → `table` + `[inventory.purchases, inventory.wastage]`;
`GET_RESERVATION_SUMMARY` → `table` + the catalogue-metric subset. Layout defaults to `{w:6, h:2}`.
Widgets whose tool/metric can't be mapped are preserved-but-flagged (rendered as a "needs attention"
notice) rather than silently dropped. Assumption: no production dashboard data exists that must be
preserved byte-for-byte (pre-launch).

**`docs/contracts/dashboard-document.schema.json` bumps to v2:** `schemaVersion: 2`; adds `filters`,
`visibility`, `pinned`, and the new widget shape (`renderType`, `queries[]` with `metric`/`range`/
`grain`/`dimensions`/`comparison`, `layout {w,h}`). A `WidgetSchemaTest` asserts the v2 schema is
closed/versioned and that a v1 document validates after migration.

## 15. API surface

| Method | Path | Change |
|---|---|---|
| GET | `/api/dashboards` | list — add creator, description, pinned, visibility, updatedAt |
| POST | `/api/dashboards` | create — extended document (filters, widgets v2, visibility) |
| GET | `/api/dashboards/{id}` | document — extended |
| PUT | `/api/dashboards/{id}` | update — extended; writes a revision |
| DELETE | `/api/dashboards/{id}` | delete (unchanged) |
| GET | `/api/dashboards/{id}/render` | render — per-metric auth, shared renderer, per-widget denial |
| GET | `/api/dashboards/templates` | list templates (as DashboardDocuments) |
| POST | `/api/dashboards/from-template/{templateId}` | instantiate as a PRIVATE dashboard |
| GET | `/api/dashboards/{id}/revisions` | revision history |
| POST | `/api/dashboards/{id}/revisions/{rev}/restore` | restore (writes a new revision) |
| PUT | `/api/dashboards/{id}/pin` | toggle pin/favourite |
| GET/PUT | `/api/dashboards/{id}/sharing` | get/set visibility + share roles |
| POST | `/api/conversational/chat` | (existing) — gains dashboard-draft tools + draft payload |

The render response becomes a list of per-widget results carrying either a `WidgetSpec` or an
explicit `deniedResource`, so the frontend can render a permission placeholder.

## 16. Testing strategy

Mapping the PRD's list to concrete tests (unit + PostgreSQL integration + frontend Vitest):

- **create/edit/delete** — `SavedDashboardApplicationService` CRUD incl. filters/visibility/pin.
- **layout persistence** — widget `w`/`h`/order round-trip through save→get→save; schema validation.
- **schema validation** — v2 closed/versioned; v1→v2 migration path (contract test).
- **dashboard migration** — V25 migration maps each known tool; unsupported widgets flagged, not dropped.
- **sharing** — visibility modes: PRIVATE (creator only), SHARED (role list), ORG_WIDE (all
  `dashboards` READ); non-creator edit denied.
- **permission enforcement / restricted metric access** — a shared dashboard renders an explicit
  denial for a widget whose metric the viewer lacks (e.g. BOH viewer on `labour.cost`), never a
  partial result; a denied metric does not leak via a dashboard another user shared.
- **AI-created dashboard save** — a draft validates against the catalogue (invalid metric/dimension
  rejected), is not persisted by the draft tool, and persists only through the normal create path
  after confirm.
- **version restoration** — create → update → restore(revision 1) reproduces the original document
  and writes a new revision.
- **ArchUnit** — the new rules in §4.

## 17. Decisions recorded

- Widget query = `List<MetricQuery>` + render type + layout (not `tool`+`input`), rendered by a
  shared `MetricQuery[] → WidgetSpec` renderer; tools refactored onto it.
- Layout = order + `{w,h}` spans on a 12-col grid, breakpoint-aware; no free-form x/y, no drag engine.
- Filters = date range (restriction) + comparison (computation) + dimension group-bys, gated by the
  catalogue; no free-form expressions; venue and product-category deferred.
- Live queries only; snapshots deferred.
- Versioning = snapshot-per-revision with restore; not event sourcing.
- Sharing = visibility enum (PRIVATE/SHARED-with-roles/ORG_WIDE) + per-metric render-time auth; no
  per-user grants.
- AI = declarative dashboard-draft tools; draft → validate → user-confirm-save; no auto-persist.
- Templates = code-based `DashboardTemplateCatalog` returning `DashboardDocument`s; instantiation
  copies to a PRIVATE user dashboard.

## 18. Remaining product enhancements (deferred, design-compatible)

- Snapshot (point-in-time) widget results and a `refresh: live|snapshot` mode.
- Venue and product-category filters (require catalogue dimensions).
- `reservations.cancelled` / `reservations.walk_ins` metrics (reservation summary parity).
- Free-form x/y drag placement, if ever wanted.
- Revision retention cap / cleanup policy.
- Per-widget dimension override (vs. dashboard-level group-by).

## 19. Open questions

- **Existing dashboard data:** confirm no production `saved_dashboard` rows need byte-preserving
  migration (assumed pre-launch).
- **Composite render bounds:** `queries` is capped at 4; confirm that is sufficient for the templates
  (it is for the nine defined here).
- **Shared-role edit rights:** this design keeps edit to creator + `dashboards` WRITE; confirm
  whether SHARED roles should ever edit (current answer: no).
