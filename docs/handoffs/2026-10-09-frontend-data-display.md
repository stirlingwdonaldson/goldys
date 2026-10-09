# Frontend Data Display — Handoff / Context (2026-10-09)

> Purpose: record what the `feature/frontend-data-display` branch changed, the patterns
> it introduced, and what's still open, so a fresh agent can extend it without re-deriving anything.

## 1. The ask

Show the data the backend already serves but the frontend didn't, using the shadcn **data table**
for lists and **n8n-style node graphs** (nodes + edges) where lineage/flow is the point. Visual rules
come from `docs/design-system.md` (shadcn defaults, no custom fonts, light only) — the node canvas is
the only bespoke-looking surface.

## 2. Gaps found before this branch

| Backend endpoint | Frontend before | Now |
|---|---|---|
| `GET /api/labour/summary?from&to` | No client; Staff page was `AwaitingData` only | Staff & Labor stats |
| `GET /api/inventory/summary?from&to` | No client | Kitchen top-line stats |
| `GET /api/reservations/covers?from&to` | No client | Reservations daily-covers table |
| `GET /api/provenance/{metricId}/{date}` | `ProvenancePanel` existed but was mounted nowhere, and called `fetch` directly (bypassing demo/live) | "Trace" sheet on Sales + Reservations rows |

All four are now on the `Api` interface (`frontend/lib/api/types.ts`) with live implementations
(`live.ts`) and demo fixtures (`demo.ts`), so they follow the usual `useApiData((api) => …)` pattern.

## 3. What was built

### Data tables (`frontend/components/data-table/`)
- `ui/table.tsx`, `ui/dropdown-menu.tsx` — stock shadcn primitives (default style).
- `data-table.tsx` — the shadcn data-table recipe on TanStack Table: sorting, optional text filter
  (`filterColumn`), column-visibility menu, client pagination (`pageSize={false}` when the server
  pages). Column `meta: { title, align }` drives the visibility menu label and right-alignment.
- `data-table-column-header.tsx` — sortable header button.
- Adopted by: Sales daily totals, Kitchen (unit cost per measure, spend by supplier), Reservations
  daily covers, and the Data explorer's canonical/resolved views (`GenericTable`, which keeps the
  explorer's server-side `Pager` — sorting there orders the current page only).

### Node graphs (`frontend/components/flow/`)
- `FlowCanvas` — read-only `@xyflow/react` canvas: dotted background, zoom controls, draggable
  nodes, scroll-wheel zoom off (page still scrolls), node `href` → `router.push`.
- `StepNode` — card with an icon tile tinted by tone, title/subtitle/detail lines, and an n8n-style
  corner badge for ok / warn / fail / missing.
- `layoutColumns` — deterministic left-to-right column layout from a pure `ColumnGraph`; edge
  colour and dashing come from tone (`missing` = dashed, "nothing flowing").
- Graph *builders* are pure functions with unit tests; components only fetch and render.

**Pipeline map** — `components/pipeline/` → Data health ("How data flows").
Sources → Raw ledger → Canonical entities → Resolution → Resolved domains. Counts come from the
explorer's paged endpoints with `size=1` (reads `total` only). Nodes link to Logs, the Data explorer
(`/data?layer=canonical&entity=…`, new deep-link param), Resolution rules, or the owning screen
(mapping in `frontend/lib/data-layers.ts`, shared with the explorer).

**Provenance graph** — `components/trust/provenance-{graph,view,sheet}.tsx`.
Each source's value → how it was resolved (`agreed` / `rule` / `override` / `conflict` / `single`,
the kinds `TrustService.resolutionDetail` emits) → the resolved figure. The chosen source's edge
takes the resolution's colour; a disagreeing source is amber; a missing one is dashed "No data".
`ProvenancePanel` now wraps the same view.

### Screens touched
`app/(app)/{sales,reservations,kitchen,staff,data-health,data}/page.tsx`,
`components/staff/staff-page-view.tsx` (now takes an optional `labour` request; Owner-only cost
figures still fail closed on absent seniority).

### Smaller changes
- `lib/rule-logic.ts` `sourceLabel()` gained `DEPUTY` and `OPENTABLE`; graphs and tables show labels,
  not codes.
- Demo connector fixtures now use live source codes (`LIGHTSPEED`, `CTB`, `DEPUTY`, `OPENTABLE`) —
  live `ConnectorStatus.source` equals raw `sourceSystem`, which is what lets the pipeline match a
  connector to its raw-record count. Demo `listRawRecords` now honours the `source` filter.
- New dependencies: `@tanstack/react-table`, `@xyflow/react`.
- `docs/design-system.md` §3 (screen inventory) and §6 (data-table + node-graph patterns) updated.

## 4. Deliberate decisions

- **No guessed edges.** The API doesn't say which canonical entity feeds which resolved domain, so
  the pipeline joins them through one Resolution node. Provenance `rawRecordIds` carry no source, so
  raw records are listed as evidence links under the graph rather than drawn as nodes.
- **Conflict is `warn`, not `fail`** in graphs, per the design system (resolvable, not a fault).
- **Null is never zero.** Unreadable counts say "Count unavailable"; an all-null labour summary
  renders `AwaitingData`.

## 5. Open items / known limits

1. **Labour permission shape.** `LabourReportingService.summary` requires both `labour.hours` and
   `labour.cost`, so a role allowed hours but not cost gets `NOT_PERMITTED` for the whole Staff page
   (shown as an explicit permission-denied state). Splitting the endpoint would let those roles see
   hours. Permission rows are still an open PRD question — don't guess them.
2. **Provenance outside sales is unverified.** `TrustService` resolution detail is built around the
   sales entity; "Trace" on a covers row may return thin detail or an error (the sheet handles both).
   Nothing here was exercised against a live backend — only demo mode, tests, and the build.
3. **Pipeline request fan-out** — one count request per connector, entity, and domain. Fine at
   current scale; a single `/api/data/summary` endpoint would replace it if it grows.
4. Raw records and Logs still use bespoke expandable-row tables (row expansion isn't in the
   data-table yet).

## 6. Verification

From `frontend/`: `bun run typecheck`, `bun run lint`, `bun run test` (155 passing, including new
`data-table.test.tsx`, `pipeline-graph.test.ts`, `provenance-graph.test.ts`, extra
`staff-page-view.test.tsx` cases), `bun run build`. Screens checked visually in demo mode.
