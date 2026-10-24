# Recipes presentation implementation plan

Status: implemented locally on `codex/recipes-presentation`; not pushed or deployed.
Design: [Recipes presentation cleanup](../specs/2026-10-24-recipes-presentation-design.md).
Baseline inspected: origin/main `45534db6`, 24 October 2026.

## 1. Start safely and capture rendered baseline

- Refresh repository/PR state and create a task branch in an isolated worktree.
  Do not modify/switch the production checkout. Reconcile newer main changes before edits.
- Open local live-shaped and demo recipes. Capture list, #314, #401/#497, #377, #485,
  stock-export, empty/legacy and failed-section states. Use existing real sanitized
  fixtures for behavior checks; don't require new CTB collection or production writes.
- Inspect data-table consumers and raw `<table>` call sites. Record explicit wrapping,
  stacked cells and narrow-container behavior before changing the shared policy.

## 2. Enforce the shared table requirement

Files: `frontend/components/ui/table.tsx`, `frontend/app/globals.css` as needed,
`frontend/components/data-table/data-table.tsx` only if required for containment,
and wrapping/stacking overrides found in consumers, including raw widget tables.

- Establish a single no-wrap policy covering shared primitives and native table cells.
- Preserve local horizontal scroll; remove conflicting per-cell wraps and replace
  intentional stacked content with inline summaries/disclosures. Do not use a blanket
  truncation rule that hides numbers or actions.
- Add focused shared-table regression coverage; verify computed styles and actual line
  layout in a browser, since class-name assertions alone cannot prove no wrapping.
- Check Data Explorer, invoice/sales detail, query results, dashboard/report tables,
  forecasting, reconciliation, logs and my-work native table call sites.

## 3. Build the smallest recipe presentation mapping

Files: existing `frontend/components/recipes/` components and a local presentation
helper only where needed, plus focused unit tests.

- Compose existing typed composition data with optional section records. Group selected
  name/boolean pairs by exact source array index; don't parse arbitrary payloads or
  match on display names. Preserve original raw field lists in evidence.
- Map source statuses/issue codes to short consistent labels and explanations.
- Test selected/unchecked/null/empty/zero, repeated names, unknown fields, failed refresh
  with retained prior values, and hostile source strings rendered as plain text.

## 4. Replace the overlapping detail panels with one recipe view

Files: `frontend/components/recipes/recipe-details-panel.tsx`,
`recipe-composition-panel.tsx`, `frontend/app/(app)/recipes/[recipeId]/page.tsx`,
their existing tests; add a single composed view only if it simplifies ownership.

- Put header/yield, ingredients, method and costing in the design's reading order.
- Put direct stock and sub-recipe usage in readable rows, with ingredient metadata
  accessible on demand. Use explicit recipe links and existing reverse references.
- Keep typed costs and source/recomputed/POS distinctions; move technical diagnostics,
  field lists, repeated capture attempts and lookups into one evidence area.
- Keep full-detail fallback for legacy-only recipes without mixing flattened descendants
  into explicit composition or double counting them.
- Make optional sections compact without erasing uncertainty or failed/stale indicators.
- Keep row keys stable by source line IDs. Preserve cancellation, API permission errors,
  invalid IDs, unavailable children and direct URL behavior.

## 5. Simplify the recipe list and entry flow

Files: `frontend/app/(app)/recipes/page.tsx` and `recipes-page.test.tsx`.

- Apply the six default columns and one-line details badge.
- Add folder/status filters from existing list data; use existing toolbar conventions.
- Remove duplicate appended Ingredients flow and redundant empty financial summary cards.
- Preserve sorting/search/visibility/pagination and return-to-list filter/page state.
  Use URL state with existing patterns where available; do not change every table's
  state API to implement this one screen.
- Rename POS observation labels and keep historical dates/source caveats available.

## 6. Fixtures, verification and review

Files: `frontend/lib/api/demo-recipe-composition.ts`, `demo-recipe-details.ts`,
recipe fixtures in `frontend/lib/api/demo.ts`, relevant component/page tests and
shared table tests. Change only fixtures whose new presentation requires adjustment.

- Ensure demo covers direct ingredients, nested batches, adjustments, empty composition,
  dietary/zero-nutrition states, missing details and stale/failed observations.
- Focused checks: recipe mapping, list, detail, shared table, and real navigation.
- Browser checks at desktop and 390px: all-column no-wrap behavior, scrolling contained
  to tables, long names, keyboard/touch access to full values, source disclosure,
  direct child/parent navigation and Back preserving list state.
- Run frontend gate sequentially: `bun run typecheck && bun run lint && bun run test &&
  bun run build`. Run Ripwire quality/change checks for edited symbols and inspect
  their limitations. If backend changes become necessary, read the applicable repo
  skill, keep semantic/permission boundaries, update live+demo contracts and run the
  backend gate; no backend change is currently planned.
- Review final diff against the acceptance cases. Report only checks actually run.
  Push/PR/CI/deploy wait for explicit authorization under repository policy.

## Out of scope and risks

No recipe editing, source writes, new ingestion, cost recalculation, dietary inference,
stock/supplier pages or new fuzzy entity matching. No dependency additions are expected.
Shared no-wrap behavior can make tables wider; browser coverage across consumers is
required. Data correctness and source evidence must survive the visual simplification.

Planning verification: source reads, real fixture inspection and bounded read-only
production SQL completed. Implementation uses a separate worktree based on `bf3ddd43`.

## Verification record

- Frontend type checking and ESLint passed.
- Final full frontend tests passed: 677 tests across 117 files, including the retained-page
  clamp regression case (`bun run test --maxWorkers=2`).
- Production build passed; Recipes list and dynamic detail routes compiled.
- Initial Chromium recipe/table checks passed at 1440px and 390px: explicit child and
  reverse parent links, source disclosure, supplier popover, preserved browser Back
  context, computed no-wrap styling, text-node line rectangles and local table scrolling.
- Browser checks use intercepted APIs and local fixtures; production auth and live REST
  were not exercised. No backend tests were needed for this frontend-only change.
- Ripwire quality-delta and test-gate were run. Existing component complexity regressions
  were removed by splitting recipe rendering into focused sections. Reports remain
  non-clean: JSX components reported as dead code, the retained legacy ingredient API
  no longer called by Recipes, rendering duplication, formatting-driven line growth,
  churn and broad untested call-graph reach. These are not claimed as clean gates.
- Initial verification needed scratch files on disk because the temporary filesystem
  quota prevented loading suites. Chromium needed a shorter temporary path to fit its
  Unix socket limit. Neither workaround changes project configuration.

No commit is made to main, no push/PR/CI run or production deployment is included.


Expanded Chromium verification passed 10 tests: recipe composition and reverse links,
no-wrap/contained scrolling on Recipes, Kitchen, Sales, Logs, raw data, invoice detail
and forecasting at desktop/mobile widths; existing query controls, incomplete-notice
preferences and taxonomy layout regressions also passed. Browser source APIs were mocked.
