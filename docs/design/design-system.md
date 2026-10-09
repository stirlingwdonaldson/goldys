# Frontend Design System

**Status:** Living reference. Principles and the screen inventory below describe
the shipped frontend. Visual tokens and global styling are defined in
[`ui-direction.md`](ui-direction.md), which overrides §1's original "shadcn
unmodified" rule and §5's tokens.

**Non-normative for architecture** — this doc governs visual/interaction
decisions only. It never overrides `system-context.md` or `prd.md`; where a
screen's data shape is still unresolved (see "Blocked screens" below), this
doc describes layout intent only, not final field lists.

---

## 1. Design principles

- **Built on shadcn/ui, with Goldy's own visual direction.** Components stay
  shadcn primitives. Global styling (inset panel, ink plus one gold accent,
  soft status pills, source identity tiles, Geist type) and component
  choices follow [`ui-direction.md`](ui-direction.md), which
  supersedes the earlier "shadcn unmodified" rule.
- **Minimize cognitive load over raw data density.** The default failure
  mode for a reconciliation/reporting tool is dumping every field into a
  giant table and letting the user hunt for what matters. Don't build that.
  Default views summarize and highlight; full detail is a drill-in, not the
  landing state. See §6 for how this applies to the reconciliation screen
  specifically — it's the screen most at risk of becoming an ugly
  spreadsheet if built naively.
- **Calm and trustworthy, not flashy.** This tool surfaces discrepancies in
  money and labor data — closer to a banking/reconciliation dashboard than
  a marketing product.
- **No brand constraint.** Goldy's has no existing brand system this needs
  to reflect.
- **Desktop/laptop first.** Primary usage is managers/owners in the office
  doing reporting and reconciliation work at a desk. A reasonably
  responsive layout is enough; not designing mobile-first.
- **Light mode only, for now.** The shadcn scaffold's dormant `.dark` block
  in `globals.css` stays in place but unused until there's a real reason
  (e.g. a kitchen/bar display use case) to activate and maintain it.
- **Never hide absence or conflict.** Directly reflects PRD Requirement 5 /
  the edge-case user stories: "no data from source X" and field-level
  disagreement must be visually first-class, never collapsed into a merged
  or blank-looking value — this is in tension with "minimize density," and
  §6 is how both are satisfied at once (surface the conflict prominently,
  hide the agreement).

## 2. Users & context

- **Owner** — full visibility (BOH + FOH + wage data), desktop,
  reporting-focused sessions.
- **BOH / FOH managers** — department-scoped visibility, desktop, mix of
  daily-glance and deeper reconciliation work.
- **Admin/ops staff** — connector status, manual data entry, desktop.

All roles share the same device context, so there's no need for
role-specific responsive breakpoints — only permission-based content
differences (see `PermissionService` in the backend).

## 3. Screen inventory

Tied directly to PRD requirements so design work stays scoped to what's
actually being built:

| Screen | PRD ref | Notes |
|---|---|---|
| Dashboard / business KPI overview | Goals 1–2, Success Metrics | Trust-first: a live "Needs a decision" band above a five-tile business KPI grid. Business areas render as `AwaitingData` placeholders until reporting endpoints land; no ingestion/connector diagnostics on this screen |
| Sales | Goals 1–2 | Daily sales trend + daily totals data table; each row has a "Trace" action opening the provenance graph. Vs-prior-period and product mix not yet built |
| Staff & Labor | Requirement 3 | 30-day hours (scheduled, actual, variance, per cover) for all roles; labor cost and FOH/BOH % Owner-only (fails closed). `AwaitingData` when nothing resolved |
| Reservations | Goals 1–2 | Today's summary stats + 30-day daily covers data table with a source-disagreement badge and "Trace" |
| Kitchen | — | 30-day purchases, wastage, food cost % + unit-cost-per-measure and spend-by-supplier data tables (from enriched invoices) |
| Recipes | — | Forward-looking placeholder; recipe list + ingredient costing (not ingested) |
| Reconciliation view | Requirement 5 | Exception-first summary + drill-in comparison. See §6 |
| Data health / ingestion health | Requirement 6 | Connector status, ingestion completeness, run activity, and the pipeline node graph (§6); failure states visually distinct from "no new data" |
| Permission-denied state | Requirement 3 | Explicit "not permitted" — never a silently filtered or partial-looking view |
| Role/permission admin (future) | Requirement 3 | Not scheduled yet; deferred design |
| Resolution rules | Requirement 8 (Phase 2) | Source-priority rules per field, with audit history; changes trigger recompute |
| Data explorer | Requirement 6 | Raw / canonical / resolved browsing with server-side paging; raw payload detail |
| Logs | Requirement 6 | Ingestion run and failure log viewer |
| Ask Goldy's / Conversations | Requirement 9 (Phase 2) | Chat over the fixed reporting-tool set; answers render through the shared widget registry; threads persist |
| Custom dashboards | Requirement 9 (Phase 2) | Saved widget documents (query config only, re-rendered live), templates, sharing, revisions |
| Smart Exporter | Requirement 10 (Phase 2) | Not built — reuses the widget/tool infrastructure when it lands |

### Blocked screens

The **reconciliation view's** exact field layout can't be fully finalized
until the PRD's two open questions resolve:
- Entity-matching strategy (what identifies "the same event" across
  sources) — affects how records are grouped for comparison.
- Field-to-role permission mapping — affects which fields render per role.

Design the reconciliation view's *pattern* (§6) now; don't hard-code a
final field list against a guess.

## 4. Navigation & layout shell

The shell is built from shadcn's `sidebar-07` block (inset variant, per
`ui-direction.md`). Navigation is defined in `frontend/lib/nav.ts`: a
**Business** group (Overview, Sales, Staff & labour, Reservations, Kitchen,
Custom dashboards, Conversations), a **Data** group (Reconciliation,
Resolution rules, Data health, Data explorer, Logs), a **Coming soon** group
(Recipes), and **Settings** in the footer. A ⌘K command palette searches the
same nav items. Diagnostics live under Data health,
never on the Dashboard.

Goldy's is a single venue and workspace. Do not carry over multi-workspace,
"Add team," billing, or upgrade behavior from demo blocks. User identity comes
from the backend-owned session (`GET /api/me`), and any avatar fallback derives
from the current staff profile rather than placeholder data.

Never present fixture data as operational data: demo mode is always labelled
with the "Demo data" banner, and screens without a backing endpoint render an
`AwaitingData` state instead of invented figures.
The repository remains Bun-only and uses the committed ESLint configuration.

## 5. Visual tokens

Tokens are defined in `frontend/app/globals.css` and documented in
[`ui-direction.md`](ui-direction.md#tokens). The semantic rules
from earlier drafts still hold:

- `--status-conflict` (formerly `--status-warning`) marks a field-level
  conflict that needs a decision.
- `--status-missing` marks "no data from source X".
- `--status-success` marks resolved or matching values.
- `--destructive` is reserved for genuine system failures (a crashed
  connector), never conflicts. A conflict is an expected, resolvable state.
- Source colours (`--source-*`) encode identity only, never state.

## 6. Core interaction patterns

- **Reconciliation view — exception-first, not a spreadsheet.** The
  default screen for a given entity (e.g. a shift, a sale) shows only the
  fields where sources actually disagree or one source is missing data —
  using shadcn's `Card`/`Badge` components, not a dense multi-column table.
  Fields where all sources agree are collapsed out of the default view
  entirely (agreement is not interesting; don't make the user scan past
  it). A "view all fields" affordance opens the full per-source comparison
  (one row per field, one column per source) as a drill-in — that's where
  the detailed table lives, not the landing state. A missing source shows
  an explicit "No data from [source]" badge (`--status-missing`), never a
  blank cell. Once a manual override is set, that exception resolves to a
  `--status-success` badge with a visible "overridden" indicator, and drops
  out of the default exceptions list on next view (still auditable via the
  drill-in).
- **Dashboard — stat cards over tables.** KPIs (ingestion completeness,
  conflict count, etc.) render as a small set of stat cards/tiles, not a
  results table. Use tables only where the user is actually scanning a
  list of records to act on one (e.g. a list of open exceptions to
  triage), not as a default way to present summary numbers.
- **Permission-denied:** a full, explicit state (e.g. a lock icon +
  message naming what's restricted), never a greyed-out or
  partially-rendered version of the real content.
- **Connector failure vs. no new data:** visually distinguishable at a
  glance — failure gets `--destructive` treatment (a system fault), "no
  new data since last check" gets neutral/muted treatment (an expected
  state, not an error).
- **AwaitingData (forward-looking placeholder):** a surface not yet wired to
  data renders its label, a one-line "what you'll see here" description, and a
  muted reason line (e.g. "Awaiting Deputy reporting") — never fabricated
  numbers. Distinct from an empty state, which means "the data exists but is
  empty right now".

- **Lists of records — the shadcn "data table".** Any screen listing records
  uses `components/data-table/data-table.tsx` (TanStack Table on the shadcn
  `Table` primitives): sortable headers via `DataTableColumnHeader`, an
  optional text filter, a column-visibility menu, and client pagination
  (`pageSize={false}` when the server pages). Numeric columns set
  `meta: { align: "right" }`. Don't hand-roll `<table>` markup for new lists.
- **Lineage and flow — n8n-style node graphs.** When the point is *how data
  got here* (pipeline stages, a figure's provenance), draw it as a
  left-to-right node graph with `components/flow/FlowCanvas` (`@xyflow/react`)
  rather than prose or a list. Build the graph as a pure `ColumnGraph` (columns
  of nodes + edges, unit-testable) and let `layoutColumns` place it. Node and
  edge tone reuse the status tokens: `fail` = destructive, `warn` =
  status-conflict (conflicts are resolvable, not errors), `missing` = dashed
  status-missing, `info` = status-info. Only draw edges the API actually
  states — never infer lineage the backend doesn't report. The canvas is
  read-only, scroll-wheel zoom is off so the page still scrolls, and a node's
  `href` makes it a link.

## 7. Open items

- Type scale / spacing density — set by `ui-direction.md` (Geist, 24px page titles); revisit only
  if a specific screen (e.g. the reconciliation drill-in table) proves
  genuinely too dense once built.
- Role/permission admin screen — no design yet, not yet scheduled.
- Reconciliation view's field list — blocked on PRD open questions (§3).
- Frontend architecture follow-ups (server-state caching, transport errors,
  editor/explorer decomposition) — proposed in [`../frontend/`](../frontend/audit.md),
  not yet approved.
