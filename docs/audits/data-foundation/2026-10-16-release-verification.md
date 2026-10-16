# Release packaging verification — 16 October 2026

Public preparation branch: `codex/data-correctness-main`. Application commit `6ec450a` is assembled
from main `54af254` without unpublished production audit exports/history. The full local investigative
chain and live aggregate evidence remain in separate worktrees. Follow-up documentation changes do
not alter the application source. No production service/database mutation was performed.

## Executed checks

- Backend: `spotlessApply test spotlessCheck build` passed locally in 3m 37s: 1,615 tests across
  350 suites, zero failures/errors, two skipped live LLM evaluations. Tests ran in the preparation
  checkout using disposable PostgreSQL; application source was byte-compared with the public tree.
  The first full run found an old test expecting populated daily sales to remain untouched; its
  expectation was corrected to require stale resolved rows to disappear. The final full run passed.
- Release preflight: four standard-library Python tests passed in the public tree. The live V82 gate
  was exercised read-only; detailed evidence is operator-local. Four synthetic gate cases cover
  checksum normalization, altered/foreign/failed/missing history and multi-section CSV output.
- Audit harness: eight standard-library Python tests passed in the public tree. Incidental heuristic
  lineage regeneration was restored to the reviewed source inventory because mappings did not change.
- The Compose overlays were rendered with a copied fixture configuration, explicit non-production
  project name, `/dev/null` env file and dummy credentials. Replay/snapshot and maintenance switches
  were checked false. No real environment file or production Compose operation was used.
- Frontend: `bun run typecheck`, `bun run lint`, 624 Vitest tests across 106 files and `bun run build`
  passed in the public checkout, generating 28 routes. Existing Vite/deprecation warnings remain.
  Existing dependency caches were reused through a temporary symlink, then unlinked; no host
  dependencies were installed. No browser end-to-end session or Python/model/PDF fallback evaluation ran.
- Backend review image built successfully as `goldys-review-backend:6ec450a`, without running it or
  changing production tags. Frontend review image `goldys-review-frontend:6ec450a` also built
  successfully; neither review image was started or published.
- `git diff --check` passed. A bounded private-key/token-pattern scan found no candidate files.
  This is not a comprehensive secret/security audit. Unpublished data-foundation production exports are absent from the new
  commit tree; no local investigative commits are ancestors of this branch. Public summaries contain
  no live probe exports.

## Static review limits

Ripwire's committed comparison `54af254..6ec450a` is **not** a clean quality gate: 633 major findings
remain. The earlier working-tree report had 702 because short-horizon churn is unavailable in the
committed ref comparison. The reports identify substantial duplication/helper/size debt across the
accumulated foundation integration and repairs. They are not suppressed by blanket acknowledgements.
The two existing complexity increases are PDF ambiguity handling (`enrich`, 15→16) and widget
validation (`validate`, 13→18). These paths retain explicit safeguards and executable regressions;
no broad architecture refactor was introduced merely to clear static scores.

The PR-context pass covers the full accumulated branch. The startup `run` edit check reports an
unchanged signature and no bound callers; Spring/runtime injection and direct integration tests,
not that graph result, verify constructor/runner reach. Static navigation cannot certify runtime
correctness, source completeness, source-policy meaning or production fitness.

## Outstanding gates

Remote PR/CI has not run at packaging time. Publishing to the public GitHub repository requires
explicit destination/payload approval after automatic review blocked the initial push/PR attempt.
No push, PR, merge, production backup/restore rehearsal, ingestion, replay, migration, restart or
deployment occurred. CI/backend/frontend/image checks must pass at the published head before merge.

The [operator runbook](../../operations/data-correctness-release-2026-10-16.md) is the exact future
merge/deployment/rollback procedure. Rehearse a protected quiesced backup and rerun release preflight
before production migration. Legacy relationship/source repairs need separate evidence/approval.
The intermittent pre-existing OpenTable close/tick race is recorded in previous reports; it was not
changed by this release and did not fail the final backend packaging gate.
