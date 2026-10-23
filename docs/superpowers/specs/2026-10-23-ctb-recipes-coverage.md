# CTB recipe evidence and verification

## Captured response coverage

The sanitized three-HAR corpus contains 19 complete recipe captures across 18
source recipe IDs: 41 direct stock lines and 15 explicit sub-recipe usages.
`CtbRecipeDefinitionParserTest` parsed all 19 captures, rejecting none and dropping
no lines. Summed direct-stock plus explicit usage contributions match each source
header total within an absolute currency tolerance of 0.01. This checks reported
component reconciliation, not independently verified supplier price/cost bases.

Examples include negative stock deductions (651), negative sub-recipe deductions
(649, 643), nested recipe reuse (464→463→462), a 15-portion batch (696), a six-litre
batch (451), a questionable ten-kilogram Crumb mix usage (645), and duplicate names
with distinct IDs (719 and 721). Source values are preserved in each case.

## Live access proof

On 2026-10-23 `CtbRecipeLiveAccessTest` authenticated through the real Java CtbClient,
read and parsed recipe 696's three detail responses, and traversed all 112 folders,
observing 2342 explicit outlet allocation rows. The opt-in test passed with no skip.
Credentials were entered through hidden terminal input, passed only in process
memory, and excluded from fixture files, logs and configuration cache.

The 27 inspected HAR folder responses are partial browser navigation, not a complete catalog. Six representative responses from the second walkthrough are retained as regression fixtures.
The parser tests correctly refuse to promote those partial walks to a full catalog.
Live source data was read only; no production database or deployment was changed.

## Scope limits

Many sub-recipes are referenced but their own headers are not opened in the HARs.
Their absence yields qualified/incomplete recursive estimates, not fabricated costs.
Parser tests verify all source-reported contributions. Recursive calculation tests
use captured values plus explicitly labeled synthetic graph mutations to exercise
scaling, negative usage, missing children and cycles. These synthetic scenarios are
not presented as source discovery.

Optional methods/equipment/notes are empty; nutrition is zero and dietary/allergen
selections unchecked. Those endpoints remain outside this implementation, and no
dietary suitability is inferred.

## Concurrency and rollout

Work is isolated on codex/ctb-recipe-integration. Concurrent wastage, source recovery
and UI PRs landed during implementation; the branch must include their latest main
before its final gate. V99 belongs to wastage; complete recipe snapshots use V100.
Refresh/replay default off. Enabling scheduled collection, production backfill,
pushing, CI and deployment are separate operations and are not claimed here.

## Final local gates

Verified after rebasing onto `8d6fa0a` (merged wastage, ingestion recovery and UI work):

- `./gradlew test spotlessCheck`: successful. JUnit reports 1655 tests across
  363 suites, zero failures/errors and three opt-in tests skipped in the default run.
- `bun run typecheck && bun run lint && bun run test && bun run build`: successful,
  638 tests across 110 Vitest files and a completed production build.
- Focused controller/live-API tests verify the HTTP 204 missing-snapshot contract.
- PostgreSQL integration tests verify complete-snapshot replacement, retained history,
  older/repeated replay, and a PARTIAL connector run retaining HTTP failure bytes
  while refreshing another recipe.
- The Compose YAML parses with collection/replay defaulting off; no production
  Compose command was executed and no real environment file was read.

Ripwire contract checks found the existing connector fetch and recipe costing
method contracts unchanged. Its branch quality report remains non-clean: it flags
Spring interface implementations as unused and structural duplication among typed
query/record mappings and endpoint wrappers, plus growth in CtbClient. The explicit
getters retain the shared authenticated client and normal adapter structure. These
findings were reviewed, not blanket-suppressed; no static-analysis clean claim is
made. The test-gate's named tests were covered by the complete backend suite; its
name-based graph is not proof of exhaustive runtime coverage.

No push, PR, CI watch, production migration/backfill or deployment was performed.
Optional recipe methods/equipment/notes/nutrition/dietary features remain deferred.
