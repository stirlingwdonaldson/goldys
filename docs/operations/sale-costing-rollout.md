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

## CTB lock incident and recovery (23 October 2026)

Read-only production inspection found CTB run `a80834a2-b477-44bf-afcf-ea90ddb39bb9` waiting
at `delete from resolved_recipe` behind a transaction started during application initialization.
At 09:40 UTC it had persisted 107 payloads / 7,383,864 bytes, with no new payload since
09:22:13 UTC. Socket samples showed only database connections. This was processing contention,
not a CTB download in progress.

The deployed price query loaded all 1,473,588 current sale entities into the recipe rebuild's
persistence context. Subsequent evidence queries could repeatedly dirty-check that entire history.
The replacement uses a six-field constructor projection, preserving all price observations and
exact source/receipt references without managing sale entities. The recipe cost inputs are also
loaded once per rebuild, and start/prepared timing messages identify the rebuild in logs.
The final prepared message precedes transaction commit; startup's “Resolved projections rebuilt”
message confirms completion of the startup sequence.

Verification: a PostgreSQL regression first reproduced 1,000 managed sale entities, then passed
with zero while retaining all current observations and receipt provenance. A separate temporary
PostgreSQL 16 benchmark with 1,473,588 synthetic sale rows, 663 recipes, one shared source payload,
and a 2 GiB test heap rebuilt/flushed in 11,866 ms with 1,326 managed entities. It verified all
1,473,588 receipt references survived in the price evidence. This is a workload benchmark, not a
production timing guarantee or a test of the live CTB endpoint. The equivalent scalar scan on
production took 427 ms in read-only `EXPLAIN ANALYZE`; that excludes Java mapping and writes.

Recovery is a planned maintenance deployment, not a database-session kill or raw replay:

The inspected production containers use release checkout
`/srv/ai/projects/goldys-releases/f2203e5adf560e12dd70b91ada06372eb378dbcd`, the committed
`ops/releases/2026-10-16-data-correctness/safety.yml`, and a local `recipe-rollout.yml` that enables
complete recipes and disables recipe replay. Preserve this layout and both overlays. Do not use
the generic root-checkout Compose command from the initial rollout instructions.

1. Merge updated PR #213 after CI passes. Prepare a new immutable release checkout in one Bash
   session; leave the shared root checkout and existing release unchanged:

   ```bash
   set -euo pipefail
   umask 077
   cd /srv/ai/projects/goldys
   git fetch --prune origin
   release_sha=$(gh pr view 213 --repo stirlingwdonaldson/goldys --json state,mergeCommit \
     --jq 'select(.state == "MERGED") | .mergeCommit.oid')
   test -n "$release_sha"
   git merge-base --is-ancestor "$release_sha" origin/main
   release_dir="/srv/ai/projects/goldys-releases/$release_sha"
   git worktree add --detach "$release_dir" "$release_sha"
   cd "$release_dir"
   cat > recipe-rollout.yml <<'YAML'
   services:
     backend:
       environment:
         CTB_RECIPES_ENABLED: "true"
         CTB_RECIPES_REPLAY: "false"
   YAML
   prod_release() {
     docker compose -p goldys-prod --env-file /srv/ai/projects/goldys/.env.prod \
       -f "$release_dir/docker-compose.prod.yml" \
       -f "$release_dir/ops/releases/2026-10-16-data-correctness/safety.yml" \
       -f "$release_dir/recipe-rollout.yml" "$@"
   }
   ```

   The recipe overlay reproduces the two flags read from the current deployment. The safety
   overlay keeps historical replay/backfill runners disabled. The ex-GST assumption defaults to
   true in the new base Compose config; set `CTB_RECIPE_COSTS_EXCLUDE_GST=false` in the existing
   operator-managed env file to withdraw it later. Do not read, copy, print or source that env file.
2. Build before starting the outage:

   ```bash
   export SENTRY_RELEASE="$release_sha"
   prod_release build backend frontend
   ```

3. Arrange a maintenance window: pause upstream webhook delivery or confirm its retry/retention.
   Stop frontend/backend only; their in-flight transactions roll back and the database releases
   their locks. The separately committed raw payloads remain available. Do not stop PostgreSQL,
   remove volumes, or terminate individual database sessions.

   ```bash
   prod_release stop frontend backend
   ```

4. Take the quiesced database backup using the existing deployment runbook. For example:

   ```bash
   backup_dir="$HOME/goldys-backups/sale-costing-$(date -u +%Y%m%dT%H%M%SZ)"
   mkdir -p "$backup_dir"
   chmod 700 "$backup_dir"
   (umask 077; docker exec goldys-prod-postgres-1 sh -c \
     'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_dir/database.dump")
   test -s "$backup_dir/database.dump"
   sha256sum "$backup_dir/database.dump" > "$backup_dir/database.dump.sha256"
   docker exec -i goldys-prod-postgres-1 pg_restore --list < "$backup_dir/database.dump" > "$backup_dir/archive-list.txt"
   ```

   Stop if backup creation or archive listing fails. This backup contains business/personal data;
   keep it outside the repository and access-controlled.

5. Start the already-built pair; Flyway applies V103 on backend startup:

   ```bash
   prod_release up -d --no-build --no-deps backend frontend
   prod_release logs --since 5m backend
   curl -fsS https://platform.swd.sh/api/health
   ```

   Expect health `UP` and a completed startup projection rebuild. If the previous run was still
   `RUNNING` at shutdown, expect `RUN_INTERRUPTED` (normally terminal `PARTIAL` because it already
   has stored payloads); a run that finished before shutdown keeps its actual terminal result.
   Health alone is insufficient: HTTP serving starts before startup runners finish.
6. Resume upstream delivery. In Data health, run CTB once and wait for terminal status. Verify
   fresh complete recipe observations including recipe 722 and then sale SP-100 1021082623.
   A complete recipe crawl can take tens of minutes; check that raw payload counts/timestamps
   advance rather than treating `RUNNING` alone as evidence of a stall.
   The five historical sale-item schema warnings observed on 7/9/15 August and 5/22 September
   remain a separate source-data issue; a new run can finish `PARTIAL` for those dates. Review
   its failure details rather than expecting a blanket `SUCCESS`.

No maintenance command in this section has been executed by this task. Do not restart the old
image just to clear the lock: it contains the same expensive entity-loading query.

Final fix validation: `LIGHTSPEED_EVIDENCE_CSV=/tmp/lightspeed-sale-evidence-full.csv ./gradlew
spotlessApply test spotlessCheck` passed (1,687 tests, zero failures/errors, four opt-in skips;
373 suites). Recovery shell blocks passed `bash -n`; the commands were not executed. Public
contracts for `currentSaleItemPrices` and `RecipeProjector.recomputeAll` are unchanged. Ripwire
still reports generic test-fixture duplication and recent-churn findings; no static-clean claim
is made. Frontend code was not changed by this follow-up, so its previous local gate was not repeated.
