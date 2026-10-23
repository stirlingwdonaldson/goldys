# Outstanding audit implementation plan

Branch: `codex/outstanding-audit-fixes`, baseline `8d6fa0a`, worktree
`/tmp/goldys-outstanding-audit-fixes`. User authorized the nine slices in chat.

- [x] Isolate work from the production checkout and inspect current main/open PRs.
- [x] Reconcile audit status and later user decisions without promoting proposed contracts.
- [x] Carry recipe stock-unit/usable/wastage/broken-link qualifications end to end; additive V100.
- [x] Apply shared calendar coverage to sale-item/deleted-sale metrics, preserving null operands.
- [x] Add shared runtime widget/dashboard/draft/provenance contracts at read/write/SSE boundaries.
- [x] Replace legacy read hook consumers with scoped React Query and transport signals.
- [x] Complete mutation invalidation and editor action/data-loss regression checks.
- [x] Add chat cancellation, generation fencing, CRLF framing and reader cleanup.
- [x] Make explorer layer/entity/applied-filter/pagination state shareable in URLs.
- [x] Restrict telemetry query metadata; retain safe correlation tags.
- [x] Complete browser/isolated-fixture lifecycle checks and request-count measurements.
- [x] Run full backend/frontend gates, diff checks, Ripwire quality/contract review.
- [x] Record actual results and remaining limits. Changes remain uncommitted; no publishing/deployment.

## Verification so far

Initial backend compile passed. First focused backend run exposed the old seven-column recipe-tool
assertion; updated to include the four new qualifications. Initial frontend integration run exposed
legacy hook mocks and old permissive parsing expectations. Focused follow-up passed product,
forecast and explorer suites; editor restore test needed explicit confirmation after the new guard.
These intermediate results are not the final gates.


## Final executed verification

- Backend `./gradlew test spotlessCheck --offline`: passed in 3m 57s; 1,639 tests, zero
  failures/errors, two skipped live evaluations. Real disposable PostgreSQL/Testcontainers;
  no production database. Includes V100 schema validation and the canonical→resolved recipe
  qualification regression with repeated source detail ids.
- Frontend sequential final gate: `bun run typecheck`, `bun run lint`, `bun run test`,
  `bun run build` all passed; 638 tests across 111 files and 28 built routes. A preliminary
  overlapping build/typecheck attempt raced on generated `.next/types`; both task builders
  were terminated, then the checks were rerun sequentially. The passing results are from that rerun.
- Production-build Playwright suite: four passed. Covers edits during save/dirty exit,
  Apply plus browser history, shared shell/page reads, and cancellation on closing chat.
  All API routes were fulfilled by isolated fixtures. No real account, connector, LLM or
  production server was contacted. Shared connector status and dashboard summary each issued
  one request; applying a raw filter added one request, typing it added none. These are bounded
  browser-fixture counts, not production performance measurements.
- `git diff --check`: passed. No Deputy, production Compose or environment files changed.
- Ripwire `streamChat` edit check: signature unchanged, one detected caller, zero incompatible
  calls. The capped PR-context report is structural guidance; it cannot prove framework reach.
- Ripwire quality delta is **not clean**: 107 gating heuristics remain (54 short-horizon churn,
  40 duplication, three complexity, three dead-code, three helper-clone, three verbosity,
  one constructor-arity). The remaining component complexity scores are DashboardEditor
  51→68, RawSection 16→23, RuleEditor 11→16. Mutation function references are incorrectly
  flagged as unused demo methods; executable UI tests exercise them. Other warnings include
  the expanded JPA projection constructor and repeated typed contract/fixture mappings.
  No blanket acknowledgements were added, and no clean merge-safety claim is made. Changes
  remain uncommitted for review. This task did not push, open a PR, run remote CI or deploy.

## Remaining limits

Live queries are disabled until the profile is authenticated, including the demo-build error
fallback. Mode, authentication status and session-verification generation key the query scope;
this also clears caches if React batches the intermediate loading state. Standalone fixtures can
read demo data without an auth provider; live reads require verified authentication.

No production acceptance, source-completeness assertion, source replay, historical repair,
new business-policy adoption, new grants, Deputy work or deferred capabilities. The navigation
protection covers explicit editor exit, link clicks and document unload; browser Back within
an SPA and concurrent edits from another browser have no new server-side guarantee. Browser
checks are local; CI continues to run the existing unit/type/lint/build gates. The original
production checkout and its untracked requirements files were preserved. A working copy of the
requirements package was kept untracked here, with a reconciliation note added to OPEN-DECISIONS.

## Main integration and conflict resolution

Merged main at `23abaeee` (PRs #208 and #209) into PR #210. Kept the receipt-style sale
summary, deleted the superseded relationship view, and migrated the summary's removed
`useApiData` dependency to React Query with cancellation of all three list reads.
The new unmount regression passed. Combined validation: backend `test spotlessCheck --offline`
passed (1,645 tests, zero failures/errors, two skipped live evaluations); frontend typecheck,
lint, all 643 tests and production build passed; all four fixture-backed browser checks passed.
No unresolved index entries or references to the deleted hook/view remain. The scoped Ripwire
comparison against the pre-merge HEAD reports inherited sale-summary complexity/duplication
heuristics from #208; these are not a clean quality result and were not suppressed or refactored
as part of this conflict resolution. No production database access or deployment was performed.
