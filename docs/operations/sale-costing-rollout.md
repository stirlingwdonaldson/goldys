# Sale recipe costing rollout

This change builds on the complete CTB recipe work in PR #211 (`3bb30f80`). Land that dependency
first. V103 adds nullable canonical adjustment evidence and two current-version resolved SQL
views. Backend/frontend must be compatible with that migration before exposing the costing call.
No raw record is changed; no report or source configuration change is necessary for payment mode
or adjustment fields already delivered.

## Behavior

The receipt no longer claims profit from POS cost. A separate permission-gated costing request
matches recipes and shows current observed recipe costs times quantity, source date/freshness,
and actual line revenue. Both `sale-items.metrics` and `recipes.cost` READ are required. A cost
permission denial or fetch failure keeps the ordinary receipt visible. The owner instructed on 23 October 2026 to assume CTB recipe costs exclude GST. Estimated
profit is final ex-GST line revenue minus recipe cost times quantity; margin is profit divided
by that revenue. Already-applied adjustments are not applied again; tips stay outside item revenue.
Source POS zero is not a free recipe. Missing, ambiguous or stale costs withhold the whole-sale ratio.
Zero revenue withholds the percentage while preserving estimated negative gross profit for a comp.

`CTB_RECIPE_COSTS_EXCLUDE_GST=true` is the default assumption, wired through
`ctb.recipes.costs-exclude-gst` in application.yml and the production Compose environment.
The API reports `recipeCostTaxBasis=ASSUMED_EX_GST`, and the receipt shows the assumption beside
“Estimated gross margin”. Set the environment variable to `false` and restart through the normal
authorized deployment procedure to withdraw the assumption and withhold margins; observed costs
remain visible. False does not blindly divide every ingredient cost by 1.1: ingredient tax treatment
must be established before implementing a GST-inclusive conversion. This setting applies to individual
sale costing; it does not silently change the recipe catalogue's separately qualified figures.

Complete recipe collection in #211 remains off by default. Its authorized rollout must enable
and verify complete observations, including recipe 722, before expecting new recipe costs here.
This change does not enable production switches or fetch new production recipe facts itself.
See the CTB source-access recipe instructions. Collection failures remain explicit and previous
successful data remains dated rather than appearing refreshed.

The new SQL views immediately reflect current canonical versions without rebuilding all sale
history. Costing reads a repeatable-read snapshot and limits one exact sale to 10,000 lines;
larger sales are explicitly incomplete. The browser's existing 200-line receipt limit does not
truncate backend cost totals. Cost observations are current estimates, not historical actual COGS.

## Historical adjustment recovery

Historical canonical rows start with unknown adjustment evidence. New deliveries retain it.
Before any separately authorized replay, run the parser against retained reports in fetched-time
order and review distinct receipt counts and conflicts. Identical split-tender copies collapse;
distinct adjustment components produce a schema failure requiring investigation. Do not enable
a startup replay blindly or replay an old delivery over a newer current observation.

Prepare an explicit bounded source-reference/date manifest, reconcile final revenue before/after,
and inspect proposed supersessions. Existing `LightspeedSaleItemIngestService.backfill` is not an
unconditional safe production repair: its replay timestamp is current and it logs/continues after
errors. The existing missing-item recovery intentionally does not enrich already-present rows.
A bounded enrichment replay with source-order guards and failure accounting requires its own
review before use. Never update canonical rows or the immutable raw ledger in place.

The real example SP-100 1021082623 must retain two lines/$43 paid; recipe 722 and wine recipe 57
must be matched or clearly missing. The steak source link remains unassigned: its recipe match is
a derived exact-name association, not a change written back to CTB.

## Evidence and local checks

Sanitized fixture `backend/src/test/resources/fixtures/lightspeed/sale_costing_real.csv` comes
from raw record `e1b13f9e-39c3-4971-80f3-fa35d977d2d4`, excluding customer/staff/register details.
Nine selected rows cover seven distinct receipt IDs: the two reported items, a comp, surcharge,
two-quantity spritz line and two lines repeated by Cash/Card tender joins. Broader coverage uses
a temporary sanitized copy of the same entire report, not a fabricated discovery fixture.

To rerun that coverage test with a reviewed local CSV, set `LIGHTSPEED_EVIDENCE_CSV` and run
`./gradlew test --tests '*LightspeedSaleItemCsvParserTest'` from backend. The tested report had
1,950 delivered rows, 1,948 distinct receipt IDs and zero parser failures. The opt-in coverage test
is skipped when that file is unavailable; ordinary committed regression fixtures always run.

No production deploy, migration, refresh or replay has been performed by this task. Push, CI and
production validation require separate authorization; local results are recorded in the task's
completion report.

## Assumption decision

Owner instruction, 23 October 2026: “assume it excludes GST, and make it easy to change later,
and make a note of this.” This authorizes an explicit modelling assumption, not a claim that
CTB's tax basis was independently verified. Historical margins use current recipe observations
and are estimates, with source dates and freshness retained.

## Final local verification (23 October 2026)

- Backend: `LIGHTSPEED_EVIDENCE_CSV=/tmp/lightspeed-sale-evidence-full.csv ./gradlew spotlessApply test spotlessCheck` passed: 1,686 tests, zero failures/errors, four opt-in skips; real PostgreSQL 16 integration tests included.
- Frontend: `bun run typecheck && bun run lint && bun run test && bun run build` passed: 665 tests across 116 files and the production build.
- Real CSV parser coverage: 1,950 delivered rows, 1,948 distinct receipt IDs, zero parser failures.
- Git diff whitespace checks passed. Ripwire review still reports mapping boilerplate duplication and test-size findings; no static-analysis-clean claim is made. The GST calculation/display refactor removed the reported complexity/nesting regressions for that update.
- No authenticated production UI check, push, CI run, migration, deployment, recipe refresh or historical replay was performed.

The code is based on merged recipe ingestion #211 and UI fixes #212. No complete recipe collection
flags were enabled by this task. Actual historical rows remain dependent on a separately authorized
refresh/replay; missing recipe cost remains missing, never a POS-zero fallback.
