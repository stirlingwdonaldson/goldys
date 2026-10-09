# UI Direction

**Status:** Approved direction, implemented on `feature/ui-direction` (October 2026).
**Mockups:** [`ui-direction-mockups.html`](ui-direction-mockups.html). Open it in a
browser. It holds the four annotated screen mockups and the full heuristic audit.
**Scope:** Visual and interaction decisions only. This doc replaces §1's "shadcn
unmodified" principle and §5's tokens in [`../design-system.md`](../design-system.md).
Everything else in that doc still applies.

The goal is a warmer, more legible UI on the same shadcn/ui primitives, with
each change tied to one of Nielsen's ten usability heuristics (H1–H10).
References: Klaviyo (inset panel, big-number tiles, soft status tags), Apollo
(ink plus one bright accent, ⌘K), HoneyBook (flow canvas, tinted icon tiles).

## Six global moves

1. **Inset content panel.** The sidebar sits on a warm grey canvas
   (`--sidebar-background`) and each page sits in a raised white panel. Use
   `<Sidebar variant="inset">` and `<SidebarInset>`.
2. **Ink plus one gold.** `--primary` is near-black, used for the active nav
   item and most buttons. `--brand` (Goldy's gold, `Button variant="brand"`)
   is for **one** action per screen, the one that matters most.
3. **Soft status pills.** `Badge` variants `success`, `conflict`, `missing`,
   `info`, `failed`, `neutral` and `brand` use a tinted background with
   coloured text. Conflicts are orange (`--status-conflict`) so they never
   read as the gold brand colour.
4. **Source identity tiles.** `SourceTile` / `SourceLabel`
   (`components/sources/source-tile.tsx`) give each source one monogram and
   colour everywhere: Lightspeed (violet), Cooking the Books (teal),
   OpenTable (magenta), Deputy (blue). These colours mean identity only and
   are never used for state.
5. **Type.** Geist and Geist Mono via the `geist` package. Page titles are
   `text-2xl font-semibold tracking-tight`, KPI values are 27px, and every
   figure uses `tabular-nums`. Use `lib/format.ts` for en-AU currency and
   dates.
6. **Panels instead of page swaps.** Detail opens in a right-hand `Sheet`
   whose state lives in the URL, so Back and Esc close it and the link can
   be shared.

## Tokens

The source of truth is `frontend/app/globals.css`. Notable additions:

| Token | Use |
|---|---|
| `--brand` | Goldy's gold, the single primary action per screen |
| `--status-conflict` / `-soft` | An expected, resolvable disagreement. Replaces `--status-warning` |
| `--status-missing` / `-soft` | "No data from X" |
| `--status-success` / `-soft` | Resolved, healthy, verified |
| `--status-info` / `-soft` | Resolved by rule or manual decision, running |
| `--destructive` / `-soft` | Genuine system failures only, never conflicts |
| `--source-*` / `-soft` | Source identity tiles and chart series |

`--radius` is `0.75rem`. Light mode only during Phase 1.

**Charts:** green (`--chart-1`) is good or healthy and red (`--chart-2`) is
bad or failed. Sparklines, trend lines and "clean" bars are green, and
"failed" series are red. Never use ink-black series. Additional series fall
back to the source hues.

## Responsive rules

Nothing should wrap onto an awkward second line or overflow. When something
doesn't fit, collapse it:

- **Sidebar:** 14rem wide and collapsible to icons via the header button,
  the edge rail or ⌘B. The state is remembered in the `sidebar_state`
  cookie. It auto-collapses below 1024px and re-expands on widening if it
  was auto-collapsed. Below 768px it becomes the shadcn mobile sheet.
- **Header:** the breadcrumb drops the section crumb below `md` and
  truncates the page name. The freshness chip shows "3/4 healthy" below `xl`
  and the full sentence at `xl`, with the full text in its tooltip. Ask
  Goldy's is icon-only below `lg`.
- **Lists:** reconciliation rows put the source values on their own line
  below `lg` instead of wrapping inside a cell. Status pills stay short
  ("Missing data"), and the detail goes in the tooltip.
- **Banners:** the demo banner shortens to "Demo data" below `sm`.
- Text that can't shrink uses `whitespace-nowrap` plus `truncate` and a
  `title`, never a forced line break.

## Shared building blocks

| Need | Use |
|---|---|
| Page title row | `components/layout/page-header.tsx` (`title`, `description`, `status`, `actions`) |
| Where am I | Header `Breadcrumb` from `lib/nav.ts` (sidebar, breadcrumb and ⌘K share one nav model) |
| Is data current | `FreshnessChip` in the header, plus sidebar count and failure dot (`ShellStatusProvider`) |
| Jump anywhere | ⌘K `CommandPaletteProvider`. Ask Goldy's moved to ⌘J |
| KPI | `StatCard` with `delta` (coloured by good/bad, not direction), `spark`, `help` (HoverCard definition from `docs/metrics/catalog.md`) and `footer` |
| Feedback | `useToast()`, now backed by Sonner (`components/feedback/toast.tsx`) |
| Sub-views | `Tabs` (segmented) and `ToggleGroup`, never hand-rolled buttons |
| Errors | `ErrorState` (retry plus copy reference) and `Alert` with cause, impact and fix |
| Irreversible actions | Confirm in a `Dialog` that names the consequence |
| Lists of records | `components/data-table/data-table.tsx` (TanStack). Format dates with `formatDay`, money with `formatCurrency`, sources with `SourceLabel` in cell renderers |
| Lineage and flow | `components/flow/FlowCanvas`. Node tones reuse the status tokens (`warn` is `--status-conflict`) |

## Heuristic rules of thumb

- **H1:** Every page shows data freshness and the open-decision count without
  a visit to Data health. Async buttons show a pending state.
- **H2:** Use venue language and Australian spelling ("Labour", "Overview",
  "Custom dashboards"). Colour deltas by meaning.
- **H3:** Detail views are URL state. Overrides and other writes give a
  toast. Undo is offered only where the backend can reverse the write (see
  below).
- **H4:** Use one component per pattern and sentence case everywhere.
- **H5:** Overrides need a reason. The button names its effect ("Use
  Lightspeed value"). Tiles built on disputed days say so. Rule deletion is
  confirmed.
- **H6:** Source tiles appear everywhere. The sheet shows whether a
  resolution rule applies. The gap (Δ) between sources is shown in the list.
- **H7:** ⌘K, J/K/Enter/Esc in the reconciliation queue, and filters in the
  URL.
- **H8:** Placeholders (Kitchen, Recipes) sit in a collapsed "Coming soon"
  group. Change history is behind a button. Fields that agree are collapsed.
- **H9:** Failures state the cause, what is affected and the next step, with
  the action next to them.
- **H10:** Metric definitions are a hover away, and Ask Goldy's is in the
  header and ⌘K.

## Not yet done (needs backend or a decision)

- **Undo on overrides.** There is no endpoint to remove or supersede an
  override from the UI. When one exists, the save toast should take an
  `action: { label: "Undo" }` that writes a superseding override, which keeps
  bitemporal history intact.
- **30-day ingestion strip per source** on Data health. It needs per-source
  run history from the API. Don't fabricate it.
- **Resolution rules as a flow canvas** (mockup 4). This is a layout idea
  only, gated on the PRD's open entity-matching question.
- **Global period picker** ("Last 7 days vs prior"). It needs reporting
  endpoints that take a period.
