# Validation — 3 November 2026

Base `f15526b5`; isolated branch `codex/product-classification`.

- Repository refreshed with `git fetch --prune`, status, origin/main log and PR list. Shared
  production checkout not switched or reset; untracked `docs/requirements/` left untouched.
- Read-only production probes: `audit.sql`, `coverage.sql`, `sample.sql`, `final-coverage.sql`.
  Results are retained beside the queries. No ingestion, source write or production classifier run.
- Owner-approved bounded Jev evaluation: 24 real cases, three pre-model identity holds, 21 calls,
  returned model `jev-1.14.0`; 18/21 agreement with provisional labels. Responses, inputs and draft
  criteria are saved. The original disagreements remain in the replay regression test.
- Focused Java classification tests passed. Targeted PostgreSQL 16 migration/reporting tests passed:
  raw provenance, item quantity/cost/unit, unknown reasons, effective dates, recipe versus name
  identity, exact-line precedence and durable run snapshots.
- Frontend preview/read-error tests passed again after JSX formatting: **15 tests**, plus
  repeated typecheck/lint.
- Frontend `bun install --frozen-lockfile`, `bun run typecheck`, `bun run lint`,
  `TMPDIR=/tmp bun run test`, `bun run build` passed: **135 test files / 735 tests**. The original
  frontend test attempt could not create its default temporary directory under the sandbox;
  explicit `/tmp` fixed the environment without changing project configuration.
- First full backend `spotlessApply test spotlessCheck`: 1,839 tests, one failure, five skipped.
  Failure: `ConversationalEvalTest` cleanup `TRUNCATE` deadlocked with an AccessShare reader on
  unrelated daily-sales/reservation tables. No classification assertion failed. The full gate
  was repeated after final source-context changes and passed.
- **Final completed-patch backend gate:** `./gradlew test spotlessCheck` passed in 5m 50s:
  **1840 tests, 0 failures, 0 errors, 5 skipped**, including the final POS
  multiple-ID/site guard and source-evidence SQL execution test. Spotless passed. `compileJava`
  also passed separately. The earlier deadlock was not observed on either succeeding full run.

## Structural analysis

Applied ripwire orientation, impact, quality-bar and change-check workflows. This tool build's
HEAD-tree fallback failed in the worktree. For a real baseline, used a clean scratch clone at
f15526b5, generated a quality floor, then compared a second scratch clone with the actual patch.
No baseline, suppression or generated ripwire cache is committed.

Quality-delta reports gating findings, including class/method growth in the extractor, Jev
validation and run provenance, plus inferred dead-code/duplication changes involving unrelated
InventoryController and thin record/query methods. The test gate lists 24 tests and an inferred
98-symbol untested radius; generic names such as `Line`, framework dispatch and interface wiring
confound that graph (2,381 ambiguous edges / 4,277 unresolved / 141 unindexed files). These are
static warnings, not a passed merge-safety gate. Existing shapes and trust-boundary validation
were retained under Ponytail; no broad refactor or blanket acknowledgement was added.

Runtime gates and explicit PostgreSQL/permission/preview tests provide separate evidence. No
claim is made that every inferred path has coverage. The edit-check selector for the new
`candidates` method was unresolved by this ripwire build; the exact `JevProductClassifier.classify`
selector resolved but reported `no-baseline`, so it cannot certify a contract comparison. Java
compilation and runtime adapter/caller tests provide that verification instead.

## Release boundary

No push, PR, CI watch, merge, deployment, production migration, category/hierarchy change,
reporting mapping or production rerun has been performed. Those require separate approval.
Browser end-to-end checks and production-scale preview performance were not run. The isolated
live Jev evaluation used bounded real evidence and draft criteria, not an end-to-end production
run of the changed pipeline or certification of all labels. No unrelated labour/bookkeeping work.
