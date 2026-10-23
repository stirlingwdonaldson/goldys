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
