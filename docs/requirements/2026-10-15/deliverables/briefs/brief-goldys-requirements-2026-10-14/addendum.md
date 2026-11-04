---
title: "Goldy's Product Brief — Supporting Detail"
status: complete
created: 2026-10-14
updated: 2026-10-14
---

# Goldy's Product Brief — Supporting Detail

This companion to [brief.md](brief.md) preserves operational examples, hypothesised decision chains and validation needs. It does not approve detailed requirements, metric conventions, role permissions, priorities or implementation choices.

## OPEN STAKEHOLDER DECISIONS: selection and prioritisation

**Unresolved question:** Which business decisions are most important to improve, and how should Goldy's prioritise them? The user lacked sufficient evidence to answer during the original workflow. Staffing, stock/replenishment, performance/exception review, second-pub opening scenarios and evidence follow-up remain plausible opportunities, not an agreed shortlist or ranked priorities. No decision-specific workflow, KPI priority or first feature set is validated by these examples.

**Stakeholders to resolve it:** the owner, general manager and relevant department managers, including front-of-house managers and the head chef/kitchen manager, with sub-head chefs and other responsible managers involved where relevant. Their actual responsibilities and approval authority must be validated separately.

**Evidence needed:** stakeholder accounts of actual decisions and current workflows, concrete examples of business impact and information gaps, decision frequency and timing, and the success measures they use or need. Validate proposed alternatives and agree selection and priority rather than assuming the coaching examples are exhaustive. Record who validated each decision, the evidence and rationale, agreement or unresolved disagreement, and any resulting KPI and feature priorities.

**Impact on the PRD:** decision-specific requirements, scope priorities, acceptance measures and feature sequencing remain provisional. The charter's domain coverage and agreed product boundaries still apply; hypotheses must be explicitly labelled and traced to this open question until validated.

**Impact on the UX specification:** role journeys, primary tasks, information hierarchy, KPI prominence and workflow order cannot be finalised from the hypothesised chains alone. Validation may change which opportunities are pursued and how the experience is organised. Do not use list order or available data as evidence of business priority.

No stakeholder answer or priority is supplied here, and this update authorises no PRD or UX workflow.

## User-reported context and agreed boundary

On 14 October 2026, the user supplied the following context and product boundaries. These are not evidence of stakeholder validation of key business decisions or priorities:

- Front-of-house managers roster and order front-of-house items. The head chef rosters back-of-house; the head chef and sub-head chefs order back-of-house items.
- Managers experience both under- and overstaffing, uncertain busyness/product demand/customer activity, and short-notice sickness that cannot always be covered. Cocktail preparation is labour intensive and takes staff away from other responsibilities.
- Managers lack dependable quantities to sustain stock until the next delivery. Examples are hot-day Aperol depletion and selling out of parmas during a busy dinner. These are user-reported examples; no frequencies, quantities or attributable losses were supplied.
- The owner has purchased another pub, expected to open in approximately 6–12 months from this discussion. Opening decisions should be informed end-to-end by evidence from the current pub.
- The platform collects relevant information, including reviews, bookings, articles and read access to internal platforms such as Asana. All connected systems remain read-only. Local annotations and follow-up records are permitted.

**HYPOTHESES — opening decision areas:** staffing/skills, menu/preparation workload, stock/suppliers, demand/capacity and financial scenarios are plausible opportunities, pending stakeholder validation of selection and priority. Any scenarios must make transfer assumptions explicit.

The reported problems and responsibilities provide discovery context; the read-only boundary and permitted local records remain agreed. They do not establish prioritised business requirements, validated decision chains, every current workflow or authority to commit spend, change prices, publish rosters or release funds.

## HYPOTHESES: decision chains for stakeholder validation

Every chain below is a hypothesis, not a confirmed business requirement or validated current workflow. Rostering and ordering responsibilities are user-reported context; stakeholder validation of the specific decisions, information, actions and measures remains open. Actions, if validated, occur through authorised business processes outside connected-system write access. List order does not imply decision, KPI or feature priority.

- **Front-of-house manager → roster front-of-house → decide service coverage and skill mix → expected attendance, drink/product mix, cocktail effort, other duties, availability, roster and actual hours → adjust/publish staffing through the existing process → compare coverage gaps, excess capacity, service outcomes and labour productivity on agreed bases.** Headcount or revenue alone cannot establish workload. Preparation effort and competing duties need observations or labelled estimates.
- **Head chef → roster back-of-house → decide preparation/service coverage → expected food mix/covers, preparation work, skills, availability and actual service/work → adjust the kitchen staffing plan externally → compare preparation/fulfilment outcomes and planned-versus-actual coverage/cost.** Sub-head-chef rostering authority is not established.
- **Front-of-house manager → order front-of-house items → decide quantities/timing through the next delivery → usable stock, demand scenarios, recipes/pours, pack conversions, outstanding orders and supplier timing → place/amend orders externally → review evidenced availability, excess holdings, waste and delivery exceptions.** Aperol is the supplied hot-weather example; weather sensitivity is an unvalidated explanatory hypothesis.
- **Head chef/sub-head chefs → order back-of-house items → decide ingredients and preparation needs → expected dishes/modifiers, portions/yields, usable stock, waste and replenishment timing → order or revise preparation externally → review shortages, waste, excess and usable yield.** Parma availability depends on the ingredients and preparation needed for the saleable dish, not merely a menu-product count.
- **Owner → inform second-pub opening → decide staffing, menu/workload, supply, capacity and financial scenarios → current-pub evidence plus explicit new-site differences and assumptions → choose/test the opening plan through the responsible business process → assess evidence coverage, sensitivity and subsequently observed outcomes against the scenario.** Current-pub comparisons support hypotheses; new-pub demand and economics require local validation.
- **Relevant user → support evidence quality and follow-up [assignment to validate] → investigate a disputed fact/exception → original source evidence, definitions, local annotations and action history → propose a correction or request external action → assess unresolved issue age and independently evidenced resolution.** A local note does not change the source fact or prove that an external task was completed.

For each chain, validate performer, outcome owner, verifier, approver, delegation/absence cover and information access separately. One person may fulfil multiple functions. Existing research capability identifiers C01–C22 and entity identifiers E01–E43 provide discovery references, not approved SRS requirement identifiers.

## Information coverage and semantic safeguards

Preserve the charter's full domain coverage: sales/transactions/items/modifiers/payments; products/menus/prices/discounts; inventory/purchasing/movements/waste; recipes/ingredients/consumption; employees/positions/shifts/rosters/timesheets; labour hours/cost/productivity; reservations/covers/service periods; suppliers/invoices/purchasing costs; operational KPIs and financial measures.

Keep these distinctions when information is compared:

- Bookings versus actual attendance; check counts versus covers; order/fulfilment times versus check closure.
- Sales versus money collected or bank settlement; supplier commitments versus receipts, invoices and payments.
- Planned versus worked versus payable hours; forecast labour cost versus actual or final payroll cost.
- Physical stock/counts versus book movements; known waste versus unexplained depletion; theoretical recipe cost versus inventory valuation/COGS/profit.
- Original evidence versus a local proposed correction; recorded follow-up versus externally verified completion.
- Existing-pub facts versus new-pub scenario assumptions; public reviews/articles versus observed customer activity.

Each proposed decision input needs a purpose, provenance, applicable population/time scope, freshness appropriate to that decision and visible evidence status. Relevance is determined by a business use, not merely by source availability. Financial scenarios require defined cost coverage; incomplete accounts cannot establish final net profit. Personal information requires a validated purpose and appropriate access; the brief does not authorise unrestricted staff/customer data collection.

## Proposed success evaluation

These are HYPOTHESES for evaluation, not stakeholder-selected KPIs, approved targets, priority rankings or final metric contracts. Validate whether each proposed outcome and measure matters to the business; list order does not imply priority:

1. **Staffing:** review selected comparable services before and after use; document expected demand/workload, planned skills/capacity, actual work and service outcomes, and the manager's explanation of changes. Agree what counts as under/overstaffing and separate demand forecast error from absence and task-allocation effects. Avoid reducing labour merely to improve a percentage while service worsens.
2. **Stock:** assess whether users can explain the next-delivery horizon and assumptions, then compare stockout incidents, usable holdings, waste and excess. Stockout measurement needs direct availability evidence; zero sales alone do not prove no demand or a shortage. Consider supplier reliability and uncertain counts separately from forecast performance.
3. **Opening:** sample the agreed decision areas; each material scenario should identify evidence, missing inputs, transfer assumptions, alternatives and sensitivity. Before opening, success is defensible planning; operating results can be assessed only after observations exist.
4. **Trust and follow-up:** sample ordinary and exception cases, trace results to evidence/definitions, and verify missing-data and correction states. Trace follow-up to an owner and completion evidence where claimed. Completion and escalation policies require agreement.

For each measure, agree an accountable evaluator, baseline period/population, eligible evidence, definition and units, numerical target if appropriate, review cadence and decision deadline. Do not import universal industry targets. Research's candidate K01–K12 metric contracts remain proposals; the brief adopts their need for explicit meaning, not every formula.

## Other open decisions and unvalidated propositions

- Actual service periods, reporting cutoff, department boundaries, customer/cover definitions and operational timing needs.
- Forecast horizons, workload observation method, decision lead times, acceptable uncertainty, absence information/cover process, and available evidence quality. No guaranteed ability to fill short-notice vacancies is assumed.
- Ordering horizons, delivery schedules/lead times, usable stock criteria, recipes/yields/conversions, count practice, safety stock and substitution policies.
- Approval limits and duties not confirmed in coaching; responsibilities and purpose-based access for owner, general/venue managers, operational staff and supporting functions.
- New-pub location, customer segments, capacity, service model, opening date, resources and approved financial assumptions. Operational consolidation of both pubs is outside the agreed brief.
- Relevant source access, coverage, permitted collection and retention, including reviews/articles/booking records/internal work platforms. Asana is an explicit example, not an obligation to adopt its structure or integrate every internal platform.
- Applicable jurisdiction/licence/employment/accounting/privacy and recordkeeping rules; no detailed legal claim is established by this brief.
- Metric definitions, baselines, targets, comparability, evaluation cadence, local correction status and follow-up authority.

**[ASSUMPTION — UNVALIDATED] Weather and product mix can explain predictable workload/usage variation.** The user supplies hot-day Aperol and cocktail-workload examples, but no relationship has been measured. Validate using suitable observations; do not claim a proven causal model.

**[ASSUMPTION — UNVALIDATED] Sufficient evidence can be obtained to produce useful forecasts and opening scenarios.** Validate source coverage, observation quality and transferability. When evidence is inadequate, expose the gap or present a qualified scenario rather than inventing a value.

These unknowns do not prevent completion of this brief update. Decision selection and prioritisation require stakeholder validation before hypotheses can become confirmed decision-specific requirements or UX priorities; the other unknowns remain validation work for detailed requirements. This document authorises no subsequent workflow.
