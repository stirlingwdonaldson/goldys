# Architecture audit fixes — 12 November 2026

These changes address the nine primary findings from the non-weekly architecture
audit against `a1c961b4`. They preserve the existing business calculations, explicit
permissions, raw-first ingestion and live dashboard semantics.

## Changes and limits

| Finding | Implemented change | Deliberate limit |
|---|---|---|
| Automatic migration repair | Remove the startup repair strategy; normal Flyway validation rejects changed applied migrations. | Historical deployment drift must be investigated before rollout, never automatically accepted. |
| Lost forecast qualifications | Persist runtime warnings separately from nullable diagnostics; expose them in result notices. Python carries optional-source failures into the response. | External driver data and model artifacts are still not frozen by the internal input snapshot. |
| Stranded forecast work | Fail queued and running work on restart, fail rejected dispatch, and prevent repeat execution of terminal runs. | In-flight ownership is single-instance; replicas need a database claim before deployment. |
| Duplicated formula operands | One ordered bounded formula definition drives the eight ratio/difference calculations and constituent traversal. | Bespoke ranking, department percentages and withdrawn measures retain explicit behavior. No expression language. |
| Tool declaration/execution drift | Six catalogue tools execute the same immutable query list authorized by the dispatcher. Comparison notices include both results' qualifications. | Specialized ranking operations remain explicit; fixed enum inputs and the dispatcher remain. |
| Repeated Lightspeed envelopes | One vendor-local decoder serves daily, product, sale-item, all-sales, deleted-sale and receipt-evidence intake. | Payment intake is deliberately untouched while PR #258 changes that path. Move its remaining decoder after that PR lands. |
| Repeated stage accounting | Ordinary product, deleted-sale and all-sales processing shares parse/write outcome accounting. | No outer transaction, replay-policy change, CTB hold change, or universal connector workflow. |
| Dashboard write/cache drift | Save/update/restore/template/pin/delete share detail/list/render/revision cache consequences. Open revision history refreshes after writes. | Uses existing live/demo adapters and query keys; no transport or auth changes. |
| Widget contract duplication | Infer frontend widget types from the runtime schema; render with exhaustive dispatch. | Backend wire compatibility remains separately tested. Invalid responses are still rejected, not filtered. |

The forecast runtime now has its own lightweight CI test job.
Its tests use fake prediction and external adapters, with network access blocked.
The [TimesFM decision](../decisions/timesfm-inference-service.md) records current
ownership rather than the original data-agnostic runtime description.

## Coordination

At implementation start, PR #257 owns session-expiry handling and PR #258 owns
payment parsing/ingestion and sales-settlement presentation. Neither path is edited.
PR #253 changes dashboard prototype files, package metadata and Tailwind setup;
the production dashboard changes here use different files.

The weekly-report assessment (now PR #259) and test-effectiveness worktrees were inspected
for changed files. Weekly calculations, report views/exports, shared frontend API
types and forecasting chart tests are untouched. The NVIDIA work runs a separate
trial and does not replace the current application tools in this change.

The pre-commit Ripwire merge scout found no same-symbol conflicts or same-file risks
against PRs #253, #257, #258 and #259 at their inspected heads. The test-effectiveness
worktree's current changed paths are also disjoint. These checks are a snapshot,
not a guarantee about later edits.

Git's simulated merges against those four PR heads were also clean. A temporary
copy of the test-effectiveness agent's new feature-window and predictor tests passed
against this branch (five tests); that agent's files were not modified.

Recheck PR heads and active changes before publication: disjoint files and a clean
text merge do not prove behavioral compatibility. Run the established full backend
and frontend suites, plus the Python runtime tests, on the combined result when
landing order changes.

## Regression evidence to preserve

- Application startup rejects a deliberately mismatched applied checksum without
  rewriting history; the test restores its disposable database afterward.
- Canonical rollback leaves byte-faithful raw evidence and a failed processing stage
  with a partial ingestion run; earlier successful row writes remain intact.
- Large/invalid Lightspeed envelopes preserve bulk support and safe error messages.
- Forecast warnings survive persistence and DTO mapping; missing predictions are not
  scored as zero; queued/rejected/repeated work reaches the intended state.
- Prepared tool reads equal authorized reads; derived metric permissions follow the
  actual formula operands; comparison qualifications survive rendering.
- Dashboard writes invalidate every affected view, revision panels refresh, and
  existing in-flight editor changes remain protected.
- Widget schema and inferred types agree on scalar/null cells and reject invalid data.

Lower-priority audit candidates (dashboard edit-session extraction, saved-dashboard
rendering extraction, relational query session, draft-tool results and legacy
stock-count date plumbing) are not required to correct these primary findings and
remain separate design work. No test/comment cleanup or new business policy is included.

The broad Ripwire quality scan was not a clean gate. Its reviewed findings include
constructor/test-shape similarities, repeated lifecycle guards, size thresholds,
and the deliberately retained payment decoder. The new guards and failure-path
tests are intentional; this change does not add global quality acknowledgements
to suppress those findings.

## Local verification

- Backend: `./gradlew test spotlessCheck --max-workers=2 --no-configuration-cache`
  passed: 411 suites, 1,924 passed tests and five skipped opt-in/live-fixture tests.
- Frontend: typecheck, lint, all 139 test files (768 tests) and production build
  passed on the final implementation, including the pending-initial-read regressions.
- Forecast runtime: 30 tests passed with fake TimesFM, blocked external network
  access and small synthetic XGBoost fits. Five additional tests from the concurrent
  test audit also passed against this branch.
- Changed documentation links and skill-discovery symlinks resolve; metric and tool
  recipes retain validation, permission and testing requirements.

No live model, real source fetch, browser end-to-end run, production database check
or deployment was performed. These are local results; CI has not run for this branch.

## Rollout

V113 adds nullable `forecast_run.runtime_warnings`; historical rows retain NULL because
their warnings were not retained and cannot be reconstructed. Deploy schema/backend
before any later consumer changes. This document does not authorize deployment.

Removing automatic repair can expose an existing migration-history mismatch at
startup. Verify migration history against retained release artifacts before rollout;
do not use a blanket repair to make a mismatch disappear. No applied migration has
been edited by this change, and no production database was inspected or changed.
