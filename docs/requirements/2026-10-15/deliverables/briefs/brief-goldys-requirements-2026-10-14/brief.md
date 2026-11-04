---
title: "Product Brief: Goldy's Unified Data Platform"
status: complete
created: 2026-10-14
updated: 2026-10-14
---

# Product Brief: Goldy's Unified Data Platform

## Purpose and business problems

Goldy's needs unified, dependable business evidence. Staffing, stock through the next delivery, and the owner's second-pub opening in approximately 6–12 months are plausible opportunities raised in coaching; their selection and relative importance require stakeholder validation. The platform must unify relevant information, make its limitations visible, and support decisions, proposed corrections and follow-up. Connected systems remain read-only; users may save local annotations and follow-up records.

The user reported three problem areas. These examples support discovery; they do not establish which business decisions are most important or constitute stakeholder-validated requirements:

- **Staffing does not reliably match demand or workload.** Managers cannot reliably anticipate attendance, product mix or workload. Cocktail preparation competes with other duties; short-notice sickness can leave unfilled gaps. Sales and customer counts alone do not establish required effort or skills.
- **Stock decisions lack dependable demand and replenishment evidence.** Managers cannot reliably determine quantities needed until the next delivery. Hot-day Aperol and busy-dinner parma stockouts illustrate the need to understand product mix, ingredients, preparation and supply timing.
- **The second pub's opening has not been considered end-to-end.** The owner needs current-pub evidence to inform opening choices, with differences and transfer assumptions explicit.

The charter requires consistent business meanings and underlying facts. Research supports this need; it does not establish Goldy's policies or quantify financial impact.

## OPEN STAKEHOLDER DECISIONS: key business decisions and priorities

**Unresolved question:** Which business decisions are most important to improve at Goldy's, and how should they be prioritised? The user did not have sufficient evidence to establish or rank them during the original workflow. No selection or ranking has been validated with stakeholders.

**Stakeholders to resolve it:** the owner, general manager and relevant department managers, including front-of-house managers and the head chef/kitchen manager; involve sub-head chefs and other responsible managers where relevant. Validate the actual decisions, current workflows, business impact, information gaps and success measures before agreeing priorities. Proposed decisions, workflows, KPIs and feature sequencing remain **HYPOTHESES**, not confirmed business requirements; list order does not imply rank.

**Impact on the PRD and UX specification:** decision-specific requirements, acceptance measures and feature order cannot be treated as agreed on this basis. Role journeys, primary tasks, information hierarchy and KPI prominence remain provisional until stakeholders validate them. Preserve the charter's information domains and agreed product boundaries; do not infer an initial feature set from these examples. Validation evidence and agreed priorities must be recorded before promoting hypotheses into confirmed requirements.

## Intended users and decision-makers

- **Front-of-house managers:** user-reported responsibility for front-of-house rostering and ordering; demand, workload, coverage, stock and delivery evidence are hypothesised needs.
- **Head chef:** user-reported responsibility for back-of-house rostering and ordering; anticipated food demand, preparation effort, skills, ingredients and supply evidence are hypothesised needs. **Sub-head chefs** also order back-of-house items; their rostering authority is unconfirmed.
- **Owner:** intended recipient of current-pub evidence for second-pub opening planning; specific opening decisions and financial scenarios remain hypotheses. Approval limits remain open.
- **General manager, venue manager and operational staff:** intended users named by the charter; actual duties, decision authority and information access require validation. Staff need relevant task information and local evidence annotations.
- **Bookkeeping/payroll and advisers:** candidate supporting users for interpreting financial, labour and applicable control evidence; access and responsibilities require confirmation.

Responsibility, authority and access are separate. One person may perform several roles. Decision chains are in the [addendum](addendum.md).

## Hypothesised operational outcomes and candidate information

The following are plausible opportunities to validate, not selected or prioritised business requirements. Proposed information supports each hypothesis:

1. **Plan staffing against expected demand and workload**, identify coverage risks and compare plans with actual service/work. Information includes reservations and actual covers, sales and product mix, service timing where observed, preparation/task effort, required skills, availability, roster versions, actual work/breaks, absences and defined labour costs. Forecast uncertainty must be visible.
2. **Plan stock through replenishment**, assess usable holdings, expected usage and shortage/excess risks. Information includes stock locations/counts/movements, recipes and yields, product/modifier demand, recorded waste, supplier packs/conversions, outstanding orders, accepted deliveries, lead times and purchase costs.
3. **Explain performance and exceptions**, keeping sales separate from receipts, purchases from consumption, known waste from unexplained variance, and rostered work from actual work and pay. Information includes atomic transactions, items, menus/prices/discounts, payments/settlements, invoices/credits, comparable periods, budgets and explicit KPI definitions. Estimates are distinct from final accounting results.
4. **Make evidence-based opening decisions**, using current-pub demand, workload, service, stock, supplier and cost evidence to compare scenarios for the second pub. State each scenario's assumptions, gaps and sensitivity to location, customer and operating-model differences.

Reviews, bookings, articles about the pub and relevant internal work records, including read access to Asana, remain in scope where they serve a validated business purpose; the proposed outcomes above are candidates for that validation. External context such as weather is a candidate input requiring validation. Public sentiment is contextual evidence, not a direct measure of attendance or demand.

Facts and measures need identities, provenance, effective times, units, currencies, scope, correction history and completeness. Preserve legitimate zero, missing, unknown, disputed and not-applicable states. Missing sources do not remove a business information need.

## Product scope, capabilities and boundaries

Business requirements describe what Goldy's must know and achieve. Candidate capabilities include collecting and relating evidence, explaining measures, supporting qualified forecasts/scenarios and surfacing relevant exceptions; their decision-specific value and priority remain hypotheses. Local annotations, proposed corrections and follow-up records remain permitted. These capabilities do not prescribe screens, navigation, algorithms, integrations or architecture, or establish which features should be developed first.

The operating scope is the current Australian pub with bar and kitchen service, plus evidence-informed planning for the second pub. Second-pub live operations and consolidated multi-venue management require a separate scope decision. The charter's information domains remain in scope; staffing, stock and opening examples establish neither decision priority nor permission to discard other domains. Relevant control evidence is conditional on verified applicability and business purpose.

## Hypothesised successful use

These examples describe possible successful use, pending stakeholder validation of decisions and success measures; they do not establish KPI priority:

- Rostering managers can explain expected workload and skills coverage, compare rostered capacity with actual demand/work, and identify avoidable shortages or excess.
- Ordering managers can explain stock needed through the next delivery, identify uncertainty and shortage risk, and review availability, waste and excess after acting.
- The owner can explain opening choices using qualified scenarios, trace assumptions to current-pub evidence, and identify what must be tested at the new pub.
- Users can trace a measure or exception to its facts and definition, distinguish unavailable evidence from zero, and track local follow-up without altering source records.

Evaluation must compare agreed service periods and evidence coverage. Baselines, numerical improvement targets, decision deadlines and acceptable forecast error remain to be agreed. Evaluation proposals and open decisions are in the addendum; demand, deliveries and staff availability are not guaranteed.

## Explicit non-goals

- Executing or writing back rosters, orders, bookings, payments, payroll, stock adjustments or tasks in connected systems; operating as a replacement transaction system.
- Treating local proposed corrections as corrected source facts, or local follow-up as completed external work without evidence.
- Reproducing the current application's features, navigation, schemas or vendor definitions as requirements.
- Building a marketing pitch, public-relations tool or unrestricted archive of everything mentioning the pub.
- Automatically setting business policies, approval limits, wage rules, compliance obligations or universal KPI targets; claiming profit or compliance without sufficient evidence.
- Guaranteeing forecasts, automatic resource decisions, or unqualified transfer of current-pub results to the new pub.
- Selecting technology, modifying application code/databases/infrastructure, deploying, or initiating implementation planning.

## Evidence and status

Product brief update complete; selection and prioritisation of key business decisions remain OPEN STAKEHOLDER DECISIONS. Authority: [project charter](../../../PROJECT_CHARTER.md) and user-provided context and correction of 14 October 2026, recorded in the [decision log](.memlog.md). Reported examples and responsibilities are distinct from stakeholder-validated decisions; proposed outcomes, workflows, KPIs and priorities remain hypotheses. Supporting evidence: [completed domain research](../../research/domain-australian-pub-operational-information-m-2026-10-14/research.md), [proposed business domain model](../../../research/business-domain-model.md), and [proposed capability map](../../../research/business-capability-map.md). Existing implementation was not inspected. Source limitations remain in force; research proposals are not silently promoted to Goldy's policy.
