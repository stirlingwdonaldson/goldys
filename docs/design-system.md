# Frontend Design System

**Status:** Draft — principles, screen inventory, and visual direction are
settled. Revisit once a couple of real screens (dashboard, reconciliation
view) exist to react to.

**Non-normative for architecture** — this doc governs visual/interaction
decisions only. It never overrides `system-context.md` or `prd.md`; where a
screen's data shape is still unresolved (see "Blocked screens" below), this
doc describes layout intent only, not final field lists.

---

## 1. Design principles

- **Use shadcn's out-of-the-box look, unmodified.** Typography, spacing
  scale, border radius, and the neutral `slate` palette should match what
  you see scrolling shadcn's own example sites and blocks — not a custom
  re-tune. Don't introduce a custom font, a different spacing scale, or
  re-tuned neutrals. The only addition is semantic status colors (§5),
  since shadcn's default theme doesn't ship any and this product needs them
  on day one.
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
| Dashboard / KPI overview | Goals 1–2, Success Metrics | Card/stat-based, not a table. Surfaces ingestion completeness and conflict counts, not just resolved numbers |
| Reconciliation view | Requirement 5 | Exception-first summary + drill-in comparison. See §6 |
| Connector status / ingestion health | Requirement 6 | Failure states must be visually distinct from "no new data" |
| Permission-denied state | Requirement 3 | Explicit "not permitted" — never a silently filtered or partial-looking view |
| Role/permission admin (future) | Requirement 3 | Not scheduled yet; deferred design |
| Conversational BI widgets | Requirement 9 (Phase 2) | Deferred — depends on the JSON widget schema landing first |
| Smart Exporter | Requirement 10 (Phase 2) | Deferred — reuses Conversational BI's rendering, no separate design needed yet |

### Blocked screens

The **reconciliation view's** exact field layout can't be fully finalized
until the PRD's two open questions resolve:
- Entity-matching strategy (what identifies "the same event" across
  sources) — affects how records are grouped for comparison.
- Field-to-role permission mapping — affects which fields render per role.

Design the reconciliation view's *pattern* (§6) now; don't hard-code a
final field list against a guess.

## 4. Navigation & layout shell

**Implemented in code** (not just documented — see `frontend/components/`):
adopted shadcn's `sidebar-07` block as the app shell, sourced from the real
shadcn-ui registry (not hand-recreated from memory) and adapted to this
repo's aliases and this product's nav structure:

- `components/ui/sidebar.tsx` plus its dependencies (`avatar`, `breadcrumb`,
  `button`, `collapsible`, `dropdown-menu`, `input`, `separator`, `sheet`,
  `skeleton`, `tooltip`) and `hooks/use-mobile.tsx` — copied verbatim from
  the block's primitives.
- `components/app-sidebar.tsx` — seeded with Goldy's nav (Dashboard,
  Reconciliation, Connectors, Settings) instead of the block's demo data.
- `components/nav-main.tsx` — simplified from the original: sidebar-07's
  demo data is a nested Playground/Models/Documentation hierarchy, but this
  product's screens are a flat list, so the collapsible sub-item behavior
  was dropped rather than carried as dead code.
- `components/team-switcher.tsx` — sidebar-07's version assumes
  multi-workspace use (a dropdown to switch teams, an "Add team" action).
  Goldy's is a single pub, single workspace, so this is a static header
  instead of a functional switcher. Revisit properly if multi-venue ever
  becomes a real requirement — don't just re-enable the dropdown without
  redesigning what it should actually do.
- `components/nav-user.tsx` — dropped the "Upgrade to Pro" and "Billing"
  items from the block's SaaS-demo account menu; neither fits a
  single-tenant internal tool. Avatar fallback derives initials from the
  signed-in user's name instead of a hardcoded placeholder.
- `app/page.tsx` — wired into `SidebarProvider` / `SidebarInset` with a
  header (`SidebarTrigger` + breadcrumb), replacing the flat placeholder
  page. Its body is now a dashboard landing page built to §3/§6's shape
  (stat cards, charts, connector status, exception list), rendering mock
  data from `lib/mock-dashboard-data.ts`. It is a layout preview, not a
  working screen: nothing on it is wired to the backend, because real KPI and
  reconciliation data is blocked on the PRD's open questions.

`bun run build` and `bun run lint` both pass, and `bun run lint` now runs
plain ESLint via `eslint.config.mjs` rather than the deprecated `next lint`
(which, with no config committed, dropped into an interactive setup prompt).
The repo stays Bun-only: no npm/yarn lockfile is committed.

## 5. Visual tokens


**Base theme: shadcn defaults, unmodified.** Keep the existing scaffold as
the source of truth — `components.json` (`baseColor: slate`, style
`default`), `app/globals.css`'s CSS variables, `tailwind.config.ts`'s
mapping, the default `--radius: 0.5rem`, and shadcn's default font stack.
Don't hand-tune hues, spacing, or type scale — if something looks off,
prefer to check whether it's actually a component being used incorrectly
before reaching for a custom token.

**One real gap: semantic status colors.** Default shadcn ships
`primary`/`secondary`/`muted`/`accent`/`destructive` but nothing for
"conflict," "resolved," or "no data" — states this product needs
immediately. Proposed additions to `app/globals.css` under `:root` (mirror
in `.dark`, dormant):

```css
--status-success: 142 71% 35%;      /* resolved / matches across sources */
--status-success-foreground: 0 0% 100%;
--status-warning: 38 92% 50%;       /* field-level conflict, needs a decision */
--status-warning-foreground: 222.2 84% 4.9%;
--status-missing: 215 16% 65%;      /* "no data from source X" */
--status-missing-foreground: 0 0% 100%;
```

Wire into `tailwind.config.ts`'s `theme.extend.colors` the same way
`destructive`/`muted` are wired, so they're usable as `bg-status-warning`,
`text-status-missing`, etc.

`--destructive` stays reserved for genuine system failures (a crashed
connector), not conflicts — a conflict is an expected, resolvable state,
not an error. **Implemented** in `app/globals.css` (both `:root` and the
dormant `.dark` block, with the warning/success hues lifted slightly for dark
contrast) and mapped into `tailwind.config.ts` under `theme.extend.colors.status`,
so they are usable as `bg-status-warning`, `text-status-missing`, etc. The
connector-status and exceptions components already consume them.

The same commit added shadcn's standard `--chart-1..5` tokens and `chart.*`
mapping, which `components/ui/chart.tsx` expects.

**Sidebar tokens — implemented, shadcn defaults.** Pulled from the real
shadcn-ui registry (`ui/sidebar.tsx`'s own `cssVars`), not hand-guessed —
`app/globals.css` and `tailwind.config.ts` now carry the standard
`--sidebar-*` variables and the `sidebar.*` Tailwind color mapping.
`--sidebar-background: 0 0% 98%` **is** `#FAFAFA` — the requested sidebar
color is shadcn's own unmodified default, not a custom override:

```css
--sidebar-background: 0 0% 98%;
--sidebar-foreground: 240 5.3% 26.1%;
--sidebar-primary: 240 5.9% 10%;
--sidebar-primary-foreground: 0 0% 98%;
--sidebar-accent: 240 4.8% 95.9%;
--sidebar-accent-foreground: 240 5.9% 10%;
--sidebar-border: 220 13% 91%;
--sidebar-ring: 217.2 91.2% 59.8%;
```

(Dark-mode sidebar variants are also in place, dormant, matching this
doc's "light mode only, for now" stance elsewhere.)

**Card radius — deliberately bumped, cards only.** Real shadcn `Card`
(`components/ui/card.tsx`) ships `rounded-lg` (i.e. `var(--radius)`,
currently 8px) — not `rounded-xl` as this doc's first draft assumed.
Rather than raising the global `--radius` token (which would also round
buttons/inputs more than intended), `Card`'s className was changed to
`rounded-xl` (12px) directly, so only cards pick up the extra rounding.
If a reason ever comes up to round buttons/inputs to match, do that
deliberately via `--radius`, not by copying this override elsewhere.

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

## 7. Open items

- Type scale / spacing density — using shadcn defaults as-is; revisit only
  if a specific screen (e.g. the reconciliation drill-in table) proves
  genuinely too dense once built.
- Role/permission admin screen — no design yet, not yet scheduled.
- Reconciliation view's field list — blocked on PRD open questions (§3).
- Reconciliation view and connector-status screen — the dashboard's
  exception list and connector panel are mock previews of these; neither is a
  real screen yet.
- `bun run build` and `bun run lint` both verified against the current tree.
  Re-check after any further dependency change.
