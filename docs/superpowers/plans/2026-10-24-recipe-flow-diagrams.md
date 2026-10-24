# Recipe diagrams implementation record

Branch: codex/recipe-flow-diagrams, based on c9218ca1 (#215).
Work is isolated from the production checkout.

1. Add explicit nullable folder identity to the resolved list contract; update existing
   constructors and demo data, with a source-fixture test.
2. Build pure folder and ingredient graph mappers using shared graph types/layouts.
   Cover duplicate names, empty folders, unknown identities, cyclic ancestry, collapsed
   counts, repeated source lines, signed quantities and missing costs.
3. Add the folder diagram to Recipes and the ingredient diagram to complete recipe
   composition. Preserve tables, source qualifications and existing URL context.
4. Reuse the shared canvas with keyboard activation and a per-view minimum zoom.
5. Verify frontend/backend gates and browser graph navigation at desktop/mobile widths.

## Verification

Frontend typecheck, lint, 682 tests across 118 files and production build passed.
The first full backend run completed 1688 tests with one failure and five skipped:
ConversationalEvalTest fixture cleanup deadlocked with a scheduled reservation query.
Its 11-test class passed immediately in isolation; the complete backend rerun then
passed (`./gradlew test spotlessCheck`), with five opt-in tests skipped.

Ripwire quality-delta/edit-check/test-gate were run. Reports remain non-clean:
new graph builder complexity, JSX callback reach/dead-code approximations, shared-canvas
churn/line growth and test-class size. Java compilation verifies constructor callers;
the static edit check falsely flags one overloaded RecipeCosting constructor call.
No claim of clean static-analysis gates is made.

No production API authentication, deployment, migrations or source writes are included.
No push or PR is included in this slice without a further request.

Chromium verification passed all 8 tests: keyboard folder expansion, nested recipe
node navigation, recipe-to-sub-recipe graph navigation, parent links, retained list
context and table containment at 1440px and 390px, plus existing taxonomy/shared-UI
regressions. APIs were intercepted locally; real recipe observations and demo data
provided rendering fixtures. No browser runtime errors were observed in recipe checks.
