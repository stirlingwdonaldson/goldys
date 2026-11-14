# Dashboard editor UX prototype

Throwaway, synthetic-data-only exploration on branch `codex/prototype-dashboard-editor`.
Question: which editing workflow best suits an owner or manager assembling a weekly view?
No direction has been selected. This code is not intended for main.

From this checkout's `frontend` directory:

```sh
bun install --frozen-lockfile
bun run prototype:dashboard
```

Open <http://localhost:3126/prototype/dashboards?variant=A>.

| Direction | Main interaction | Tradeoff |
| --- | --- | --- |
| A — Canvas first | Configure and resize cards in the dashboard itself | Immediate layout feedback; more visible controls |
| B — Report outline | Arrange sections beside a single-column report | Clear reading order; less layout freedom |
| C — Guided setup | Context → content → review, alongside a preview | Fewer decisions at once; extra steps |

The floating arrows and keyboard Left/Right cycle `?variant=A`, `B`, `C`.
Arrow keys retain their normal behaviour inside fields, tabs, menus and sheets.
All variants share the draft so comparisons use identical content. Widget configuration,
settings and the library open in the existing right-hand Sheet primitive, with URL state.
Browser Back and Escape close a sheet. Sidebar items are fixture context only.

## Try this comparison

1. Rename the dashboard to “Monday owner review”.
2. Add Invoice purchases; configure Gross sales as a trend.
3. Move Invoice purchases before Booked covers. Duplicate or remove a widget; try Undo.
4. Compare the canvas and outline. Canvas widths are intentionally ignored by the outline.
5. Switch the date period and previous-week comparison; toggle Preview.
6. Switch View as to Manager. Wage figures must become an explicit permission notice.
7. Save the draft. It stays in memory; reload resets everything.

The 26 October–1 November 2026 fixtures include $92,740 in gross sales, 1,248
booked covers, 28% labour cost / sales, one $310.20 sales-source difference and
missing closing stock. Booked covers remain an attendance proxy; invoice purchases
remain distinct from consumed cost. The alternative week uses an illustrative
92% scale for non-percentage values, not operational calculations.
Manager restrictions illustrate a viewer with no wage-data grant; they do not
settle the project's open role mapping. Sharing a dashboard grants no data access.

Expand “Prototype state” to inspect the complete draft, viewer and mode.
The prototype route returns 404 and the switcher is hidden in production builds.
It uses a fixture shell outside the authenticated app route group to avoid session,
backend and persistence dependencies; production routes and auth are untouched.

## Evidence and limits

Visual basis: [DESIGN.md](../../../../DESIGN.md),
[UI direction](../../../../docs/design/ui-direction.md) and
[design system](../../../../docs/design/design-system.md).
Existing behaviour reference: [dashboard-editor.tsx](../dashboard-editor.tsx).

No real API calls, data grants, persistence, drag-and-drop, revision history or
live query execution. Moving uses keyboard-accessible buttons. Undo restores a
prior in-memory draft. No user research verdict yet; compare the three directions
before choosing an implementation. A selected design needs production implementation
and acceptance checks, not direct promotion of this prototype.

## Local verification

Typecheck, lint and the final production build passed. All 137 frontend test files / 745 tests passed. The prototype URL returned HTTP 404 in a local production-mode server.
A headless Chromium walkthrough checked all three directions, editing actions,
viewer restrictions, period changes, in-memory save/reload, URL navigation, Back,
Escape and arrow keys. No page overflow at 900px or 390px; no API requests or page errors.
The source palette registry is included in Tailwind scanning so existing SourceTile
classes render with the approved colours. No backend, CI or real-user acceptance
checks were run; no changes were published.
