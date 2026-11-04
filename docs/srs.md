# Goldy's software requirements

Approved scope baseline, 4 November 2026. The [PRD](prd.md) owns scope. This document owns
behaviour and acceptance; it does not track implementation progress. The
[disposition table](requirements/dispositions.md) links every independent SRS record
to its original full text and identifies retained, revised, excluded, future or
undecided scope. Source examples are not approved venue policies or real-data tests.

## Reading and status

Read the relevant contract below and its linked source records, not the entire
source bundle. Preserve source IDs when revising detail. A source requirement marked
`mandatory — proposed` is not automatically adopted. The compact contracts and each scoped disposition govern where a source clause
conflicts with the owner's [scope decisions](decisions/2026-11-04-product-scope.md).
Source details apply only within that retained scope; unvalidated policy stays open. The original app requirements 1–12 remain mapped
at the end of the disposition table.

Scope adoption, policy validation, implementation and acceptance are separate states.
An accepted capability can remain blocked on a business decision or unavailable
source. Existing code or a passing test alone does not certify operational meaning.

## Shared contracts

- **Evidence and history:** retain source observations and provenance; distinguish
  source facts, resolved measures, estimates, targets and forecasts. Corrections
  preserve history. Business reads follow the existing architecture, including its
  narrowly approved evidence-query exception.
- **Meaning:** identify measure, period, timezone, units, tax/cost basis, version,
  population and source coverage. Missing, unknown, not applicable and known zero
  remain distinct. Partial subtotals stay labelled; ratios need supported operands.
- **Access:** enforce explicit grants on views, calculations, exports, distribution
  and local mutations. Derived measures require operand permissions. Denial is
  explicit. No manager grants are inferred from title or feature scope.
- **Source boundary:** integrations are one-way and failures visible. New parsers
  require real source samples/schema and measured hit rates; fixtures do not prove
  live access or completeness. Missing integrations do not satisfy a contract.
- **UX:** preserve current design and desktop-first use; make qualifications, missing
  states, conflicts, failures and permission denials visible. Accessibility remains
  required. A phone redesign or three new role contexts is not implied.

Detailed source groups: BR-001–010, ACC-001–008, INT-001–007, REL-001–006 and
NFR-001–012, subject to the scoped dispositions and unresolved policies.

## Reporting and distribution

**RPT-001:** Weekly and reusable custom reports identify their definition/version,
period, filters, source basis, generation time, qualifications and status. Live
views can refresh. Issuing or sending a report retains its exact authorized result;
a correction produces a distinguishable new version without replacing the original.

**Acceptance:** reopen an issued report after source correction and observe its
original figures and notices; a new issue references the prior version and explains
the change. Repeating an unchanged authorized report produces the same business
result. Data snapshots and saved-dashboard query configurations are distinct.

**RPT-002:** CSV and Excel exports match the authorized selected result and retain
units, missing values, scope and material qualifications. Exports are usable files,
not screenshots or silently truncated datasets. Include CSV formula-safety checks
where source-controlled text enters a spreadsheet.

**RPT-003:** Scheduled reports and condition-based alerts expose readiness, failure
and delivery state; authorize recipients and content. Cadence, channels, conditions,
retry policy and duplicate-delivery behaviour remain decisions to settle before
acceptance. A send attempt is not proof of receipt.

Source detail: FR-001–006, FR-038–040, FR-051–054, DR-040–042/048,
NFR-004/012, REL-005/006. RPT IDs add contracts missing or insufficiently explicit
in the original app PRD; they do not renumber source requirements.

## Business domains

| Contract | Required verification | Source detail |
|---|---|---|
| Sales, products and payments | Reconcile supported net/tax/gross and adjustments without double-counting; do not infer liability balances while that scope is undecided. | FR-007, FR-012–016; DR-004–015; BR-003/010 |
| Cash and settlements | Compare independently observed physical/expected cash; explain processor gross, refunds/fees and bank net receipts without changing sales to force agreement. Missing independent evidence makes reconciliation unavailable. | FR-015/016; DR-014/015 |
| Stock and recipes | Respect item/location/cutoff, units, pack conversions, recipe versions, yields and evidenced movements. Counts alone are not stock variance. Separate recorded waste from unexplained depletion. | FR-023–030; DR-021–028/050/052 |
| Replenishment and suppliers | Explain demand horizon, usable stock, commitments, delivery promises and suggested quantity. Preserve partial orders/receipts/credits and over-allocation exceptions. Payment status needs payment evidence. | FR-017–022; DR-016–020/052/053 |
| Cost and profit | Keep purchases, theoretical costs and actual COGS separate; actual valuation requires validated movements and policy. Operating profit needs complete relevant expenses and an explicit accounting basis. | FR-038–040; DR-049/050; MET-007/016 |
| Prime cost | Sum validated actual COGS and labour cost only on comparable periods, coverage and cost bases. Theoretical source costs cannot substitute for actual COGS; a missing or unvalidated operand makes prime cost unavailable. Weekly inclusion waits on labour readiness. | MET-009; DOC-005/028 |
| Labour | Compare rostered/actual hours and separately identify wages versus supported full employment cost. Do not derive a complete payroll cost from hours × base rate. Weekly inclusion waits on validated inputs. No staffing recommendations or payroll obligation/payment/settlement reporting. | FR-033–036 as narrowed; DR-029–036; MET-008; DOC-027 |
| Reservations | Show bookings, stated party sizes and recorded outcomes with consistent cancellation/no-show populations. Do not label reservation party size as verified actual attendance. | FR-008 as narrowed; DR-037; MET-004 |
| Forecasts | Support overall revenue and individual products; identify training scope, assumptions, issue version, horizon and uncertainty. Compare issued forecasts to comparable actuals. Source fitness and target validation remain required. | FR-041–044 as narrowed; MET-010/013; NFR-011 |
| Ask Goldy's and dashboards | Use bounded authorized semantic tools, typed widgets and versioned configuration. Reopening live dashboards re-authorizes and refreshes data. Preserve current contracts. | Original app Req. 9/11; existing AI/widget/dashboard contracts |

**REV-001:** Retain review ratings and full text; themes/sentiment link to source
reviews and do not claim wider population coverage than the received sample.
Verify access and traceability for individual reviews and summaries. The independent
SRS lacks an explicit complete customer-review contract; retain the implemented
domain and validate summary quality separately.

**ACT-001:** Local notes, proposed corrections and assigned operational follow-ups
retain actor, responsibility, status and changes. Completion and source correction
are distinct. Define permitted assignees and closure authority before enabling a
workflow; staff application access is excluded.

Source detail: FR-048–050, DR-046/047 and ACC-004/007. Source lifecycle states and
escalation/materiality policies remain proposals until agreed.

## Outstanding decisions

Manager resource/field/department grants; customer-balance scope; report measures,
sign-off/recipients and delivery cadence; cash/bank/expense source access; accounting
and valuation policy; recipe/unit mappings and stock movements; labour components
and permissions; forecast acceptance and horizons; quality/coverage thresholds;
follow-up assignment/closure policy; retention and recovery targets.

Keep these in decision issues. Resolve concrete policy gates as the relevant slice
is planned; neither this list nor the source bundle silently supplies their answers.
