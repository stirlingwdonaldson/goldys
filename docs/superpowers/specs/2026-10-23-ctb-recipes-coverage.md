# CTB recipe implementation evidence and verification

## Real capture coverage

The original three HARs supply 19 complete captures across 18 recipe IDs, with 41 direct
stock lines and 15 explicit sub-recipe usages. All parse successfully; no lines are dropped.
Reported direct-stock plus usage contributions reconcile to source header totals within 0.01.
This verifies source arithmetic, not independently evidenced supplier cost/tax bases.

The later sanitised survey supplies five endpoints for all 670 catalogue recipe IDs:
3350 unique recipe/endpoint pairs, 3372 responses including repeats. The actual parser accepts
all 3372 responses. It preserves selected dietary states and extracts method text without
markup. There are 24 unique recipes with visible method text, including source stock-export
descriptions; no automatic summary discards those descriptions. Two recipes have dietary
selections. Lookups contain ten dietary requirements, 23 allergens and 17 equipment choices.
The supplied gzip fixture has zero login requests, cookie records or authentication headers.

The [field checklist](2026-10-23-ctb-recipe-field-coverage.md) accounts for 129 distinct supplied
business scalar paths across ten section families. Field paths accompany displayed values;
account/approval-user objects and permission flags are excluded from business display.
Original bytes, metadata and hashes remain available through authorised raw evidence access.

## Live source validation on 23 October 2026

The authenticated Java client fetched and parsed complete compositions for **670/670** listed
recipes: header, direct stock lines and explicit usages. The local audit CSV records every
recipe ID/name and completion status. Of those, 637 appeared in the folder traversal and 33
did not; this is why the recipe list, rather than visited folders, drives full collection.
See [the complete source coverage CSV](2026-10-23-ctb-recipe-live-coverage.csv).

The live Gradle harness encountered an EOF reporting failure after writing the complete CSV
because two test tasks shared the same output directory. This is not claimed as a successful
JUnit live-audit run. The coverage rows themselves record 670 completed parser results and
zero source failures. Subsequent final backend verification runs serially.

A separate read-only proof using the compiled client successfully fetched and parsed all
eleven optional sections for recipe 377 plus the 17-choice equipment lookup. Each response
was HTTP 200. Its output is `/tmp/goldys-recipe-optional-live.log`; the proof completed at
2026-10-23 08:10 UTC. Earlier access proof also traversed 112 folders and 2342 explicit outlet
allocation rows; allocation rows are not a count of unique recipes.

Credentials were entered through hidden input, held in process memory, excluded from fixture
files and configuration caching. Live validation did not write to CTB or the production database.

## Behaviour and source selection

- Catalogue-only recipes remain visible with missing details and unknown costs.
- Header/direct-stock/usage observations replace owned lines atomically; removals retain history.
- Optional sections have independent success/failure histories. Failed refreshes retain previous
  successful values and their original capture times. Older/repeated replays cannot inflate freshness.
- Compatible legacy reported costs compete by original capture time; complete structure retains
  precedence over flattened ingredients. Catalogue names do not establish cost freshness.
- Source evidence reaches resolved/API data: capture/run/raw IDs, hashes, source dataset, endpoint
  or legacy report identity, field paths, and contributing POS entity references. Raw references
  require connectors READ in addition to recipe domain permissions.
- Parent/child recipe navigation uses CTB IDs. Reverse lookup uses an indexed resolved usage query.
  Recursive calculation evidence includes descendant capture references. No inferred name matching
  is introduced; existing POS name/modal-value associations remain explicitly qualified.
- Methods are plain text extracted with the JDK parser. Paragraphs, ordered lists and entity text
  survive; script/style content and formatting do not enter the DOM. Raw source bytes are unchanged.
- Existing recipe tax/cost basis qualifications remain in force; incomparable derived GP/cost-gap
  figures stay unavailable. Reported CTB GP remains labelled as a source figure.

## Scope limits

No populated equipment or ingredient-method schema was observed. Unexpected populated rows are
retained raw with an explicit unsupported-state failure; no fabricated parser claims coverage.
Nutrition was zero throughout the survey. CTB screenshots show per-serving/per-100g displays,
but the response does not establish the basis. Unchecked allergens do not establish absence.
Recipe-fact and ingredient-method modes were sampled; archived/deleted recipes and other outlets
were not enumerated. No automatic dietary suitability or recipe editing is implemented.

## Final verification

`./gradlew spotlessApply test spotlessCheck` passed: 1672 tests across 369 suites, zero failures/errors and 4 opt-in tests skipped.
`bun run typecheck && bun run lint && bun run test && bun run build` passed: 653 tests
across 115 files, with the `/recipes/[recipeId]` route included in the completed production build.
`git diff --check` passed. Browser checks use a local demo server
and mocked owner session, not production authentication. Desktop and 390px mobile checks verify
plain text, child → parent navigation, no horizontal overflow and no browser runtime errors.

Ripwire reports remain non-clean. The two introduced pre-existing method complexity regressions
(capture and overlay) were simplified; RecipeProjector grows from complexity 18 to 21 (minor).
Remaining findings include framework/route/test entry points reported as unused, structural
mapping/record duplication, contract arity, class growth and edit churn. They were reviewed,
not blanket-suppressed. No static-analysis-clean claim is made.

## Concurrent work and rollout

The isolated worktree includes current main `051d0012` (#208–#210), including native React Query,
cancellation, source coverage and ingredient qualification changes. V100 belongs to #210;
unshipped complete snapshots use V101 and sections/evidence V102. The connector monitor allows
120 minutes when complete recipes are enabled (30 minutes otherwise), because the default pacing
alone puts the full 670 × 14-request refresh beyond the old 30-minute monitoring threshold.

Collection/replay remain disabled by default. No push, PR, CI, production migration, production
backfill, source write or deployment has been performed by this task.
