# Business capability map — pub operations

Date: 14 October 2026. Status: proposed map for stakeholder validation.

## Purpose, evidence and accountability

This map defines what the venue needs to manage, measure and decide. It follows [PROJECT_CHARTER.md](../PROJECT_CHARTER.md), the [completed domain research report](../deliverables/research/domain-australian-pub-operational-information-m-2026-10-14/research.md), especially sections 3–8, and the accompanying [business domain model](business-domain-model.md). Entity references E01–E43 refer to that model. No Goldy's source code, existing schemas or PRD were inspected. No feature, screen, software permission or implementation is prescribed.

All responsibilities below are **proposed [V: validate with stakeholders]**. The charter names relevant roles but does not establish their actual duties, delegations or authority. The research describes an interview map, not Goldy's confirmed organisation. General manager and venue manager may be separate roles or one person. Kitchen/FOH/bar supervision may be shared. Payroll/bookkeeping is a business function and may be internal or external; do not assume it is a new staff position. Owner accountability does not imply that the owner performs every activity.

For each capability, confirm separately who **owns the outcome**, **performs the work**, **verifies the evidence**, and **approves consequential actions**, including limits and cover during absence. A title is not proof of decision authority, access rights, qualifications or employment classification. Access/privacy decisions follow validated responsibilities and purposes.

The capability groups are:

- **Commercial and service:** offer and price management; customer sales; payments and liabilities; reservations and attendance; service execution.
- **Supply and stock:** purchasing; receiving; supplier liabilities; stock custody; stock observation; recipes/production; consumption/waste.
- **People:** employment/competency; staffing plans; actual time; labour cost/pay obligations.
- **Stewardship and improvement:** cash/settlement control; accounting performance; budgets/forecasts; operational intervention; safety/licence assurance; information quality and accountability.

These capabilities are stable needs; the specific processes, roles, controls, measures and targets remain subject to validation. A required information domain can be missing today without making the capability unnecessary. Missing evidence changes the decision's confidence and the measure's availability.

## Commercial and service

### C01 Manage the offer, availability and pricing

- **Responsibility [V]:** General/venue manager owns commercial offer performance; kitchen manager defines food portions/availability; FOH/bar manager defines beverage/service availability. Owner approval of prices or spend limits is unconfirmed.
- **Business process:** Introduce/revise products and menus; cost recipes; agree price terms/promotions; communicate effective availability and substitutions; review results.
- **Required information:** Products, menu/price versions, sale quantities and historical prices, discounts/comps, recipe ingredients/yields, purchase-cost basis, stock availability and service constraints (E04–E10, E17, E21–E28). Cost evidence is required for margin decisions; demand alone can support availability decisions.
- **KPI / outcome:** Net sales and sales mix; realised unit revenue; theoretical ingredient contribution with its basis; product availability. Measure unavailability only if observation/opportunity evidence exists.
- **Decisions / actions:** Change portions/prices, amend the menu, schedule a promotion, pause an unavailable item, approve a substitution with appropriate evidence. Validate who approves each change and how kitchen/FOH communicate it.

### C02 Manage customer sales and commercial exceptions

- **Responsibility [V]:** FOH/bar manager owns check accuracy and service exceptions; operational staff record orders/fulfilment; venue manager verifies/approves comps, voids and reversals within agreed limits.
- **Business process:** Open check, take/amend orders and modifiers, fulfil, apply eligible reductions, close/split/merge, investigate cancellation/return/refund requests.
- **Required information:** Check/line identity, order/fulfilment evidence, historical consideration/tax, selected modifiers, financial adjustments, reasons and applicable authorisations (E07–E10); payment status is separate (E11–E12).
- **KPI / outcome:** Correct charge reconciliation; net sales; comp/discount/void/reversal values and reasons; unresolved commercial-exception count/age. Exception frequency is a prompt to investigate, not proof of misconduct.
- **Decisions / actions:** Correct a charge, authorise a comp, cancel unfulfilled items, arrange re-service, issue a commercial credit and separately authorise any cash refund. Prepared-but-cancelled product may require a separately evidenced waste record.

### C03 Manage tender, customer balances and stored value

- **Responsibility [V]:** FOH/bar manager owns collection and open-tab follow-up; operational staff receive authorised tenders; bookkeeping verifies allocations/liabilities; venue manager/owner approves refunds, credit limits and expiry policies as delegated.
- **Business process:** Receive/allocate tender, handle part payments and tabs, issue/redeem deposits/vouchers, refund money, reconcile customer balances and liabilities.
- **Required information:** Check consideration, money movements/statuses, allocations, liability entries, reservation deposit links and accountant-approved recognition/tax/expiry rules (E07, E10–E13, E37).
- **KPI / outcome:** Unpaid check balance/age; unapplied receipts; outstanding customer liability; reconciliation exceptions. Cash collected and sales remain distinct outcomes.
- **Decisions / actions:** Seek outstanding payment, resolve misallocation, apply a deposit to an eligible bill, release an authorised refund, investigate over-redemption. Validate liability ownership, refund authority and treatment before treating voucher receipts as revenue.

### C04 Manage reservations, attendance and capacity

- **Responsibility [V]:** FOH manager owns booking/arrival coordination; operational staff confirm attendance/covers; kitchen manager contributes preparation constraints; venue manager resolves exceptional capacity commitments.
- **Business process:** Accept/amend/cancel reservations, agree deposit arrangements, anticipate arrivals, seat walk-ins/bookings, observe covers, assess no-shows and release capacity.
- **Required information:** Intended service times/party size, booking outcomes, deposits, actual visits/covers, table/area capacity and assignment history, service-period definitions (E02–E03, E13, E37–E39). Customer identity is needed only for a demonstrated booking/contact purpose.
- **KPI / outcome:** Booked covers versus actual covers; eligible reservation no-show rate; seat/capacity utilisation only where capacity and occupancy duration are evidenced; aligned spend per actual cover.
- **Decisions / actions:** Accept/decline or move a booking, adjust seating, release a no-show table under agreed policy, notify kitchen of changed attendance, revise staffing/preparation forecasts. Confirm cover definition, re-entry and split-party rules first.

### C05 Coordinate service and fulfilment

- **Responsibility [V]:** FOH/bar and kitchen managers jointly own the service handover; operational staff perform preparation/service; venue manager resolves cross-team constraints. Validate one escalation owner during each service.
- **Business process:** Brief service, prioritise preparation/orders, communicate amendments/availability, deliver items, respond to delays/complaints and close service.
- **Required information:** Actual demand/arrivals, order and fulfilment times/statuses, modifiers and recipe instructions, available items, work coverage and relevant control evidence (E05, E08–E09, E22–E23, E33–E35, E38, E43).
- **KPI / outcome:** Order-to-fulfilment duration distribution and coverage; unfulfilled-item backlog; recorded service incidents and correction outcomes. Check closure time is not serving time.
- **Decisions / actions:** Reassign floor/bar tasks, sequence preparation, communicate waits, stop an unavailable item, remake/re-service or escalate an incident. Targets depend on the venue's service promise and measured evidence.

## Supply and stock

### C06 Plan and control purchasing commitments

- **Responsibility [V]:** Kitchen manager proposes food orders; FOH/bar manager proposes beverage orders; venue manager coordinates supplier commitments; owner/manager approves spend under unconfirmed delegations.
- **Business process:** Forecast needs, review stock and outstanding orders, compare pack/price/lead-time terms, order, approve/amend/cancel and follow up supply.
- **Required information:** Demand/booking forecasts, usable stock and uncertainty, recipes/yields, supplier items/conversions, lead times, order quantities/prices, outstanding accepted deliveries, budget/cash constraints (E16–E24, E26–E28, E37, E42).
- **KPI / outcome:** Evidenced availability/stockout incidents; outstanding order exposure; forecast-versus-usage variance; purchasing against approved plan. Supplier on-time/in-full performance requires promised and actual receipt evidence.
- **Decisions / actions:** Set order quantities/timing, expedite, substitute, consolidate orders or change supplier. Reorder thresholds and approval limits require validation; no automatic industry target is assumed.

### C07 Receive and verify goods

- **Responsibility [V]:** Operational staff or designated receiving staff perform checks; kitchen/FOH/bar manager owns acceptance within their area; venue manager resolves supplier discrepancies. Confirm who may reject/conditionally accept goods.
- **Business process:** Compare delivered goods with commitments, inspect quantity/condition, record accepted/rejected parts, identify substitutions, place into stock and raise claims.
- **Required information:** Supplier identity, order if available, delivered item/pack quantities, conversions, inspection evidence, acceptance disposition, stock location and conditional lot/expiry information (E16–E21, E24, E43).
- **KPI / outcome:** Accepted versus ordered quantity; damaged/rejected quantities; unresolved delivery discrepancies; receiving-evidence completeness and corrective actions.
- **Decisions / actions:** Accept, quarantine/reject under applicable procedures, record a substitute, request replacement/credit, escalate unsafe or unidentified goods. Receiving a delivery does not establish that its invoice is correct or paid.

### C08 Validate supplier charges and manage amounts payable

- **Responsibility [V]:** Bookkeeping owns payable reconciliation; receiving/department managers verify quantity/price claims; venue manager/owner approves disputed charges and payment release within agreed limits.
- **Business process:** Receive invoices/credits, compare with orders and actual acceptance where available, allocate part/consolidated documents, resolve discrepancies, schedule and reconcile payments.
- **Required information:** Independently identified orders, receipts, invoices and credits; line/document matches; currency/tax/terms/due dates; payment allocations and cash commitments (E11–E12, E16–E20).
- **KPI / outcome:** Outstanding payable balance/age; due/overdue obligations; unmatched/disputed charges; accepted-versus-invoiced quantity/price differences and unreceived credits.
- **Decisions / actions:** Query a charge, obtain credit, confirm an authorised non-stock expense, approve payment or negotiate terms. Three-way matching is a candidate control, not an established mandatory venue policy.

### C09 Maintain stock custody, transfers and production records

- **Responsibility [V]:** Kitchen manager owns kitchen/preparation stock; FOH/bar manager owns beverage stock; operational staff record physical activity; venue manager owns cross-area reconciliation.
- **Business process:** Put away receipts, transfer between locations, prepare batches, record usage/disposal, investigate unexpected balances and preserve correction evidence.
- **Required information:** Item/location identities, units/conversions, accepted receipts, production/transfer legs, consumption and waste evidence, recipe versions and adjustment basis (E02, E17, E19, E21–E25).
- **KPI / outcome:** Book stock by item/location; incomplete transfer/production groups; negative/unresolved balances; movement evidence completeness. Valued stock also requires an agreed cost method.
- **Decisions / actions:** Replenish a bar, move usable stock, plan a batch, resolve unmatched transfer legs, correct a unit/receipt error. Internal transfer is neither a purchase nor venue-wide consumption.

### C10 Observe stock and resolve count discrepancies

- **Responsibility [V]:** Department managers organise counts; operational staff count; venue manager or delegated independent reviewer verifies discrepancies and approves adjustments. Independent review is a proposed control to validate.
- **Business process:** Define count scope/cutoff, observe quantities, reconcile movements during counting, recount material differences, investigate and authorise any book correction.
- **Required information:** Count session scope/completeness, item/location observations, unit conversions, matched book balance/cutoff, intervening movements, reasons and adjustment evidence (E21, E24, E26–E27).
- **KPI / outcome:** Count coverage; observed-minus-book variance in physical units and separately valued basis; unresolved discrepancy age; approved correction backlog.
- **Decisions / actions:** Recount, resolve wrong locations/conversions, locate missing receipts/transfers, make an evidenced adjustment, change count frequency. A variance alone does not establish waste or theft.

### C11 Manage recipes, yields and preparation economics

- **Responsibility [V]:** Kitchen manager owns food recipes/yields; FOH/bar manager owns beverage/pour specifications; venue manager approves commercial implications. Competent staff provide actual preparation evidence.
- **Business process:** Specify portions and ingredients, test yields, assess substitutions/allergens, cost effective versions, plan and record preparation output, review variation.
- **Required information:** Product/output identities, recipe/component versions, ingredient units/conversions, usable yields, modifier effects, input cost basis and relevant safety evidence (E04–E06, E09, E17, E21–E24, E43).
- **KPI / outcome:** Theoretical ingredient cost per usable portion; expected-versus-observed yield when batch evidence exists; completeness of recipe/ingredient mapping; prepared availability.
- **Decisions / actions:** Change batch size, portion or ingredient; standardise preparation; approve a documented substitution; recommend a menu/price revision. Purchase pack cost alone does not establish portion cost.

### C12 Measure consumption, known waste and unexplained usage

- **Responsibility [V]:** Kitchen and FOH/bar managers own usage/waste within their scopes; staff record known loss; venue manager coordinates investigation and approves corrective actions.
- **Business process:** Record observed consumption/disposal, calculate theoretical usage using effective recipes/modifiers, reconcile counts/movements, classify known losses and investigate residuals.
- **Required information:** Fulfilled/eligible sold quantities, recipe versions/yields/modifiers, compatible opening/closing counts and movement scope, measured/estimated waste reasons and optional valuation evidence (E08–E09, E21–E28).
- **KPI / outcome:** Theoretical usage; observed/residual depletion distinctly labelled; physical/residual minus theoretical usage variance; recorded waste quantity/value by reason; input completeness.
- **Decisions / actions:** Adjust preparation/order quantities, improve handling/portion control, test a yield/conversion assumption, investigate an unexplained residual. Do not assign a cause or staff blame from a variance alone.

## People and work

### C13 Establish employment duties, availability and competency

- **Responsibility [V]:** General/venue manager owns engagement/duty assignments; payroll/adviser verifies applicable pay terms; kitchen/FOH managers verify task competency; owner approves engagement terms where delegated.
- **Business process:** Engage workers, define duties/availability, establish applicable employment terms, verify required task evidence, review changes and renewals.
- **Required information:** Employee identity, employer/assignment history, actual duties, role definitions, availability, applicable instrument/classification evidence and qualification validity (E29–E31, E43).
- **KPI / outcome:** Relevant competency coverage; unresolved employment-term questions; expiring required evidence; staffing availability. Applicability must be established before reporting compliance.
- **Decisions / actions:** Assign duties, seek payroll/legal clarification, arrange training/renewal, restrict a task where an established rule requires valid competency, amend engagement terms through authorised processes. Titles alone establish none of these rules.

### C14 Plan staffing and publish rosters

- **Responsibility [V]:** Kitchen and FOH/bar managers propose departmental coverage; venue manager coordinates/publishes the roster; owner approves labour budgets if that is the agreed delegation.
- **Business process:** Forecast demand, determine skill/role coverage, check availability and applicable constraints, cost proposed shifts, publish and communicate changes/swaps.
- **Required information:** Reservations and demand history, service capacity, employee availability/duties/competencies, effective employment terms, roster versions/planned shifts and forecast cost assumptions (E02–E03, E29–E33, E36–E38, E42–E43).
- **KPI / outcome:** Filled versus required planned coverage; forecast labour cost/percentage on an explicit basis; unfilled or conflicted shifts; publication/change timeliness against agreed policy.
- **Decisions / actions:** Add/remove/re-time a slot, change skill mix, fill an absence, authorise a swap or alter service capacity. An estimated roster cost is not actual payroll.

### C15 Record, review and approve actual work

- **Responsibility [V]:** Employees/operational staff provide actual time/break evidence; department managers verify operational attendance; venue manager/delegate approves corrections; payroll resolves pay-related queries. Confirm whether self-approval is allowed.
- **Business process:** Capture/attest starts, ends and breaks, resolve missing/overlapping records, compare with the published plan, confirm work allocations and approve timesheets.
- **Required information:** Employee and assignment, actual intervals/activity, break evidence, planned shifts, scope/allocation, review/correction history and applicable approval policy (E29–E35).
- **KPI / outcome:** Actual worked hours; roster-versus-actual hours on matched scope; missing clock-out/break evidence; unapproved/correction backlog. Paid hours are separately identified.
- **Decisions / actions:** Obtain missing evidence, correct an interval with a reason, accept unrostered work, query an overlap, approve/return the statement, change future coverage. The roster must not fill gaps in actual attendance automatically.

### C16 Determine labour cost and settle pay obligations

- **Responsibility [V]:** Payroll/bookkeeping owns calculation/reconciliation; managers verify time/duty evidence; authorised owner/manager releases pay and contributions. Professional advice and external payroll responsibilities require confirmation.
- **Business process:** Apply validated terms to approved/qualified evidence, calculate components/on-costs, review exceptions, pay, reconcile adjustments and verify relevant contribution receipt/failure evidence.
- **Required information:** Effective employment terms/classification, actual/paid time and breaks, entitlements/rates, cost allocation, payroll calculation status, payment allocations and applicable due/receipt evidence (E11–E12, E31, E34–E36, E43).
- **KPI / outcome:** Wages-only and defined loaded labour cost separately; correction backlog; unresolved payable obligations; payment/contribution timeliness against verified applicable rules; labour cost percentage and sales per declared hour basis.
- **Decisions / actions:** Resolve a classification/time query, correct a pay component, release authorised payments, investigate failed contributions, change labour forecasts. No specific award, rate, deadline or on-cost percentage is established by this map.

## Stewardship and improvement

### C17 Reconcile cash, tenders and processor settlements

- **Responsibility [V]:** FOH/bar manager or designated cash custodian performs close/count; bookkeeping reconciles settlements and bank receipts; venue manager investigates exceptions; owner approves material write-offs where delegated.
- **Business process:** Account for floats and cash movements, count holdings, compare expected cash, match processor components and fees to bank receipts, investigate timing differences and unresolved items.
- **Required information:** Money movement/status, allocations, cash observations/cutoffs, processor components, bank receipt evidence and adjustment/approval reasons (E11–E15).
- **KPI / outcome:** Observed-minus-expected cash variance; expected-minus-received settlement difference under declared fee/sign treatment; unmatched settlement count/value/age; unresolved chargebacks/fees.
- **Decisions / actions:** Recount, match a delayed receipt, query a fee, investigate an unexplained shortage, correct an allocation or approve an evidenced resolution. Net bank settlement is not a substitute for gross customer sales.

### C18 Establish comparable financial performance

- **Responsibility [V]:** Owner owns financial sustainability; bookkeeping/accounting function prepares/verifies financial measures; general/venue manager owns operational interpretation and response.
- **Business process:** Agree accounting/management scopes, reconcile revenue, inventory/period costs, labour and other expense evidence, assess commitments/cash separately, review and restate results when evidence changes.
- **Required information:** Sales/adjustments/tax, purchase/credit evidence, opening/closing stock quantities and valuations, classified losses, cost-method evidence, labour components, other expense/accountant statements, monetary commitments and reporting periods (E03, E07–E28, E36, E40–E41).
- **KPI / outcome:** Reconciled net revenue, COGS, gross profit/margin, category cost percentages, defined labour/prime cost and, with sufficient account scope, operating/net profit. Cash balances and commitments are separate measures.
- **Decisions / actions:** Change pricing/cost structure, renegotiate terms, contain expenses, seek funding or investigate unexplained results. Label theoretical estimates and provisional periods; do not present incomplete cost coverage as final profit.

### C19 Set budgets, forecasts and performance targets

- **Responsibility [V]:** Owner approves financial objectives; general/venue manager coordinates the plan; department managers propose demand, purchasing and staffing assumptions; bookkeeping verifies comparability.
- **Business process:** Define measurable outcomes, build budget/forecast assumptions, approve versions, compare matched actuals and revise forecasts without overwriting the budget.
- **Required information:** Versioned metric definitions, historical results with completeness, service/demand scenarios, cost/price assumptions, planned staffing/purchasing, plan versions and approval history (E03, E32–E33, E37–E42).
- **KPI / outcome:** Actual-minus-budget variance; forecast error on a declared horizon; exposure from commitments; achievement of agreed targets. Favourable direction is measure-specific.
- **Decisions / actions:** Set/revise targets, allocate budget, defer expenditure, change commercial/staffing plans or revise the forecast. No industry target or absent budget is treated as a venue-approved value.

### C20 Review operations and act on exceptions

- **Responsibility [V]:** Venue manager coordinates daily/service reviews; kitchen/FOH managers own agreed actions in their areas; general manager/owner resolves cross-business trade-offs; staff escalate evidence and incidents.
- **Business process:** Review demand, availability, work coverage, service, cash/stock and safety exceptions; prioritise by business effect; assign actions and assess the next comparable result.
- **Required information:** Relevant operational facts and evidence gaps, current demand/coverage, reconciled/qualified measures, targets, reasoned exceptions and action evidence (E03, E07–E43 as applicable). This capability consumes other domains; it does not create duplicate sales, stock or labour facts.
- **KPI / outcome:** Named operational outcomes, unresolved exception age and verified action completion; service/productivity/margin changes interpreted with context and completeness.
- **Decisions / actions:** Reassign resources, escalate supply/pay/safety issues, change tomorrow's preparation/roster, commission a recount or investigate a persistent variance. Confirm action owner, deadline and verification method rather than treating a dashboard view as the outcome.

### C21 Manage applicable safety, licence and incident controls

- **Responsibility [V]:** Venue manager coordinates applicable controls; kitchen manager owns relevant food-handling procedures; FOH/bar manager owns relevant service/liquor procedures; trained staff perform checks; owner/legal adviser confirms licence/entity responsibilities.
- **Business process:** Establish applicability, maintain relevant operating/competency evidence, perform controls, record incidents, take corrective action and verify resolution/renewal.
- **Required information:** Venue jurisdiction/legal operator/licence and actual business activities, current applicable requirements, competent-person evidence, control criteria/observations, incident and corrective-action history, conditional supplier/lot traceability (E01–E02, E16–E27, E29–E31, E43).
- **KPI / outcome:** Applicable control coverage; unresolved critical findings; expired required evidence; action completion. A compliance claim requires verified applicability and evidence, not just a completed checklist.
- **Decisions / actions:** Obtain current authoritative clarification, renew evidence, isolate/reject goods under applicable procedure, stop or alter unsafe activity, assign/verify corrective action. The research did not establish Goldy's jurisdiction, licence conditions or full obligations; this map adds no universal legal rule.

### C22 Govern business definitions, evidence and responsible use

- **Responsibility [V]:** General/venue manager owns operational definitions and evidence quality; bookkeeping/accountant owns accounting interpretation; department managers resolve domain gaps; owner/authorised privacy or records adviser approves applicable access/retention policies.
- **Business process:** Agree definitions, validate units/identities, review incomplete/disputed facts, preserve corrections, establish purpose-based access and applicable record retention, and verify responsibility/delegation changes.
- **Required information:** Metric versions, provenance/status, correction links, missing-information reasons, unit conversions, role/duty/approval evidence and verified record-class obligations (all domain contracts; particularly E01, E17, E30–E31, E40–E43).
- **KPI / outcome:** Completeness by business population; unresolved reconciliation/mapping/definition issues; proportion of decision measures qualified or unavailable; timely authorised correction and retrieval of evidence.
- **Decisions / actions:** Ask for missing evidence, resolve duplicate identity or incompatible units, qualify/suspend an unreliable KPI, agree a definition, assign a correction owner, establish justified access/retention. Diagnostic work supports the operating decision and is not the primary venue workflow.

## Candidate KPI contracts

These definitions are proposals for agreement, not benchmarks. Each use requires E40–E41: definition/version, scope/population, time basis, units, tax/cost basis, status, provenance and completeness. Currency is stated explicitly; revenue comparisons normally use an agreed tax-exclusive basis. If inputs are incomplete, report unavailable or explicitly partial/estimated with coverage. If a denominator is missing or zero, a ratio is unavailable with a reason; it is not zero or infinity. Valid zero observations remain zero.

- **K01 Net sales and sales mix — C01–C03, C18–C20.** Eligible item consideration less agreed revenue reductions/refunds, excluding collected tax and separately classified tips/liabilities, with surcharge treatment explicit. State whether modifier effects are already included. Mix is category/product eligible net sales divided by total eligible net sales, or a separately named quantity mix with compatible units. Negative periods are possible after reversals; disclose them rather than forcing positive shares.
- **K02 Spend per cover / average check — C01, C04, C18–C20.** Spend per cover = eligible net sales for the agreed attendance scope divided by actual covers for that same scope. Average check = eligible check sales divided by eligible check count. State check/visit linkage coverage, bar-only/children/re-entry rules, split/merge treatment and attribution date. Booked covers cannot replace missing actual covers.
- **K03 Service duration — C05, C20.** Fulfilment instant minus order instant for each eligible fulfilled item/line or explicitly defined check service stage. State handling of partial fulfilment and amended orders. Report count/coverage and useful distribution such as median and upper percentile; exclude no-timestamp cases visibly. Closing time is not a fulfilment proxy.
- **K04 Reservation no-show rate — C04, C14.** Reservations explicitly assessed as no-show divided by reservations expected to attend under the agreed eligibility/cancellation/cutoff rules, multiplied by 100. Booking count and booked-person no-show measures are different. A missing arrival link is an unresolved outcome until the policy/evidence supports a no-show claim.
- **K05 Stock balance and count variance — C09–C10.** Book balance follows the E24 movement reconciliation in the domain model. Count variance = observed quantity minus book quantity at matched item/location/cutoff. Present physical units first; valued variance additionally requires a declared cost method/date. Missing count lines are uncounted, never zero stock.
- **K06 Usage variance / recorded waste — C11–C12.** Usage variance = declared physical or residual depletion minus theoretical usage for matching items/period/scope, stated in stock units and optionally valued. Theoretical usage uses eligible output quantities, effective recipes, modifiers, yields and conversions. Recorded waste sums evidenced quantity by compatible item/unit/reason; no universal cross-item physical total. Waste percentage requires an explicitly agreed denominator. Distinguish direct consumption, count-derived residual and known disposal; do not count linked facts twice.
- **K07 COGS, gross profit and margin — C01, C11–C12, C18–C19.** COGS is agreed carrying cost attributable to the period's goods sold. A periodic opening inventory value + eligible net inventory additions − closing inventory value calculation requires explicit treatment of conversion costs, returns, transfers, losses and other classified movements. Never use purchases or supplier payments alone. Gross profit = eligible net revenue − matched COGS; gross margin % = gross profit / net revenue × 100. Category cost % = category COGS / matching category net revenue × 100. Label losses included/excluded and avoid double-subtracting waste. Markup uses cost as denominator and is a different measure.
- **K08 Labour cost and productivity — C14–C16, C18–C20.** Labour % = defined labour cost / matching eligible net sales × 100. Sales per labour hour = matching net sales / declared worked or paid hours. Define included wages, penalties, allowances, super/leave/on-costs and allocation rules; distinguish forecast, provisional actual and final payroll. Actual worked hours subtract break intervals only according to their activity meaning; pay treatment is separately determined. No overlap/allocation double counting or mixing annual accrual with payment of the same cost.
- **K09 Prime cost — C18–C19.** Optional management subtotal = defined COGS + defined labour cost; percentage divides by matched net revenue. It is not a validated universal industry measure. Scope must prevent overlapping cost inclusions, and both inputs must be comparably complete.
- **K10 Budget and forecast variance — C06, C14, C18–C20.** Actual − approved budget for the same metric/version, scope and period. Define favourable/unfavourable separately for each measure. Forecast error specifies forecast issue date/horizon and sign convention; compare against the original forecast version, not a later overwritten value. No approved comparator means no budget variance.
- **K11 Reconciliation exceptions — C02–C03, C07–C10, C15–C17, C22.** Count/value/age of specifically defined unresolved facts, such as accepted-but-unmatched receipts, invoice credits outstanding, unapplied payments, incomplete transfers and missing clock-outs. State eligible population, as-of date, ageing start and whether partial resolution reduces remaining value. Cash variance = observed − expected. For settlements, define expected gross − refunds − fees ± reserves/other components and compare net expected with bank received, retaining timing and currency differences.
- **K12 Control/action outcomes — C13, C20–C22.** Overdue applicable controls/actions, expired required evidence and completed-and-verified corrective actions. Define applicability, due date and required completion evidence. Missing applicability is reported separately; it cannot be counted as a pass or established breach.

## Stakeholder validation agenda

1. **Owner and general/venue manager:** Confirm whether those roles are distinct; decision/accountability boundaries, approval limits, budgets, financial priorities and escalation cover. Establish who is accountable for unresolved cross-team issues.
2. **Kitchen and FOH/bar managers, with operational staff:** Walk through one service, delivery, count, waste incident, shift change and guest arrival. Confirm the performer, verifier and approver for each fact/action; identify real evidence and unavailable information. Validate bar/food boundaries, table/cover definitions and service-time observations.
3. **Payroll/bookkeeping and appropriate advisers:** Confirm employer/instrument applicability, payroll duties, pay/cost bases, accounting/valuation/recognition, GST treatment, deposits/vouchers, settlements and the authority to release funds. This is a request for validation, not an assignment of new legal duties.
4. **Authorised licence/safety/privacy/recordkeeping owners:** Confirm jurisdiction, activities, applicable controls, current authoritative requirements, competency/incident evidence and justified access/retention by record class. Research limitations remain open until verified.
5. **Each measure's proposed owner:** Agree population, time attribution, unit, tax/cost basis, estimate/final status, completeness threshold, denominator and target. Test the definition against one ordinary case and one exception, including overnight trading, missing evidence and correction.

Until validated, role assignments, thresholds, approval separation and KPI conventions retain proposal status. These two documents establish business meaning and decision needs for later requirements work; they do not authorise implementation or infer Goldy's present operating policies.
