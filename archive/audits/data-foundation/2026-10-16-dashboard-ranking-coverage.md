# Dashboard trends and product-ranking qualifications

Implementation branch `codex/dashboard-ranking-coverage`, based on `72227a92c7d137b495c2c2bcbd573217f615dd7c`.
Isolated checkout: `/tmp/goldys-dashboard-coverage`. This increment follows the product/coverage and
stock/waste corrections. It changes existing reads and presentation; no migration or ingestion work.

## Confirmed drift and resulting behaviour

| Existing path | Confirmed drift | Correction |
|---|---|---|
| `DashboardApplicationService.salesTrendInternal` → REST/bootstrap → `SalesTrend` | The service queried `sales.net` but discarded notices/provenance. The frontend called it gross sales and described every gap as awaiting a decision. | Carry points, notices and provenance together. Display net sales ex GST, actual requested dates and unavailable gaps. Preserve zero and null. |
| `BusinessKpiGrid` | Every null date was labelled disputed; a fully populated calendar was labelled reconciled without business completeness evidence. | Label null dates unavailable and calendar coverage unverified. Existing source permissions remain. |
| `ResolvedProductSalesQuery` → dashboard, `DerivedMetricExecutor`, `RankingService` | Rank lists dropped period qualifications, including when empty. An all-unresolved product could receive a numeric rank behind known products. | Shared qualified ranking: known revenue subtotals, pre-limit date coverage, excluded unknown-revenue product count, and “Ranked by known sales; full-period ranking unverified.” Explicit zero stays rankable. |
| Dashboard default product window | Subtracting 30 days then including both endpoints produced 31 dates under a 30-day label. | Thirty inclusive dates, using venue today. Trend remains fourteen inclusive dates. |

This addresses established BR-004 (unknown/zero), BR-006 (derived scope), BR-007 (completeness), BR-009
(meaning) and the user's partial-subtotal/net-sales decisions. Proposed DR-040/041 and REL-001/003
provide related concepts, not authorization for new reports, full source lineage or completeness
inference. The imported gap assessment is historical evidence; findings above were checked against
this branch's implementation rather than assumed from its older assessed commit.

## Boundary and contracts

`semantic/ProductRanking` carries items, `MetricCoverage` and notices. The resolved query computes
coverage over all rows before top-N. A date with any unavailable revenue is unresolved; a date with
no projected rows is absent; other represented dates are observed. These categories do not establish
which dates should have traded, which products should exist or whether within-day receipts are complete.
An absent product/date combination never becomes zero. Missing-period provenance uses the same
unresolved/absent dates. Name-based identities, source amounts, quantity meaning and GST bases are
unchanged and qualified. No quantity conversion, tax normalisation or historical expiry is inferred.

The existing values-only Java `topSellers` adapter remains for compatibility; all production ranking
consumers use the qualified method. Products with no known revenue are excluded from numeric ranks
with their count disclosed. Known subtotals can still have an unresolved component, so ordering is
always explicitly unverified. Ties use product name for deterministic order.

`/api/dashboard/sales-trend` now returns `{points, notices, provenance}`;
`/api/dashboard/top-sellers` returns `{items, notices, coverage}`. Bootstrap embeds these envelopes.
Backend/frontend must roll out together. Both live and demo clients are updated. No independently
versioned external consumer was found; any such consumer needs this contract update. Empty results
retain notices. Existing catalogue and fixed ranking tools carry the shared notices/provenance;
saved widgets already retain result notices and requery current resolved data without changing stored
configuration. Ask Goldy's existing answer rendering displays tool notices.

## Verification

Regressions cover known revenue plus an unresolved component, unknown-only products, explicit zero,
empty periods, absent calendar dates, unknown expected population and qualifiers outside the top-N.
A real PostgreSQL seam compares stored resolved rows through dashboard, catalogue, saved widget and
`get_top_products` output. Application tests verify permissions before semantic access and inclusive
venue-date default windows. REST serialization and live-client tests cover envelopes; component/demo
tests cover net labels, gaps, zero/null, empty-ranking notices and shared default/range behaviour.

Final backend recheck passed: `./gradlew test spotlessCheck` in 3m 38s, with 1,614 tests across
350 suites, zero failures/errors and two skipped. The skipped live LLM evaluations are not claimed
verified. The isolated polling retry also passed; its earlier failure (expected three frames after
close, observed four) remains recorded below. `spotlessApply` ran before this final unchanged-source
gate. This includes the PostgreSQL comparison and existing architecture/permission tests.

Frontend final gate passed: `bun run typecheck`, `bun run lint`, `bun run test` (624 tests across
106 files) and `bun run build` (28 routes generated). Dependencies were reused from the previous
isolated worktree via a temporary symlink, then unlinked; none were installed. The audit harness's
eight stdlib tests passed. Its incidental generated lineage changes were reverted because no
source/schema mapping changed in this increment. `git diff --check` passed.

Ripwire quality-delta and contract/review passes were run against `72227a92c+dirty`. The final
quality report is not a clean gate: 32 major findings remain (14 churn, 11 lexical duplication,
two helper-clone, two dead-code, three length). The short arithmetic helper matches unrelated
parser/purchase helpers; cross-domain coupling would be inappropriate. Longer query and test bodies
reflect explicit coverage and cross-path checks. Other clones are conventional test/DTO patterns.
The values-only adapter is intentionally retained; the application method reported dead is called
by its controller. Static edit-check even reported the changed application return type unchanged
and found no callers: its graph could not bind these Java calls. The compile, REST, stored-row
seam and explicit caller inspection supply the contract evidence. No blanket acknowledgements or
unrelated refactors were applied to make these scores green.

A final backend gate after the metadata adjustment failed in the untouched
`LiveFramePollerTest.pushesFramesOnTheCadenceUntilClosed` post-close size assertion (line 34).
`LiveFramePoller.close()` cancels future scheduling with `cancel(false)` and does not await an
already-running frame consumer. The first full gate passed this case. This intermittent close race
is recorded as an unrelated existing risk; no connector or poller code was changed for this task.

## Remaining limits and operational boundary

Expected trading/product population, CTB complete/partial exports and revision semantics remain
unverified. Source product identity/category history, GST-normalised product amounts, validated
missing-money bounds and prospective booking freshness remain separate evidence/decision work.
These qualifications do not implement those capabilities. No new exports, forecasting features or
permission grants were added. Tests begin from isolated resolved fixtures for this increment;
production source-to-projection reconciliation and production visual/browser behaviour were not exercised.

No production database access, migrations, replay, ingestion, restarts, source writes, dependency
installation, push, PR, CI run or deployment occurred. The production checkout and its uncommitted
requirements/plans were preserved. The branch also inherits earlier prepared migrations through V98;
deploying the chain requires separate production migration and deployment approval.
