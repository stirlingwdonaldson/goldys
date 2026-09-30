# Goldy's Phase 2 UI Design

**Status:** In review
**Date:** 2026-09-30
**Scope:** UI/interaction design for the Phase 2 surfaces the PRD already commits
to. No implementation; design only, staying inside the existing architectural
invariants.

## 1. Objective

Define the user-facing surfaces for the four Phase 2 modules — **Resolution
rules** (Req 8), **Conversational BI** (Req 9), **Smart Exporter** (Req 10),
and **Automation Hub** (Req 12) — and where they live in the application shell,
so each surface is designed once against a shared mental model rather than
ad hoc when its module is built.

This doc deepens `docs/prd.md` Requirements 8–12 into concrete screens and
interaction patterns. It does not change scope, invariants, or sequencing; the
PRD remains authoritative on requirements and `docs/system-context.md` on
architecture.

## 2. Sources of Truth and Baseline

- Requirements/scope: `docs/prd.md` (Requirements 8–12, Non-Goals).
- Architecture invariants: `docs/system-context.md`; AI tool boundary:
  `docs/contracts/ai-tool-boundary.md`; widget schema:
  `docs/contracts/widget-spec.schema.json` (v1: `stat`, `table`, `line-chart`,
  `bar-chart`).
- Visual/interaction treatment: `docs/design-system.md`.

Baseline shell (from the frontend management IA work, `docs/superpowers/specs/
2026-09-29-frontend-management-ia-design.md`): sidebar groups **Business**
(Dashboard, Sales, Staff & Labor, Reservations, Kitchen, Recipes) and **Data**
(Reconciliation, Data health), Settings in the footer. Diagnostics already live
on Data health, not the Dashboard.

Binding invariants carried into every surface below:

- The AI assistant answers only through the fixed, enum-validated tool set;
  never freeform SQL or free-text filters (`ai-tool-boundary.md`).
- The assistant emits JSON widget specs, never executable frontend code.
- No AI/heuristic auto-resolution of conflicts — resolution rules are
  staff-authored (`prd.md` Non-Goals).
- Every report/export/alert reads **resolved views only**, never raw or
  canonical data.
- Write-back to any connected source is permanently forbidden; Automation Hub
  is outbound notifications/exports only.
- Permission is the department × seniority model; denied = explicit
  "not permitted", never a partial/filtered result.

## 3. Scope

### In scope

- Information-architecture placement of the four surfaces.
- Screen/interaction design for: the resolution-rule list + editor + recompute
  status + audit; the Conversational BI drawer and answer anatomy; the Smart
  Exporter one-click export, wizard, and history; the Automation Hub
  trigger/action builder.
- Cross-cutting patterns: provenance trace, "as of" timestamps, saved reports,
  and permission-denied behavior for AI answers and exports.

### Out of scope

- Backend implementation, data model, tool implementations, or embedding/vector
  infrastructure (net-new capabilities such as RAG/vector search over
  unstructured content are explicitly not part of this doc — the assistant
  operates over resolved structured data via fixed tools).
- New canonical entity types (inventory, recipes) — those are a separate
  connector/data-scope decision, not a UI one.
- Tenzo ingestion, write-back to sources, or per-user permission exceptions.

## 4. Information Architecture

Four changes to the shell, in build order:

| Change | Where | Notes |
|---|---|---|
| **Resolution rules** page | Data group, sibling of Reconciliation | Reconciliation = triage; Resolution rules = standing config |
| **Exports** page | Business group | Wizard + export history |
| **Saved reports** section | Dashboard, below the KPI grid | Pinned Conversational-BI widgets |
| **Ask Goldy's** | Header, global drawer | Not a nav item; reachable from anywhere |
| **Automation** page | Future (own page/group) | Phase 2 P2, outbound-only |

Resulting groups:

- **Business:** Dashboard, Sales, Staff & Labor, Reservations, Kitchen, Recipes,
  Exports.
- **Data:** Reconciliation, Resolution rules, Data health.
- **Automation** (future): triggers and actions.
- Settings in the footer (unchanged).

## 5. Resolution rules (Req 8)

The foundational surface: Conversational BI and Smart Exporter read resolved
views, which these rules produce.

### 5.1 Rule list (main view)

Grouped by entity → field, each row shows its effect and precedence:

```
Sales
  quantity_sold        Cooking the Books wins      (priority: CTB > Lightspeed)
  net_amount           unresolved                  ← no rule → flagged, never auto-picked
Shifts
  hours_worked         Deputy wins                 (priority: Deputy > Lightspeed)
```

A field with **no rule** renders an explicit `unresolved` badge — the
documented default is "flag as unresolved", never a silent or arbitrary pick
(Req 8 acceptance criterion).

### 5.2 Rule editor (modal)

1. Choose a **field** — only fields the current department × seniority permits
   (a BOH manager authors BOH fields only).
2. Choose a **strategy**:
   - **Priority by source** — drag to order sources.
   - **Manual override** — always ask a human (the Phase 1 behavior, kept as a
     valid rule).
   - **Custom logic** — a small declarative set only: **flag it**, **pick
     highest**, **pick lowest**, **pick newest**. No scripting, no expression
     builder (decided — keeps the surface calm and auditable).
3. **Recompute history?** toggle — apply going forward only, or replay against
   history (Req 8's bitemporal guarantee; recompute is reproducible and never
   mutates canonical history).

### 5.3 Recompute status

A persistent banner reflecting the edge-case user story: "Rules changed —
recomputing resolved views…" until "Last recompute 12 min ago". A manager must
never read a partially-recomputed resolved view.

### 5.4 Audit

A change history (who / what / when) per rule, so staff-authored rules remain
answerable.

## 6. Conversational BI — "Ask Goldy's" (Req 9)

The flagship surface.

### 6.1 Surface

A global **drawer** (shadcn `Sheet`) opened from an "Ask" button in the header,
also bound to `⌘K` / `Ctrl+K`. A drawer rather than a dedicated page keeps the
reporting question in context with whatever screen the manager is on.

### 6.2 Conversation flow

1. **Empty state** — greeting + role-aware suggestion chips (e.g. "Labor % vs
   sales last week", "Top sellers this month", "No-shows this weekend").
2. **Ask** — free text.
3. **Clarify** — one focused question when ambiguous (missing period/metric),
   never a wall of options.
4. **Working** — a transparent status line naming the tools being run:
   `Running get_sales_by_period · get_labor_cost_variance over resolved data…`.
5. **Answer** — summary + widgets + trace + actions (see 6.3).
6. **Denied** — out-of-scope question → explicit "You don't have access to
   labor-cost data" (Req 9 criterion), never a partial answer.

### 6.3 Answer anatomy

Every answer is one block with four parts:

- **Summary** — 1–2 sentences.
- **Widgets** — rendered from JSON spec: `stat` → `StatCard`, `table` → a table,
  `line-chart`/`bar-chart` → Recharts (already a frontend dependency).
- **"How I got this"** — a collapsible provenance trace naming the tools run and
  their sources ("`get_labor_cost_variance` (Deputy, resolved) ·
  `get_sales_by_period` (Lightspeed + CTB, resolved)"). This is the trust layer:
  a manager can see the number came from resolved data, not improvised.
- **"As of" timestamp** — because a reconciled number can change after a
  recompute.
- **Actions** — "Pin to dashboard" and "Export" (see §7).

### 6.4 Saved reports (pin to dashboard)

Pinning a widget saves it to the Dashboard's **Saved reports** section. The
Dashboard therefore becomes: needs-a-decision band → KPI grid → saved reports.

### 6.5 Unresolved routing

If a question touches fields with open conflicts, the assistant says "N numbers
are unresolved — reconcile them first" and deep-links to `/reconciliation`. The
assistant never auto-resolves (respects the non-goal); it routes.

### 6.6 History

The drawer keeps a list of recent conversations, each re-openable.

## 7. Smart Exporter (Req 10)

Two entry points, one shared pipeline (Conversational BI's tool-calling layer):

1. **One-click export** — every widget/report has an "Export" action that
   downloads that widget's data as a formatted `.xlsx`.
2. **Exports page** — a wizard for bespoke pulls: pick a saved report or data
   scope → choose columns → preview → download; plus an export **history**
   (who / what / when).

Non-negotiables made visible:

- **Role-gating at the data level** — a field the user can't see is *omitted
  from the file*, never merely hidden (Req 10 criterion). The wizard shows a
  note: "This export excludes wage data (Owner-only)."
- **Presentation-ready** — formatted headers, no raw JSON or internal IDs; the
  preview step makes this concrete before download.
- Reuses the tool-calling layer (no separate query path).

## 8. Automation Hub (Req 12, future)

A dedicated page with a **trigger → action** list and builder:

```
When  labor cost % this week  >  40%        →  post to #managers (Slack)
When  ingestion run           fails         →  email export to owner
When  a source connector      has no new data →  (muted, no action)
```

- **Triggers** evaluate against resolved-view data only (never raw/canonical).
- **Actions** are notifications/exports only (Slack, email), each with its own
  auth (e.g. a Slack bot token). The UI makes the outbound-only constraint
  explicit: this is the only write path, never a write-back to a source system.
- Per-rule status: "last fired 2h ago", "disabled", "auth missing".

## 9. Cross-cutting patterns

- **Provenance everywhere**: AI answers carry the trace (§6.3); exports carry a
  column/note of their source; rules carry an audit log (§5.4).
- **"As of" timestamps** on any number derived from resolved data.
- **Permission is consistent**: one model, enforced identically in the
  reconciliation UI, the rule editor, AI tool calls, and exports — denied is
  always explicit, never partial.
- **Calm/trustworthy**: reuse shadcn defaults + semantic status colors;
  exception-first; never fabricate data.

## 10. Decisions recorded

- Conversational BI is a **global drawer**, not a page.
- Widgets are **pin-able** to the Dashboard (saved reports), not static-only.
- The **"How I got this" trace** is a first-class part of every AI answer.
- Resolution rules is a **sibling page** of Reconciliation, not tabs.
- Rule custom logic is **declarative only** (flag / highest / lowest / newest).
- Smart Exporter is **one-click + wizard + history**, role-gated at data level.
- Automation Hub is **outbound-only** and explicitly out of scope until Phase 2
  P2.

## 11. Open questions (not blocking the design)

- Whether saved reports / export history / automation config persist as new
  backend concepts (they will need storage, but that is a backend concern
  deferred to implementation planning).
- The exact suggestion-chip set per (department, seniority) — needs the
  field-to-role mapping (PRD Open Questions) before it can be enumerated.
- The Automation Hub's notification channels (Slack confirmed? email?) — a
  stakeholder decision, not a design one.

## 12. Sequencing

Build order is risk-first and respects the PRD's dependency: **Resolution
rules (Req 8) first** (it produces the resolved views the others read), then
**Conversational BI (Req 9)**, then **Smart Exporter (Req 10)** reusing its
tool layer, then **Automation Hub (Req 12)** last, as P2. Each surface is
independently testable and lands behind the existing permission model.
