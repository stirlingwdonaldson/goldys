# Role–KPI matrix — Goldy's Unified Data Platform

Date: 14 October 2026 (Australia/Melbourne). Status: supporting documentation; stakeholder validation and operational acceptance pending.

Authority: [SRS v1.1](SRS.md), especially its source precedence and obligation/maturity rules. Existing SRS identifiers are retained. These documents explain the baseline and its proposals; they do not approve a capability, assign a grant, choose architecture or establish implementation acceptance. Revision 1.1 extends proposed information contracts under DR-049–DR-055 and existing IDs, as recorded in [REVISION-LOG.md](REVISION-LOG.md). These supporting documents mirror that SRS revision; they do not independently adopt policies or approve capabilities. Validation questions remain in [OPEN-DECISIONS.md](OPEN-DECISIONS.md).

## Interpretation and access boundary

Sources: SRS sections 3–4, 6, 12–13 and 16; [BMAD responsibility contracts](ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md#responsibility-and-kpi-contracts); [reported duties](briefs/brief-goldys-requirements-2026-10-14/addendum.md); [candidate capability/KPI meanings](../research/business-capability-map.md). Role assignments from the earlier capability map remain proposals where later reported duties narrow them.

The matrix maps responsibilities to decisions, information, permitted actions and candidate feedback. It assigns no actual account, approval power, KPI target or job description. All rows require validated performer, outcome owner, evidence verifier, consequential-action approver, delegation limits and absence cover under GOV-001/OD-02. One person can hold several responsibilities; head chef/kitchen manager and GM/venue manager remain distinct until confirmed.

Every view, operand, note, drill-down and export is purpose/scope/operation/time constrained by ACC-001–ACC-008. Navigation context and personal pins never grant permission. Private absence reasons, individual pay and customer contact need an independent justified grant. Small-population inference rules remain QP-05/OD-10. Revocation blocks newly accessed restricted views, exports and writes; downloaded files cannot be recalled. Connected systems remain read-only; consequential actions occur externally (CON-001/CON-003, INT-003/INT-007).

## Responsibility → decision → information → action → KPI matrix

All KPI accountabilities below are candidates, subject to OD-09/OD-13. MET-001–MET-012 preserve K01–K12 meanings. Local save/review/verify actions apply only within independently established grants and OD-14 authority.

| Role / evidence status | Responsibilities | Decisions | KPIs / outcome feedback | Data required | Actions | Access constraints / validation |
|---|---|---|---|---|---|---|
| Owner — reported opening-evidence recipient; sustainability/approval duties proposed | Interpret sustainability, resources and opening assumptions | Which qualified result supports intervention? Which site assumption/resource option needs testing? | MET-007–MET-013, MET-015–MET-016; optional MET-009; later opening outcomes only when observed | Compatible revenue, COGS/expense/valuation coverage, labour scope, cash/liabilities/commitments, original forecast versions, new-site assumptions and sensitivity (DR-040–DR-042/DR-048–DR-050) | Review/export permitted evidence; retain local decision/scenario/follow-up; choose or fund externally within confirmed authority | No title-derived full access or spend approval; individual pay/contact and technical evidence need justified purpose. OD-02/OD-08–OD-11/OD-13; UX-002 |
| General manager — reported reporting/insight intent; report preparation/sign-off ownership open | Weekly review, interpretation and cross-business coordination where assigned | Is report ready for its purpose? Which changes need whose response? What changes the outlook? | MET-010–MET-012, MET-015; selected operating measures only | Report definition/instance/revision, report-linked assembly/check/rework person-time (DR-044/DR-048), coverage, comparisons, departmental evidence, forecast limitations, follow-up owner state | Review/export; record question, proposal or ownership request; coordinate external response; return to comparable outcomes | Report-definition change, sign-off and sensitive finance/personnel access require separate grants. GM is not automatically venue manager. OD-01/OD-02/OD-09/OD-13/OD-14; UX-002/UX-006 |
| Venue manager — charter role; duties proposed | Assigned service and cross-department coordination | What service/skill/availability issue needs coordination? What remains unresolved? | MET-003, MET-008, MET-011–MET-012 | Demand versus workload, service evidence, skill/availability scope (DR-051), unresolved issue ownership and due basis (DR-033–DR-035/DR-038/DR-044/DR-047) | Scoped local follow-up/escalation; coordinate work externally; revisit outcomes | No automatic owner finance/opening or payroll scope; context follows assigned duty; no fourth management group. OD-01/OD-02/OD-04/OD-14; UX-002–UX-005 |
| FOH manager — reported FOH rostering and ordering; service/booking/cash duties proposed | Plan FOH coverage and supply through delivery; other service responsibilities only if validated | Do effort and skills match coverage? What quantity/timing is justified? What changed in attendance or service? | MET-003–MET-006, MET-008, MET-010, MET-014; if additional duties selected, MET-001–MET-002/MET-011–MET-012 | Roster/actual time, availability/skills with dated declarations and precedence (DR-051), without private absence reasons; bookings versus visits; product mix/effort; physical/usable/uncommitted stock cutoff and holds/commitments (DR-052), pack conversion, recipe/pour, outstanding orders/delivery horizon | Save local coverage/order scenario, request evidence, record follow-up; publish roster/place order/change service externally | FOH scope only unless independently granted; no inferred BOH personnel, individual pay, venue finance or cash custody. OD-02–OD-07/OD-09/OD-14; UX-003/UX-007 |
| Head chef — reported BOH rostering and ordering; recipe/control ownership proposed | BOH coverage and ingredient supply; assigned preparation, recipe or control duties | What food/preparation/skills are needed? Is coverage credible? Which ingredients support demand until replenishment? | MET-003, MET-005–MET-008, MET-010, MET-014; MET-011–MET-012 when assigned | Food/product outlook, effort, effective recipe/modifier/yield evidence, usable stock/count cutoff and holds/commitments (DR-052), conversions, scheduled outstanding supply (DR-053), BOH dated availability/skills/roster (DR-051) | Save scenario/proposal; request count/recipe evidence; follow up; roster/order/preparation action externally | BOH scope; no automatic FOH staffing, private pay, full accounts or food-control authority. Not assumed equivalent to kitchen manager. OD-02/OD-04–OD-09/OD-14; UX-004/UX-008 |
| Sub-head chef — reported BOH ordering; rostering/spend approval unconfirmed | Assigned ordering | What should be ordered, why and by when? Which uncertainty changes quantity? | MET-005–MET-006, MET-014; scoped supplier/exception feedback if assigned | BOH product/recipe demand, usable stock, pack conversion, promised tranche/revision horizon (DR-053), outstanding orders/receipts, holds/commitments (DR-052) and gaps | Retain scoped purchasing scenario and follow-up; order externally under validated delegation | Ordering does not grant Staffing, labour-cost/pay access, approval of spend or head-chef work. OD-02/OD-05/OD-06/OD-14; UX-004/UX-008 |
| Kitchen manager — distinct charter role; duties unvalidated | Only validated kitchen preparation/supply/control responsibilities | Which assigned question can this person resolve? | Corresponding selected MET measures; no blanket head-chef KPI allocation | Only evidence supporting the assigned duty; reuse BOH patterns where duties overlap | Scoped local follow-up/proposals and external actions for validated duties | No automatic head-chef equivalence, rostering authority or duplicate dashboard. OD-01/OD-02/OD-10/OD-14; UX-004/UX-008 |
| Operational staff working FOH — charter role; specific assignments proposed | Assigned service/work/evidence contribution | What instruction applies? What evidence or issue should I contribute/escalate? | Assigned verified issue response under MET-012; no established personal margin/sales/labour ranking | Relevant versioned offering/modifier/task instructions; own permitted work evidence; issue status | Add permitted local observation/proposal and response evidence; perform work externally | No manager-wide forecasts, peer records, costs/pay, customer contact or diagnostic access without separate purpose. OD-02/OD-10/OD-14; UX-003/UX-007 |
| Operational staff working BOH — charter role; specific assignments proposed | Assigned preparation/work/evidence contribution | What preparation evidence applies? Which issue needs escalation? | Assigned verified issue response under MET-012; no established department KPI ownership | Relevant versioned recipe/task evidence, own permitted observations, assigned issue context; allergen/control assertions only with evidence | Add local observation/proposal and follow-up evidence; count, receive, dispose and prepare externally where assigned | No department finance, peer personnel/pay, ordering or control approval by title. OD-02/OD-05/OD-10/OD-14; UX-004/UX-008 |
| Bookkeeping/payroll — conditional supporting function | Authorised financial/pay interpretation and reconciliation | Is difference timing, classification, allocation or missing evidence? Is cost/pay conclusion justified? | MET-007–MET-008, MET-011, MET-016 | Typed sales/money/settlement, invoice/credit matches, valuation cost allocations (DR-050), atomic recognition and overlap reconciliation (DR-049), effective assignment, work/pay components and payment evidence | Save explanation/proposed correction; query/correct externally; export approved evidence | Purpose/time limited; payroll and accounting scope validated separately; no automatic operating controls or unrelated personal records. OD-02/OD-07–OD-10/OD-14; ACC-003/ACC-006 |
| Adviser — conditional supporting function, no assumed account | Assigned interpretation of specific financial, employment, privacy, records or control question | What applies? Which interpretation/evidence supports the permitted conclusion? | Only the selected measures/control outcomes for that purpose | Approved scoped evidence, relevant applicability and policy versions | Explain/query; retain authorised local advice/proposal; export only if separately granted | Professional title/invitation is not indefinite venue-wide access or approval authority. OD-02/OD-07–OD-10; ACC-006/ACC-008 |
| Evidence steward / administrator — conditional duties, not new positions or a fourth context | Resolve assigned mapping/definition/evidence or grant issues | What blocks reliance? Who can authorise resolution or a grant change? | MET-011; defined coverage/eligibility and verified issue outcome under MET-012 where selected | Original evidence, effective/recorded time, identities/units, definitions, correction history, grant authority and affected decisions | Save proposal/follow-up; seek source repair externally; perform only specifically authorised local definition/grant operations | Evidence stewardship and administration grants differ; raw/technical access is bounded. Duty-specific pin stays in existing context. OD-02/OD-09/OD-10/OD-14; UX-009/UX-015 |

Customers, employees and suppliers can be information subjects/counterparties without platform accounts. The Employee business concept is distinct from a staff member's platform grant (DR-029, ACC-001/ACC-003).

## Candidate KPI contracts and input dependencies

The definitions below preserve SRS section 16. No numeric target is selected. Each selected measure requires definition/version, eligible population, scope/period, unit, tax/cost basis, comparator, missing/zero-denominator rule, provenance/coverage, freshness and accountable evaluator. Ratios with missing/zero denominators are unavailable, not zero or infinity. Reversals can produce negative values; missing costs cannot imply full profit. Filters recalculate eligible components (DR-040–DR-042, BR-003–BR-008, FR-054, REL-001–REL-004).

### MET-001 — Net sales and sales mix

**Status:** Mandatory — proposed. **Source:** [SRS MET-001](SRS.md#met-001--net-sales-and-sales-mix).

**Definition:** Eligible line consideration less agreed revenue reductions/reversals, excluding collected tax and separately classified liability/tip amounts; surcharge treatment is explicit. Product/category monetary mix divides compatible eligible net sales by total eligible net sales; quantity mix has its own sale-unit basis.

**Acceptance / input qualification:** MET-001.AC1 — A/T: independently recompute from the eligible fixture inputs. Line/modifier/adjustment consideration reconciles without double counting. Unmapped products remain in known totals with mix coverage qualified; zero/missing denominator is unavailable, and negative reversal periods remain negative.

### MET-002 — Spend per actual cover and average check

**Status:** Mandatory — proposed. **Source:** [SRS MET-002](SRS.md#met-002--spend-per-actual-cover-and-average-check).

**Definition:** Spend per actual cover = eligible attributed net sales / matching actual covers. Average check = eligible check sales / eligible check count. Cover, check, split/merge and bar/food inclusion rules and charge-attribution coverage are explicit.

**Acceptance / input qualification:** MET-002.AC1 — A/T: independently recompute from the eligible fixture inputs. Six booked persons, four actual covers and two checks never share a denominator by default. Unlinked sales or unknown cover count qualifies/unavailable spend per cover; missing counts never become booked size.

### MET-003 — Service duration distribution

**Status:** Mandatory — proposed. **Source:** [SRS MET-003](SRS.md#met-003--service-duration-distribution).

**Definition:** Eligible fulfilment instant minus order instant for a defined item or service stage; amended/partial orders have explicit eligibility. Report observed count/coverage and agreed distribution summaries.

**Acceptance / input qualification:** MET-003.AC1 — A/T: independently recompute from the eligible fixture inputs. Missing service timestamp is excluded with visible coverage; check closure is not a proxy. Invalid time order is an exception, not negative duration silently averaged away. DR-054 event pairing, per-event/per-unit weighting and remake treatment shall be explicit; verify AT-04.STAGE.

### MET-004 — Reservation no-show rate

**Status:** Mandatory — proposed. **Source:** [SRS MET-004](SRS.md#met-004--reservation-no-show-rate).

**Definition:** Explicitly assessed no-show reservations / eligible expected reservations under agreed cancellation and assessment-cutoff rules × 100. Booking-count and booked-person measures are separately defined.

**Acceptance / input qualification:** MET-004.AC1 — A/T: independently recompute from the eligible fixture inputs. A missing arrival link remains unresolved without assessment evidence. Zero/missing eligible denominator produces unavailable rate; cancelled-ineligible bookings do not inflate it.

### MET-005 — Stock balance and count variance

**Status:** Mandatory — proposed. **Source:** [SRS MET-005](SRS.md#met-005--stock-balance-and-count-variance).

**Definition:** Book closing = opening + accepted receipts + transfers in + production outputs − transfers out − recorded consumption − recorded disposal ± other evidenced adjustments. Count variance = observed − book at matched item/location/cutoff.

**Acceptance / input qualification:** MET-005.AC1 — A/T: independently recompute from the eligible fixture inputs. A count eight/book ten gives -2 physical units; absence of a count is not zero. Value variance additionally requires cost method/date; transfer/linked loss counted once.

### MET-006 — Usage variance and recorded waste

**Status:** Mandatory — proposed. **Source:** [SRS MET-006](SRS.md#met-006--usage-variance-and-recorded-waste).

**Definition:** Usage variance = named observed or residual depletion − theoretical usage at matched item/period/scope/unit. Recorded waste sums compatible evidenced waste quantities by reason; waste percentage needs its own agreed denominator.

**Acceptance / input qualification:** MET-006.AC1 — A/T: independently recompute from the eligible fixture inputs. Do not sum litres with kilograms or count residual as known waste. Theoretical calculation shows recipe/modifier/yield coverage; known disposal is not subtracted twice in residual reconciliation.

### MET-007 — COGS, gross profit and gross margin

**Status:** Mandatory — proposed. **Source:** [SRS MET-007](SRS.md#met-007--cogs-gross-profit-and-gross-margin).

**Definition:** COGS is agreed carrying cost attributable to period goods sold. Periodic inventory calculations declare additions, conversion costs, returns, transfers and loss treatment. Gross profit = matched net revenue − COGS; gross margin % = gross profit / matched net revenue × 100. Category cost % matches category scopes.

**Acceptance / input qualification:** MET-007.AC1 — A/T: independently recompute from the eligible fixture inputs. Purchases/payments alone cannot supply COGS. Missing valuation/cost coverage makes profit partial/estimated or unavailable; waste is not double-subtracted. Markup uses cost denominator and is separately named. Verify the explicit complete result in AT-09.POS, unsupported result in AT-09.INC and cost allocation restatement in AT-09.REV.

### MET-008 — Labour cost and productivity

**Status:** Mandatory — proposed. **Source:** [SRS MET-008](SRS.md#met-008--labour-cost-and-productivity).

**Definition:** Labour % = defined labour cost / matched eligible net sales × 100. Sales per labour hour = matched net sales / explicitly worked or payable/paid hours. Included wage/penalty/allowance/super/leave/on-cost components and allocation method are explicit.

**Acceptance / input qualification:** MET-008.AC1 — A/T: independently recompute from the eligible fixture inputs. Wages-only, loaded, forecast, provisional and final results are distinguishable. Accrual/payment and estimate/final replacements do not double cost; overlapped departmental allocations cannot inflate hours.

### MET-009 — Prime cost

**Status:** Optional — proposed. **Source:** [SRS MET-009](SRS.md#met-009--prime-cost).

**Definition:** Where selected, prime cost = defined COGS + defined labour cost; prime cost % divides by matched net revenue. Both component scopes and non-overlapping inclusions are explicit.

**Acceptance / input qualification:** MET-009.AC1 — A/T: independently recompute from the eligible fixture inputs. Missing one component means incomplete/unavailable prime cost, not the other component alone called total. No universal prime-cost target is adopted.

### MET-010 — Budget variance and forecast error

**Status:** Mandatory — proposed. **Source:** [SRS MET-010](SRS.md#met-010--budget-variance-and-forecast-error).

**Definition:** Budget variance = actual − approved budget for same metric/version, period and population; favourable direction is measure-specific. Forecast error has agreed sign, issued version/horizon and eligible actual basis; absolute or relative summaries need explicit weighting/denominator rules.

**Acceptance / input qualification:** MET-010.AC1 — A/T: independently recompute from the eligible fixture inputs. No approved budget means unavailable budget variance. Outcome evaluation uses the pre-outcome forecast version; zero actual in a relative-error calculation follows the agreed unavailable/alternative rule.

### MET-011 — Reconciliation exception count, amount and age

**Status:** Mandatory — proposed. **Source:** [SRS MET-011](SRS.md#met-011--reconciliation-exception-count-amount-and-age).

**Definition:** Count and compatible outstanding amount of explicitly typed unresolved exceptions, as of a stated time. Age starts at the agreed business event; partial resolution changes remaining amount with history. Cash variance is observed − expected; settlement sign convention is declared.

**Acceptance / input qualification:** MET-011.AC1 — A/T: independently recompute from the eligible fixture inputs. Resolved/superseded issues are not counted as current unresolved work. Unknown start/value remains unknown, not zero age/value; invoice, cash and staffing exception quantities are not summed as a single monetary loss.

### MET-012 — Applicable control and verified action outcomes

**Status:** Mandatory — proposed. **Source:** [SRS MET-012](SRS.md#met-012--applicable-control-and-verified-action-outcomes).

**Definition:** Applicable overdue controls, expired required evidence and completed-and-verified actions use validated applicability, due/validity dates and completion criteria.

**Acceptance / input qualification:** MET-012.AC1 — A/T: independently recompute from the eligible fixture inputs. Unknown applicability is reported separately from pass/fail or overdue. Locally marked complete but unverified work is not counted as verified resolution. The expected DR-055 population includes due occurrences with no observation; AT-11.DUE verifies separate due and outcome states.

### MET-013 — Forecast revenue and product-sales outputs

**Status:** Mandatory — proposed. **Source:** [SRS MET-013](SRS.md#met-013--forecast-revenue-and-product-sales-outputs).

**Definition:** Revenue outlook declares money/currency/tax basis and revenue scope. Product outlook declares product/variant, expected sale units and optionally distinct monetary sales under price assumptions. Both include issue time, evidence cutoff, horizon, version, driver assumptions and uncertainty.

**Acceptance / input qualification:** MET-013.AC1 — A/T: independently recompute from the eligible fixture inputs. A quantity forecast does not appear as money; expected money is not units times an unstated price. Stockout-constrained sales, new products and missing history are disclosed; inadequacy produces qualified scenarios/unavailable results.

### MET-014 — Availability, stockout and supplier outcomes

**Status:** Mandatory — proposed. **Source:** [SRS MET-014](SRS.md#met-014--availability-stockout-and-supplier-outcomes).

**Definition:** Stockout/availability incidents require direct observation or explicit evidenced unmet demand with time/scope. Supplier timeliness/in-full comparison needs promised and accepted quantities/times and agreed eligibility. Excess/usable holdings use agreed criteria.

**Acceptance / input qualification:** MET-014.AC1 — A/T: independently recompute from the eligible fixture inputs. Zero sales alone is not a stockout; a rejected delivery is not full usable supply. Unknown promised time prevents an on-time score; unknown excess policy prevents authoritative excess classification. DR-053 identifies promised tranches and selected scoring baseline; DR-052 identifies quantity-specific availability and overlaps. Verify AT-06.PROMISE and AT-07.HOLD.

### MET-015 — Report automation outcome

**Status:** Mandatory — proposed. **Source:** [SRS MET-015](SRS.md#met-015--report-automation-outcome).

**Definition:** Report preparation effort is observed person time for assembly, checking and corrections under an agreed report definition; timeliness is ready/status time against the agreed deadline. Compare equivalent report scope/coverage and record manual interventions/reissues.

**Acceptance / input qualification:** MET-015.AC1 — A/T: independently recompute from the eligible fixture inputs. Less preparation time with more hidden gaps or correction work is not unqualified improvement. No baseline or agreed deadline means the corresponding improvement/timeliness conclusion is unavailable. AT-01.EFFORT sums report/revision/stage-linked DR-044 observations in a declared comparison window; elapsed readiness time is not person-time.

### MET-016 — Operating profit and cash commitments

**Status:** Mandatory — proposed. **Source:** [SRS MET-016](SRS.md#met-016--operating-profit-and-cash-commitments).

**Definition:** Operating/net profit uses a validated account/expense/period scope beyond gross profit. Cash position, due payables, customer liabilities and future commitments have separate money/status/as-of definitions, not implicit profit classification.

**Acceptance / input qualification:** MET-016.AC1 — A/T: independently recompute from the eligible fixture inputs. Missing rent/other included expenses prevents a complete net-profit claim. Open purchase commitments remain commitments rather than paid expenses; cash received cannot replace revenue recognition evidence. DR-049 recognised costs reconcile to the selected expense scope with operational/accounting overlaps counted once. Verify AT-09.POS, AT-09.INC and AT-09.REV; aggregate-only evidence remains explicitly aggregate-only.

## Responsibility validation and acceptance

For every adopted workflow, record the named performer, outcome owner, verifier, approver, delegation limits and absence cover; separately record allowed information, purpose, scope, operations and effective interval. An unanswered field stays unresolved and blocks authority-dependent acceptance (GOV-001.AC1, ACC-001.AC1–ACC-008.AC1).

Validate an ordinary case and an exception with actual role holders, confirm which KPI they evaluate and whether the action has observable value (GOV-002.AC1, UX-018.AC1). Do not turn candidate KPI lists into mandatory dashboard contents or staff performance scores. Duties, measure selection and access questions remain in [OPEN-DECISIONS.md](OPEN-DECISIONS.md), especially OD-01/OD-02/OD-09/OD-10/OD-13/OD-14.

Revision 1.1 input chains: service review consumes DR-054 stage/quantity events; applicable control review consumes DR-055 expected occurrences and DR-043 observations. These facts belong in the existing assigned decision workflows. [Worked verification fixtures](SRS-VERIFICATION-FIXTURES.md) link each affected KPI to its inputs and expected results; no responsibility, grant, target or new primary destination is assigned.
