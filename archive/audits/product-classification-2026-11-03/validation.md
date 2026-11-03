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


## PR #230 review fixes, based on 27a062e9

The review findings were reproduced in code and fixed without changing the design:

- Wastage now checks inventory.stock, inventory.cost and products.category READ before storage.
  A permanent test allows cost/category checks but denies stock and verifies the wastage query
  and mapping storage are never called; a positive test verifies all checks precede the query.
- Candidate text includes a name ahead of bounded evidence and retains original source names.
  A catalogue-only Porterhouse test has no sales, receipts or recipe links and inspects the
  actual classification request, not the map key.
- Manual exemplars include category ID, readable name and approved criteria. Missing category
  references and criteria are explicit states with nullable text. The permanent full-run regression
  captures the Jev HTTP request and the naming LLM's actual system prompt; it tests valid, deleted
  and missing-criteria references and verifies manual assignments are never saved over.
- Run polling uses a JPQL metadata constructor projection with PageRequest(0,25), not entity
  loading or LAZY basic fields. PostgreSQL SQL capture tests seed 30 runs, verify the newest 25,
  assert the SQL has a database limit and no snapshot columns, then verify the preview retrieves
  all three saved snapshots. The public polling/preview response shapes are unchanged.

Focused regression and architecture tests passed. Final full-gate results for this update follow
below. No new paid model calls or production database/source-system operations were performed.
This remains foundational work: Beer → Food is not repaired automatically. Weekly food/beverage
actuals remain unavailable until separate curation and reporting activation are completed.


The frontend full gate passed again for the review update: frozen-lockfile installation,
`bun run typecheck`, `bun run lint`, `TMPDIR=/tmp bun run test` (**735 tests / 135 files**),
and `bun run build`. No frontend source or response schema changed.

Ripwire was run against a clean scratch baseline at 27a062e9 because automatic HEAD fallback
was unavailable. The review-fix delta still reports 11 gating warnings: candidate-extractor
complexity growth (14→16), required regression-test class growth and structural duplication
matches to other permission/mapping tests. These are documented static warnings, not a passed
quality gate; no unrelated refactor or blanket suppression was added. The listRuns edit check
reported no-baseline; Java compilation, query capture and caller tests validate this change.


The first full backend review-update run exhausted the test JVM heap. Its SQL-capture fixture
created another full Spring application context. The final fixture captures Hibernate SQL through
the existing logger instead, restricted to the test thread and restoring logger configuration in
finally; it creates no additional Spring context. The stuck worker was stopped only after verifying
its PID and isolated worktree directory. A cache retry failed before tests due to Spotless's stale
JVM-local configuration cache; a superseded lifecycle-based attempt was stopped before completion.
Final gates use `--no-configuration-cache` for those invocations. No production or repository heap
settings changed. These failed/interrupted attempts are not claimed as successful validation.


Final review-update backend gate: `./gradlew --no-configuration-cache test spotlessCheck`
**passed in 5m37s**, with **1845 tests, 0 failures, 0 errors and 5 skipped**.
The final focused permission/model-context/SQL/architecture run also passed after the logger-fixture
change. The full frontend gate passed with 735 tests and production build. PR CI is checked after
pushing the review-fix commit; no merge or deployment is included in this task.
