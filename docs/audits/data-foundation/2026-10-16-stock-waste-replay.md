# Stock/count/waste correction replay

Implementation branch `codex/stock-waste-replay`, based on `1cbaa71c37d02b9c45bff06ac2fea6c48828d0a0`.
All work is isolated in `/tmp/goldys-stock-waste-replay`. No production access, replay, migration,
source mutation, dependency installation, restart, deployment or push is included.

## Confirmed defects and scope

The existing stocktake/count/waste writers compared incoming fields only with the current version.
Recording A=5, then B=6, then replaying A could restore 5; replaying A/B could append artificial
system history. An unchanged intermediate receipt was lost as evidence. The count replay also
supplied the latest header's date as a transient input without retaining that dependency.

Four isolated PostgreSQL regressions on the unchanged base produced three application failures:
old-fact restoration, intermediate/unseen older receipt regression, and failure to move the latest
count with a corrected header date. The rollback characterization already passed. An initial test
fixture JDBC timestamp-binding error was corrected before that valid reproduction.

This addresses existing correctness risks behind G06/G15 and established BR-001 (identity), BR-002
(time), BR-004 (unknown/zero), BR-005 (history), BR-007 (scope), BR-009 (meaning), BR-010 (single
recognition), INT-004/005. Proposed DR-026/027 describe related count concepts but do not authorize
a valuation engine or new source policies. SRS source precedence and user decisions remain in force.

## Resulting behaviour

- Internal processing receipts distinguish HEADER, COUNT and WASTE, keyed by source/reference/raw
  receipt. Equal-value observations are retained, so subsequent correction replay is idempotent.
- Reception order `(fetched_at, UUID)` rejects regressive previously unseen receipts. It is the
  repository's existing deterministic replay convention, not a new vendor revision policy.
- COUNT receipt identity also includes the accepted canonical header version. The writer derives
  its date from that locked same-source header instead of trusting a stale caller hint. The latest
  detail can be re-evaluated after a header date correction; an older detail cannot restore its
  quantity. New count versions retain an immutable header-version FK.
- Changing only header lock/partial metadata does not manufacture a count version. Unknown
  quantities remain unknown; an old zero cannot replace a newer unknown observation.
- The existing replay selects the latest retained header per stocktake, validates missing header
  dependencies, and streams bytes/transactions per payload through the existing raw reference port.
  Its former single-caller bulk-payload/time helper, record and duplicate finder are removed.
- Receipt and canonical writes roll back together on a failed payload. Earlier committed payloads
  survive a later failure; rerunning is safe. Malformed source references still warn and skip as
  before. Missing referenced headers and unavailable payload bytes fail explicitly.

The resolved observation view, API/DTOs, permission gates, source identities, units/cost bases and
frontend are unchanged. Counts remain dated source observations, not current stock or valuation.
No absent line becomes a deletion or zero, and no source-completeness or age-expiry policy is added.

## Migration implications

V98 adds `inventory_observation_receipt` with raw/header FKs and null-aware unique receipt identity,
and a nullable `canonical_stock_count.header_version_id` FK. Its seed reads only raw references
already present in canonical history, including superseded versions. It rewrites no raw bytes,
business facts, logical identities or dates. Existing count-to-header lineage stays unknown.

The upgrade regression starts from V97 with two versions each of header/count/waste, then applies
V98 and validates: six evidenced receipts, unchanged canonical version counts and null historical
header links. Production migration and any repair/replay require separate approval. Reception order
does not settle edited CTB exports, source-issued revision precedence or delete semantics.

## Verification and limitations

Focused tests pass for all three canonical writers, repeated/older receipts, unchanged intermediate
receipts, UUID ties, header-derived date correction, lineage FKs, metadata-only changes, nullable
quantity, atomic rollback/retry, V97 upgrade and the existing observation/parser boundaries.
A full-ledger integration test uses real retained-byte test fixtures and all real parsers/writers:
four replay passes conserve versions, apply one date correction and preserve source quantities/units.
These are synthetic regression fixtures, not newly discovered production evidence or parser hit rates.
No parser implementation or source schema changed; prior bounded real-sample evidence remains the
source-schema reference. No claim of new real-source extraction accuracy is made.

The audit harness's eight standard-library tests passed. Code-derived table schema/lineage artifacts
were regenerated for V98; historical production coverage/readiness probes were not rerun.
Final full backend gate and static-review results are recorded below once complete. Frontend,
browser and model-service tests are not rerun because those implementations/contracts are unchanged.

Static review used Ripwire task planning, the existing invoice/product receipt implementations,
contract checks and the changed-file review bundle. The count writer's `recordAt` contract is
unchanged (two indexed callers, no arity warning). Its internal `sameFact` comparison now takes the
resolved date explicitly; the direct caller was updated. Name-based analysis also flags unrelated
other domains' `sameFact` methods, whose contracts were not changed. The removed bulk raw helper
is no longer a symbol, as expected; repository-wide search found no live callers outside the
replaced inventory replay path. Historical dated plans still describe that older mechanism.

The working-tree quality delta at `1cbaa71c3+dirty` is not clean: 25 gating findings (18 duplication,
three helper-clone, two churn, two verbosity). The actual dead bulk-loader path was removed and an
unnecessary 15-field count-input copy helper was avoided. Remaining warnings include thin
transactional writer growth and broad lexical clone groups involving unchanged methods. No
blanket acknowledgements or unrelated score-driven restructuring was applied. Static navigation
is not runtime coverage or proof that every possible source revision policy is correct.

No remote branch contained a V98 filename at the refreshed refs checked during this task. Migration
versions and actual application/migration-role grants must be rechecked before a separately approved
production rollout. No operational runner flag has been enabled by this implementation.

Final backend gate: `spotlessApply test spotlessCheck` passed in 3m 36s, with 1609 tests
across 350 suites: 0 failures, 0 errors and 2 skipped. Skipped cases are not
claimed verified. This includes PostgreSQL/Testcontainers migration/upgrade, canonical and complete
runner replay, observation reads, rollback/retry and architecture boundaries. The eight audit
harness tests passed again after code-derived schema/lineage regeneration. `git diff --check` passed.
No frontend/model implementation changed, so their gates were not rerun.

Production checkout main remains at `54af2543391615fae20306cc5c85090929c8c99e`; its existing untracked
requirements and plans are preserved. This task did not inspect production payloads or modify
production data/services. No push, PR or CI run occurred. V98 and any operational replay/deployment
require separate approval.
