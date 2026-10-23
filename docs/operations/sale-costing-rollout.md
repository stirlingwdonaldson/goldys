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
permission denial or fetch failure keeps the ordinary receipt visible. Backend ratios remain
null while cost tax/effective bases are unverified; source POS zero is not a free recipe.

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
