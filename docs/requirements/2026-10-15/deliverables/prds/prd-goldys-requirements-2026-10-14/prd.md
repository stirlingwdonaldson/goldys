---
title: "PRD: Goldy's Unified Data Platform"
status: final
approval_status: stakeholder-validation-pending
created: 2026-10-14
updated: 2026-10-14
intent: create
mode: coaching
approach: journey-led
implementation_inspected: false
---

# PRD: Goldy's Unified Data Platform

## 0. Purpose, authority and requirement status

Goldy's needs dependable operational evidence that people can use quickly: automatically assembled weekly reports, useful forecasts, actionable insights and usable exports. This PRD defines the business outcomes, information and safeguards required to support those needs across the current Australian pub, plus evidence-informed planning for a second pub. It is a completed requirements document when finalised, not a declaration that every candidate requirement, policy or priority has stakeholder approval.

The primary readers are the owner, general manager, departmental managers and people responsible for operational evidence. Requirements describe what those people need to know, decide and achieve. They do not select technology, software components, vendor schemas, screens or implementation sequence.

### Authority and evidence

- **S01 — [Project charter](../../../PROJECT_CHARTER.md):** authoritative domain coverage, atomic facts, semantic discipline, role-to-decision justification, independent acceptance and documentation-only constraints.
- **S02 — [Completed product brief](../../briefs/brief-goldys-requirements-2026-10-14/brief.md) and [brief addendum](../../briefs/brief-goldys-requirements-2026-10-14/addendum.md):** agreed read-only boundary, permitted local records, reported business context and explicitly open decisions.
- **S03 — [Completed domain research](../../research/domain-australian-pub-operational-information-m-2026-10-14/research.md):** supporting evidence with confidence, applicability and freshness limitations. It establishes neither Goldy's policies nor current legal applicability.
- **S04 — [Business domain model](../../../research/business-domain-model.md):** proposed entities E01–E43, grains, relationships and semantics.
- **S05 — [Business capability map](../../../research/business-capability-map.md):** proposed capabilities C01–C22 and KPI conventions K01–K12.
- **S06 — User clarification in this PRD session, 14 October 2026:** the owner and general manager primarily want automation, particularly weekly numbers aggregated from different platforms into one spreadsheet, revenue and specific-product sales forecasts, simple fast actionable insights, CSV and formatted Excel exports. Recorded in [.memlog.md](.memlog.md). This is user-reported stakeholder intent, not a validated report specification or approval of every proposed workflow.
- **S07 — [Scope and workflow sequence](../../SCOPE_AND_WORKFLOW_SEQUENCE.md):** sequential coaching and review, planning under `deliverables/`, preserve uncertainty and stop after the requested deliverable.

S01 governs coverage; S02 and S06 govern agreed boundaries and reported intent. S03–S05 supply candidate detail. S06 narrows the brief's priority question toward reporting automation and usable insights, but does not close it or silently rank all other decisions. No existing Goldy's application repository, application PRD, source code or vendor schema was inspected. No new external research was conducted or legal threshold adopted.

### Status vocabulary and stable identifiers

- **B — Baseline:** required by the charter, agreed product boundary or necessary evidence safeguard. B is not an implementation commitment or a claim that detailed venue policies have been validated.
- **C — Candidate, stakeholder validation required:** a proposed business behaviour or detailed contract derived from evidence. It remains provisional until its named dependencies are resolved. The word “must” inside a C requirement expresses its candidate acceptance contract, not current approval.
- **CC — Conditional candidate:** applies only when the activity, justified business purpose or obligation is established. Unknown applicability remains explicit; absence of a current source does not remove charter coverage.

Every normative requirement has a permanent `GOL-` identifier: `CON` for constraints, `FR` for functions, `DR` for minimum information contracts, `SEM` for shared meanings, `ACC` for access, `MET` for analytical contracts and `NFR` for quality. IDs are globally unique, survive moves and revisions, and are never reassigned after retirement. Each requirement’s acceptance contract has the stable child ID `.AC1`; additional contracts receive `.AC2`, etc., without renumbering existing criteria. Changes retain prior wording/status and validation evidence. C01–C22, E01–E43 and K01–K12 are source references, not requirement IDs. Ordering and numeric IDs imply no priority.

Each requirement below states status, source, business capability or constraint, and acceptance. Candidate dependencies point to open decisions OD-01–OD-14. Shared rules apply to each domain contract without repeating them. Candidate validation must record stakeholder, date, concrete evidence, rationale, agreement or disagreement, revised wording and priority; a completed document alone never promotes a C or CC requirement to B.

## 1. Business vision, value and boundaries

The owner and general manager currently seek automation of a recurring reporting task: collecting numbers from different platforms into a weekly spreadsheet. Goldy's should turn that effort into a repeatable, explainable business report, make important changes and evidence gaps easy to understand, and provide useful revenue and product-sales forecasts. Users should be able to obtain a quick answer, understand its limitations, export it in a usable format and choose an authorised business action.

The business value hypothesis is less report preparation and checking effort, more dependable comparison, and better-informed operating and opening decisions. Report automation is the strongest new intent signal. Staffing, replenishment, performance, evidence follow-up and second-pub scenarios remain candidate decision areas rather than a validated ranked backlog. Forecasts and reporting speed cannot compensate for missing or misunderstood facts.

### Goals

- **BG-01:** reduce recurring cross-platform report assembly and rework while preserving correctness and explainability (S06; C18, C20, C22).
- **BG-02:** give users simple, timely evidence and forecasts relevant to their responsibilities and next decision (S01, S02, S06; C01–C20 as applicable).
- **BG-03:** establish canonical atomic facts, comparable measures and visible trust across the charter's full domain coverage (S01; C22).
- **BG-04:** inform second-pub opening choices using qualified current-pub evidence and explicit transfer assumptions (S02; C19, C20).
- **BG-05:** protect information and preserve authorised business control, including read-only connected systems (S01, S02; C21, C22).

### Constraints

#### GOL-CON-001 — Read-only connected systems

**B | Trace:** S02; BG-05; C22. Connected systems must remain read-only. Goldy's users may save local annotations, proposed corrections, scenarios and follow-up records; consequential operating actions happen through authorised external business processes.

**Acceptance — GOL-CON-001.AC1:** Reviewing or exporting a roster, order, task, booking, stock fact, payment or payroll result causes no connected-system change. A local proposal is labelled as local and does not claim source correction, external approval, publication, purchase or payment.

#### GOL-CON-002 — Full business coverage independent of source availability

**B | Trace:** S01; BG-03; C01–C22. The PRD must cover all charter information domains even when evidence is unavailable; available integrations must not determine business scope.

**Acceptance — GOL-CON-002.AC1:** Each charter domain resolves to a minimum information contract below. An absent source is recorded as an evidence gap with affected decisions and measures, rather than deleting the requirement or fabricating activity.

#### GOL-CON-003 — Human authority over decisions

**B | Trace:** S01, S02; BG-05; C20, C22. Insights, forecasts and proposed actions must not establish business policy, approve spend, change employment conditions, or execute operating decisions.

**Acceptance — GOL-CON-003.AC1:** A forecast is distinguishable from a selected plan. A suggested response remains proposed until the responsible person records a decision; that local decision still does not prove external execution.

#### GOL-CON-004 — Verified applicability before compliance claims

**B | Trace:** S01–S03; BG-05; C13, C16, C21, C22. Obligations and control thresholds must have verified applicability, current authority and a responsible validator before operational adoption.

**Acceptance — GOL-CON-004.AC1:** Unknown jurisdiction, employer coverage, licence, business category or record-class obligation produces “applicability unresolved”. No award, rate, control limit, universal retention period or compliance conclusion is inferred from a pub setting or job title.

#### GOL-CON-005 — Current pub and opening planning scope

**B | Trace:** S02; BG-04; C19. Current-pub operations and planning evidence for the second pub are in scope. Live operation of the second pub and consolidated multi-venue management require a separate decision.

**Acceptance — GOL-CON-005.AC1:** Opening assumptions have a distinct site/scenario scope; they never appear as actual second-pub results. No current-venue total silently incorporates a proposed venue.

#### GOL-CON-006 — Business-first requirements and bounded work

**B | Trace:** S01, S07. Requirements must establish business meaning independently of the existing application. This engagement performs no application, database or infrastructure change, deployment, migration, architecture selection or implementation planning.

**Acceptance — GOL-CON-006.AC1:** No requirement mandates a storage schema, platform, vendor, algorithm or framework. The package contains business contracts and verification criteria; no application inspection or downstream workflow is needed to complete it.

## 2. Users, responsibilities and candidate journeys

### Responsibilities and jobs to be done

The **owner and general manager** are the principal stakeholders for automation and quick actionable insights (S06). The owner is also the intended recipient of opening-planning evidence (S02). Whether the general manager and venue manager are separate people, who signs off reports, and who can view individual pay or sensitive financial information remain open (OD-02, OD-10).

**Front-of-house managers** roster and order front-of-house items. The **head chef** rosters and orders back-of-house items; **sub-head chefs** also order back-of-house items (user-reported, S02). These duties do not establish authority to approve expenditure, publish changes, access every employee record or approve their own evidence. Kitchen manager and head chef are not assumed to be interchangeable titles.

**Venue manager, kitchen manager and operational staff** are charter users whose actual duties need validation. Candidate staff work includes seeing relevant task evidence, contributing local observations and escalating discrepancies; the platform does not replace transactional order, clock or receiving systems. **Bookkeeping, payroll and specialist advisers** are candidate supporting functions, not newly assumed positions. A single person may hold several responsibilities with separate access purposes.

For each role and decision validate performer, outcome owner, evidence verifier, consequential-action approver, delegation limit, absence cover and information entitlement separately (OD-02). A work responsibility does not automatically grant a software permission or demonstrate a qualification.

### Journey status

UJ-01–UJ-08 are **candidate future-use narratives**, derived from the sources. They are not captured stakeholder sessions or evidence of current practice. Names below are illustrative labels, not claimed real Goldy's employees. Screen sequences, devices and interface hierarchy remain unspecified; OD-01, OD-02 and OD-12 govern validation. The business chain is role → responsibility → decision → information → external action or local follow-up → outcome measure.

### UJ-01 — Alex prepares the weekly report

Alex represents the general manager. The weekly review is approaching, and numbers currently need to be gathered from different platforms. With access appropriate to Alex's validated scope, Alex requests or opens a recurring report for an explicit week, reviews consolidated results and material exceptions, checks missing or late evidence, and obtains a formatted Excel workbook or CSV. Alex discusses the results with the owner and records local questions or follow-up for external action.

**Decision/value moment:** whether the report is sufficiently complete to use, which changes merit action, and what remains provisional. **Candidate outcomes:** report preparation effort, correction/reissue effort, timely qualified reporting and verified follow-up. **Exception:** an unavailable platform produces a visibly incomplete report, not a zero-valued contribution or an apparently final workbook. **Trace:** BG-01; C18, C20, C22; GOL-FR-001–GOL-FR-006, GOL-FR-049–GOL-FR-053; OD-01, OD-10, OD-13.

### UJ-02 — Alex examines a revenue or product-sales forecast

Alex wants an outlook for an agreed trading interval or product. Alex selects the business scope and horizon, reviews historical evidence and its gaps, compares the forecast with relevant observations and assumptions, and checks uncertainty. Alex considers an operating response with the responsible manager, saves a local scenario or follow-up, and later compares the originally issued forecast with eligible actual results.

**Decision/value moment:** whether the outlook is usable and what action it supports. A forecast of money and a forecast of product units remain different measures. **Candidate outcomes:** forecast error and useful-decision coverage, balanced by undercoverage and false confidence. **Exception:** a new product or inadequate history leads to a qualified scenario or unavailable forecast, not invented demand. **Trace:** BG-02; C01, C06, C14, C19, C20; GOL-FR-041–GOL-FR-044; OD-01, OD-04, OD-09, OD-13.

### UJ-03 — Casey and Morgan review service staffing

Casey represents a front-of-house manager; Morgan represents the head chef. Each reviews their reported rostering responsibility using expected attendance, product mix, preparation and other workload, required skills, employee availability and the relevant roster version. They assess gaps and excess, discuss coverage and cost trade-offs, then amend or publish through the existing authorised process. Later they compare planned capacity with observed service and actual work.

**Decision/value moment:** whether the evidence supports changing coverage and skill mix. **Candidate outcomes:** explained coverage gaps/excess, service outcomes, labour hours/cost and productivity on agreed definitions. **Exception:** short-notice sickness leaves an explicitly unresolved slot; there is no guarantee of a replacement. **Trace:** BG-02; C05, C13–C16, C19; GOL-FR-031–GOL-FR-036; OD-02, OD-04, OD-07, OD-09.

### UJ-04 — Casey and Morgan assess stock through the next delivery

Casey reviews front-of-house purchasing; Morgan or a sub-head chef reviews back-of-house purchasing. Each establishes a replenishment horizon, usable stock, outstanding accepted deliveries, demand scenarios, recipes/modifiers/yields, supplier pack conversions and timing uncertainty. They compare shortage and excess risks, choose what to order externally, and later review delivery and availability evidence. Aperol on a hot day and parmas during a busy dinner are reported examples, not measured patterns.

**Decision/value moment:** explain the proposed quantity and what uncertainty could change it. **Candidate outcomes:** evidenced stockouts, usable holdings, waste, excess and supplier performance. **Exception:** missing count/conversion or uncertain delivery time prevents an unqualified quantity recommendation. **Trace:** BG-02; C06–C12, C19; GOL-FR-017–GOL-FR-030; OD-01, OD-05, OD-06, OD-09.

### UJ-05 — Taylor reviews a service or guest-demand exception

Taylor represents the person whose service coordination responsibility is to be validated. Taylor compares bookings with observed attendance and covers, looks at order/fulfilment evidence and relevant capacity/work coverage, and assesses a changed arrival pattern, delay or unavailable product. Taylor discusses a response with the responsible FOH/kitchen person and records local follow-up while the actual service action occurs externally.

**Decision/value moment:** a business explanation based on attendance and service evidence rather than check counts alone. **Candidate outcomes:** service-duration distribution, availability and verified incident resolution. **Exception:** unknown arrival or fulfilment time stays unknown; public reviews may add context but cannot establish actual attendance or delay. **Trace:** BG-02; C01, C04, C05, C20; GOL-FR-007–GOL-FR-011, GOL-FR-046; OD-01, OD-02, OD-03, OD-10.

### UJ-06 — Alex and Jamie explain a weekly financial difference

Jamie represents a supporting bookkeeping function whose access requires validation. Alex sees a difference between sales, money received, stock costs or labour results. With authorised access they inspect financial components, source facts, time attribution and reconciliation gaps, distinguish an estimate from a final result, and seek external clarification or correction from the responsible function.

**Decision/value moment:** distinguish a real performance change from timing, classification or missing-evidence effects. **Candidate outcomes:** reconciliation exceptions and reliable comparable financial measures. **Exception:** an unmatched settlement or missing inventory valuation prevents a claim of complete revenue-to-cash reconciliation or final profit. **Trace:** BG-01, BG-03; C02, C03, C08, C16–C18, C22; GOL-FR-012–GOL-FR-016, GOL-FR-021–GOL-FR-022, GOL-FR-037–GOL-FR-040; OD-02, OD-03, OD-08, OD-09.

### UJ-07 — Sam compares opening scenarios

Sam represents the owner. Sam selects an opening decision area, identifies current-pub evidence that might transfer, states differences in location, customers, capacity, service model, supplier arrangements and resources, and compares versioned scenarios. Sam tests sensitivity, records remaining evidence needs and chooses what to investigate or decide externally.

**Decision/value moment:** understand why an opening option appears viable and what remains untested. **Candidate outcomes:** traceable planning assumptions, sensitivity and evidence coverage; operating results are assessed only after observations exist. **Exception:** insufficient new-site evidence prevents an unqualified forecast or a claim of confirmed profitability. **Trace:** BG-04; C19, C20, C22; GOL-FR-043–GOL-FR-045; OD-01, OD-08, OD-11.

### UJ-08 — Riley investigates an evidence gap and follows it up

Riley represents an authorised evidence contributor or steward. Riley sees a disputed number, missing clock-out, unmatched receipt, expired evidence or incorrect mapping, checks the original evidence, adds a local annotation/proposed correction and assigns follow-up within validated authority. Riley keeps source fact, proposal and external completion evidence separate and verifies any claimed resolution.

**Decision/value moment:** determine whether a measure remains usable and who must obtain missing evidence. **Candidate outcomes:** unresolved issue age, trustworthy coverage and verified resolution. **Exception:** a task marked complete without supporting evidence does not correct the source fact or prove external work occurred. **Trace:** BG-03, BG-05; C20–C22; GOL-FR-047–GOL-FR-053; OD-02, OD-10, OD-14.

## 3. Canonical vocabulary and capability coverage

### Glossary

- **Venue:** enduring operational establishment; **operational location:** physical or declared operating area within it. A department responsibility does not automatically identify a stock location.
- **Reporting period:** explicitly bounded operational, service, accounting or pay interval under a named policy. **Trading day:** venue-defined operational interval; **service period:** an explicitly defined service interval, potentially different for bar and kitchen.
- **Product:** customer-facing sellable concept; **menu offering:** its effective presentation/availability context; **price term:** intended price under stated conditions. Historical charged consideration remains an observed sale fact.
- **Customer check:** customer commercial account, including an open tab; **sale line:** one ordered/charged item occurrence; **modifier selection:** actual addition, omission or substitution; **commercial adjustment:** charge change, distinct from money refunded.
- **Money movement:** receipt, disbursement or transfer; **payment allocation:** amount applied to one obligation; **customer liability:** outstanding deposit, voucher or customer-credit obligation; **settlement component:** fee, refund, reserve, chargeback or tender element explaining processor settlement.
- **Supplier pack:** purchased item/unit definition; **stock item:** material inventory identity; **purchase order, goods receipt, supplier invoice, supplier credit:** independent commitment, physical acceptance, charge and charge-reduction facts.
- **Stock movement:** evidenced physical/book quantity change; **stock count:** observed quantity at a cutoff; **known waste:** evidenced loss; **residual depletion:** count/movement-derived remainder with stated exclusions; **theoretical consumption:** recipe-derived estimate; **usage variance:** difference between named compatible usage quantities, with no inferred cause.
- **Recipe version:** dated specification of usable output and inputs; **yield:** explicit usable-output relationship; **unit conversion:** evidenced item/pack-specific equivalence.
- **Employee:** identifiable worker; **employment assignment:** effective engagement terms; **position/work role:** duties; **roster version/planned shift:** staffing plan; **timesheet/actual interval:** work/break evidence; **labour-cost component:** separately based estimate or pay/cost result. Planned, worked and payable hours remain separate.
- **Reservation:** intended attendance; **visit:** actual attendance episode; **actual cover:** observed person under an agreed counting rule; **service allocation:** explicit check/line-to-visit attribution. Check count is not cover count.
- **Metric definition:** versioned business meaning; **measurement:** result for a definition, scope, period and status; **plan/target:** versioned budget, forecast or target, distinct from actual.
- **Net sales, COGS, gross profit, labour cost, prime cost:** explicitly scoped analytical terms defined in GOL-MET-001–GOL-MET-012. Revenue, expense and cash are not synonyms.
- **Completeness:** coverage against an explicitly named expected population and required facts; **freshness:** last evidenced occurrence/coverage and receipt age relative to a decision; **trust status:** known gaps, disputes and reconciliation state, not an opaque confidence claim.
- **Local annotation:** commentary preserved independently from source facts; **proposed correction:** unconfirmed amendment; **follow-up:** locally recorded work request/decision and its evidence; **operational control evidence:** observation/validity and applicable criteria, not automatic compliance.
- **Forecast:** issued estimate for a specified future population/horizon; **scenario:** conditional outcomes under explicit assumptions; **insight:** evidence-backed observation connected to a candidate decision; **weekly report:** versioned compilation for an agreed week and purpose.

### Capability coverage

The full S05 map remains a candidate discovery structure. This PRD supports the information and decision needs of these capabilities; verbs such as publish, receive and pay describe business responsibilities, not connected-system execution by this platform.

- **C01 offer/availability/pricing; C02 sales/commercial exceptions; C03 tender/customer balances; C04 reservations/attendance/capacity; C05 service/fulfilment:** UJ-02, UJ-05, UJ-06; GOL-FR-007–GOL-FR-016; GOL-DR-001–GOL-DR-015, GOL-DR-037–GOL-DR-039.
- **C06 purchasing commitments; C07 goods receiving; C08 supplier charges/payables; C09 stock custody/transfers/production; C10 stock counts; C11 recipes/yields; C12 consumption/waste:** UJ-04, UJ-06; GOL-FR-017–GOL-FR-030; GOL-DR-016–GOL-DR-028.
- **C13 employment/availability/competency; C14 staffing/rosters; C15 actual work; C16 labour cost/pay obligations:** UJ-03, UJ-06; GOL-FR-031–GOL-FR-037; GOL-DR-029–GOL-DR-036.
- **C17 cash/settlements; C18 financial performance; C19 budgets/forecasts; C20 operational review; C21 applicable controls; C22 evidence/definitions/responsible use:** UJ-01–UJ-08 as applicable; GOL-FR-001–GOL-FR-006, GOL-FR-038–GOL-FR-053; all shared contracts and quality/access requirements; GOL-DR-040–GOL-DR-048.

BG-01 links weekly automation to C18/C20/C22. BG-02 links operational decisions to each domain capability. BG-03 links canonical contracts and metric definitions to C22. BG-04 links opening scenarios to C19/C20. BG-05 links control/access/read-only requirements to C21/C22. The links are traceability, not confirmation of organisational ownership or feature priority.

## 4. Functional business requirements

Each group describes supported business work. All C/CC behaviours require OD-01 selection and decision-value validation as well as listed dependencies. Report automation and forecasts have the direct S06 intent signal; other decision chains are source-derived hypotheses. All functions obey GOL-CON-001 and the access rules in section 8.

### Weekly reporting and quick insights

#### GOL-FR-001 — Repeatable weekly business report

**C | Trace:** S06; BG-01; C18,C20,C22; UJ-01. **Validation:** OD-01; OD-03,OD-09,OD-13.

The owner/general manager must be able to obtain a recurring consolidated weekly report under an agreed report definition without manually copying every contributing platform total.

**Acceptance — GOL-FR-001.AC1:** For an agreed example week, each selected measure has a defined scope/basis and matches its eligible evidence. Repeating preparation with unchanged evidence and definition yields the same business totals; preparation effort is measured against the agreed baseline.

#### GOL-FR-002 — Weekly report contract and version

**C | Trace:** S06; BG-01,BG-03; C18,C22; UJ-01. **Validation:** OD-01; OD-03,OD-09,OD-13.

Users with validated report-definition responsibility must be able to agree the report purpose, week boundaries, included measures, comparison periods, sources, recipients/access and sign-off status.

**Acceptance — GOL-FR-002.AC1:** A report identifies its definition/version, week, venue/department scope, preparation/as-of time and provisional/final status. A changed calculation or period creates a distinguishable report definition/result revision.

#### GOL-FR-003 — Automated report timing and readiness

**C | Trace:** S06; BG-01; C20,C22; UJ-01. **Validation:** OD-01; OD-10,OD-13.

Recurring report preparation must follow the agreed cadence and availability deadline and expose readiness or failure without requiring a person to discover missing reports by comparing platforms.

**Acceptance — GOL-FR-003.AC1:** A due report is either available with coverage/status or visibly failed/incomplete with reason and follow-up need. A late source has its own status; report timing, availability deadline and notification behaviour are validated before acceptance.

#### GOL-FR-004 — Reconciliation of contributing numbers

**C | Trace:** S01,S06; BG-01,BG-03; C18,C22; UJ-01,UJ-06. **Validation:** OD-01; OD-03,OD-08,OD-09,OD-13.

Users must be able to explain how contributed numbers relate to canonical definitions, included facts, adjustments and source totals.

**Acceptance — GOL-FR-004.AC1:** Two sources covering the same activity do not double its total. A source total with a different time/tax scope is presented as a qualified comparison, with explainable differences rather than forced equality.

#### GOL-FR-005 — Actionable business summaries

**C | Trace:** S01,S06; BG-02; C20,C22; UJ-01,UJ-02. **Validation:** OD-01; OD-01,OD-02,OD-09,OD-12.

Users must receive concise summaries of relevant changes, exceptions and candidate next decisions, with supporting evidence and uncertainty accessible when needed.

**Acceptance — GOL-FR-005.AC1:** Each summary identifies affected scope/period, measure or observation, comparator where used, business significance or decision question, and evidence qualification. Correlation is not described as a proven cause; no proposed action is automatically executed.

#### GOL-FR-006 — Trace from summary to evidence

**C | Trace:** S01,S06; BG-03; C22; UJ-01,UJ-08. **Validation:** OD-01; OD-02,OD-09,OD-10.

An authorised user must be able to move from a report or insight to its definition, eligible population, calculation components and available supporting facts.

**Acceptance — GOL-FR-006.AC1:** A sample total can be reproduced from its retained definition and eligible inputs to the agreed rounding tolerance. Where only an aggregate is available, detail is explicitly unavailable rather than reconstructed; restricted detail remains protected.

### Commercial offer, demand and service

#### GOL-FR-007 — Historical product, menu and price interpretation

**C | Trace:** S01,S04,S05; BG-02,BG-03; C01; UJ-02,UJ-05. **Validation:** OD-01; OD-03,OD-05.

Users responsible for commercial review must be able to compare effective offerings, intended prices and observed charged consideration.

**Acceptance — GOL-FR-007.AC1:** A later price/menu/category change does not alter historical charged consideration. An unmapped product remains in known venue sales with product-level coverage disclosed.

#### GOL-FR-008 — Bookings versus attendance and covers

**C | Trace:** S01,S02,S04; BG-02; C04; UJ-02,UJ-05. **Validation:** OD-01; OD-03,OD-09.

Users must be able to review intended reservations, booking outcomes, walk-in visits and actual covers as distinct evidence.

**Acceptance — GOL-FR-008.AC1:** A booking for six with four observed covers and two checks reports six booked, four actual and two checks. Missing arrival evidence is unresolved, not automatically a no-show.

#### GOL-FR-009 — Demand and capacity comparison

**C | Trace:** S04,S05; BG-02; C04,C05,C14; UJ-03,UJ-05. **Validation:** OD-01; OD-03,OD-04,OD-09.

Users with service-planning responsibility must be able to compare evidenced attendance and expected demand with defined service capacity and work coverage.

**Acceptance — GOL-FR-009.AC1:** Seat/area utilisation is unavailable without capacity and relevant occupancy evidence. Split arrivals, table moves and split bills do not duplicate covers; demand and workload remain separate quantities.

#### GOL-FR-010 — Fulfilment and service outcomes

**C | Trace:** S01,S04,S05; BG-02; C05; UJ-05. **Validation:** OD-01; OD-03,OD-09.

Users responsible for service review must be able to inspect observed order, amendment and fulfilment stages, delays and recorded service incidents.

**Acceptance — GOL-FR-010.AC1:** Partial fulfilment remains distinguishable from complete fulfilment. Service duration uses observed order/fulfilment evidence; closure time does not fill a missing fulfilment time.

#### GOL-FR-011 — Availability and unmet-demand evidence

**C | Trace:** S02,S05; BG-02; C01,C05,C06; UJ-04,UJ-05. **Validation:** OD-01; OD-03,OD-05,OD-10.

Users must be able to distinguish recorded unavailable products/ingredients and unfulfilled demand from products with no recorded sales.

**Acceptance — GOL-FR-011.AC1:** Zero parma sales alone creates no stockout or lost-sales claim. An availability incident records scope/time, affected offering/material and evidence; lost-demand estimates are explicitly qualified.

### Customer sales and money

#### GOL-FR-012 — Atomic sales and commercial exceptions

**C | Trace:** S01,S04,S05; BG-03; C02; UJ-01,UJ-06. **Validation:** OD-01; OD-03,OD-08.

Users with authorised commercial-review responsibility must be able to review checks, lines, modifiers, adjustments, cancellations and reversals independently.

**Acceptance — GOL-FR-012.AC1:** A prepared zero-price item can contribute consumption without revenue. An amount-only refund does not imply quantity returned, cash paid or stock replenished; reasons/approval are not invented.

#### GOL-FR-013 — Tender allocation and unpaid balances

**C | Trace:** S01,S04,S05; BG-03; C03; UJ-06. **Validation:** OD-01; OD-02,OD-08.

Users responsible for customer balances must be able to distinguish charges, receipts, part payments, allocations, refunds and unresolved balances.

**Acceptance — GOL-FR-013.AC1:** A 100-unit-currency check with a 60-unit allocation has a 40-unit unpaid balance on the compatible basis. An unapplied receipt remains unapplied; a closed check does not prove payment.

#### GOL-FR-014 — Deposits, vouchers and credits

**CC | Trace:** S03,S04,S05; BG-03; C03,C04; UJ-06. **Validation:** OD-01; OD-08,OD-10.

Where these activities apply, authorised users must be able to explain customer-liability increases, redemptions, refunds and outstanding value separately from sales.

**Acceptance — GOL-FR-014.AC1:** Issuance and redemption are not both counted as product sales. Unknown expiry does not become expiry; an over-redemption remains an exception pending an agreed treatment.

#### GOL-FR-015 — Cash holding reconciliation

**C | Trace:** S04,S05; BG-03; C17; UJ-06. **Validation:** OD-01; OD-02,OD-08.

Users responsible for cash review must be able to compare observed holdings with independently explained expected holdings for a matched cutoff.

**Acceptance — GOL-FR-015.AC1:** A count of 98 against expected 100 reports observed-minus-expected variance of -2, without adjusting sales. Unknown expected holdings make variance unavailable.

#### GOL-FR-016 — Processor-to-bank settlement explanation

**C | Trace:** S01,S04,S05; BG-03; C17; UJ-06. **Validation:** OD-01; OD-08,OD-10.

Users responsible for settlement review must be able to explain gross tender, refunds, fees, reserves/chargebacks, expected net settlement and actual bank receipts.

**Acceptance — GOL-FR-016.AC1:** A 100 gross settlement less 3 fee matched to a 97 bank receipt is explained without lowering product sales by 3. An unmatched bank deposit does not establish gross sales or fees.

### Purchasing and supply

#### GOL-FR-017 — Replenishment horizon and usable stock

**C | Trace:** S02,S04,S05; BG-02; C06; UJ-04. **Validation:** OD-01; OD-05,OD-06.

Ordering managers must be able to assess usable holdings and supply commitments through a stated next-delivery horizon.

**Acceptance — GOL-FR-017.AC1:** The analysis identifies current count/balance cutoff, usable-stock rule, outstanding receipts, lead-time assumptions and horizon. Unknown delivery time or unusable-stock status produces visible uncertainty.

#### GOL-FR-018 — Candidate purchase quantity explanation

**C | Trace:** S02,S04,S05; BG-02; C06,C11,C12,C19; UJ-04. **Validation:** OD-01; OD-05,OD-06,OD-09.

Ordering managers must be able to compare candidate quantities under explicit demand, recipe, yield, supply and stock assumptions.

**Acceptance — GOL-FR-018.AC1:** Each proposal states its units, conversion/pack rounding, expected need, current usable supply and assumptions. Missing recipe/conversion evidence prevents an unqualified quantity recommendation; no safety-stock policy is invented.

#### GOL-FR-019 — Supplier terms and outstanding commitments

**C | Trace:** S01,S04,S05; BG-02; C06; UJ-04. **Validation:** OD-01; OD-05,OD-06,OD-08.

Users must be able to compare supplier pack/cost terms and separately identified pending, amended or cancelled purchasing commitments.

**Acceptance — GOL-FR-019.AC1:** A partially received order shows only remaining quantities as outstanding on matched units. A changed pack/quote retains effective dates; cost comparisons exclude incompatible tax/currency bases.

#### GOL-FR-020 — Accepted and rejected receipt evidence

**C | Trace:** S01,S04,S05; BG-03; C07; UJ-04. **Validation:** OD-01; OD-02,OD-05,OD-06.

Users responsible for receiving review must be able to inspect delivered, accepted, rejected and pending quantities, substitutions, condition and claims.

**Acceptance — GOL-FR-020.AC1:** For ten delivered packs with eight accepted and two rejected, only eight accepted packs support accepted-stock additions after conversion. Missing invoice/order evidence does not invalidate a known receipt.

#### GOL-FR-021 — Order, receipt, invoice and credit matching

**C | Trace:** S01,S04,S05; BG-03; C07,C08; UJ-04,UJ-06. **Validation:** OD-01; OD-02,OD-06,OD-08.

Authorised reviewers must be able to explain partial/multiple deliveries, consolidated invoices and credits through evidenced matches.

**Acceptance — GOL-FR-021.AC1:** One order can match several receipts and one invoice several receipts without duplicated purchase activity. A document-level match cannot claim line-level quantity agreement; over-allocation remains an exception. Three-way approval policy is not assumed.

#### GOL-FR-022 — Payables and supplier payment status

**C | Trace:** S01,S04,S05; BG-03; C08; UJ-06. **Validation:** OD-01; OD-02,OD-08.

Authorised users must be able to distinguish asserted supplier charges/credits, disputes, due obligations and allocated payments.

**Acceptance — GOL-FR-022.AC1:** An invoice without a payment remains outstanding, not paid because it was received. Non-stock service charges do not create stock movements; missing due-date evidence prevents an overdue conclusion.

### Stock, recipes and consumption

#### GOL-FR-023 — Stock by item, location and cutoff

**C | Trace:** S01,S04,S05; BG-03; C09; UJ-04. **Validation:** OD-01; OD-05.

Users must be able to review stock balances from distinct evidenced movement classes and observe unresolved negative or incomplete balances.

**Acceptance — GOL-FR-023.AC1:** Opening plus classified movements explains closing book quantity, with each fact counted once. A missing location or conversion leaves the affected balance incomplete; a negative balance is retained as an exception.

#### GOL-FR-024 — Internal transfers and preparation production

**C | Trace:** S01,S04,S05; BG-03; C09,C11; UJ-04. **Validation:** OD-01; OD-05.

Users must be able to explain linked transfer and production legs without treating internal activity as new purchasing or duplicate consumption.

**Acceptance — GOL-FR-024.AC1:** A venue-internal transfer of two bottles has equal outbound/inbound quantity and zero venue-wide net change. Production inputs/outputs use their own units and yield evidence; a missing counterpart is visible.

#### GOL-FR-025 — Count scope, observations and recounts

**C | Trace:** S01,S04,S05; BG-03; C10; UJ-04,UJ-08. **Validation:** OD-01; OD-02,OD-05.

Users responsible for stock review must be able to identify intended count scope, observed/recounted quantities and matched cutoff balances.

**Acceptance — GOL-FR-025.AC1:** A missing count line is uncounted, not zero. A recount supersedes rather than adds to the prior observation; movements during the count are reconciled to the comparison cutoff.

#### GOL-FR-026 — Count variance and proposed adjustment

**C | Trace:** S01,S04,S05; BG-03; C10,C22; UJ-08. **Validation:** OD-01; OD-02,OD-05,OD-14.

Users must be able to investigate count discrepancies and record local adjustment proposals separately from source counts and movements.

**Acceptance — GOL-FR-026.AC1:** Eight observed against ten book units yields -2 variance without automatically posting -2 stock movement or labelling waste/theft. A proposal records basis/reason; an external adjustment requires independently obtained evidence.

#### GOL-FR-027 — Effective recipes, modifiers and yields

**C | Trace:** S01,S02,S04; BG-02,BG-03; C01,C11; UJ-02,UJ-04. **Validation:** OD-01; OD-03,OD-05.

Users responsible for preparation review must be able to relate products/modifiers to effective ingredient/output specifications, portions and usable yields.

**Acceptance — GOL-FR-027.AC1:** A historical dish uses the appropriate recipe version under an explicit attribution rule. An omission removes its defined input rather than adding consumption; unknown substitution mapping marks ingredient coverage incomplete.

#### GOL-FR-028 — Theoretical usage and ingredient economics

**C | Trace:** S01,S04,S05; BG-02; C11,C12; UJ-04. **Validation:** OD-01; OD-05,OD-08,OD-09.

Users must be able to review reproducible theoretical consumption and recipe ingredient cost with price/conversion/yield basis.

**Acceptance — GOL-FR-028.AC1:** Output multiplied by effective component quantities gives labelled theoretical usage. Cost per usable portion states cost date and yield; purchase pack cost alone does not establish portion cost or actual COGS.

#### GOL-FR-029 — Known waste and disposal

**C | Trace:** S01,S04,S05; BG-03; C12; UJ-04,UJ-08. **Validation:** OD-01; OD-05,OD-08.

Users must be able to review evidenced waste incidents, measured/estimated quantities and reasons independently from unexplained discrepancy.

**Acceptance — GOL-FR-029.AC1:** A linked waste observation and disposal movement enter stock balance once. Unknown cost permits physical waste reporting; unknown quantity permits incident reporting with incomplete measured waste, not zero loss.

#### GOL-FR-030 — Residual depletion and usage variance

**C | Trace:** S01,S04,S05; BG-03; C12; UJ-04,UJ-08. **Validation:** OD-01; OD-05,OD-09.

Users must be able to compare named compatible theoretical, observed or residual usage quantities and investigate differences without inferring causes.

**Acceptance — GOL-FR-030.AC1:** The residual shows opening/closing counts, included movements and excluded known losses. Residual minus theoretical usage is labelled unexplained usage variance, never automatically waste, theft or employee fault.

### People and staffing

#### GOL-FR-031 — Availability, duties and competency evidence

**C | Trace:** S01,S02,S04; BG-02,BG-05; C13; UJ-03. **Validation:** OD-01; OD-02,OD-04,OD-07,OD-10.

Rostering managers must be able to assess relevant availability, duties and validated competency requirements without unnecessary private information.

**Acceptance — GOL-FR-031.AC1:** An employee title alone proves neither competency nor classification. Required-but-expired evidence and unknown availability are distinguished from valid evidence and confirmed unavailability.

#### GOL-FR-032 — Workload and skill coverage planning

**C | Trace:** S02,S04,S05; BG-02; C05,C14,C19; UJ-03. **Validation:** OD-01; OD-02,OD-04,OD-07.

Department rostering managers must be able to compare candidate capacity/skill mix against demand, preparation effort and competing duties.

**Acceptance — GOL-FR-032.AC1:** A cocktail-heavy demand scenario can have a separately stated effort assumption; revenue/headcount alone is not declared measured workload. Required coverage and under/overstaffing criteria are validated, and missing task evidence is visible.

#### GOL-FR-033 — Roster versions and absence risks

**C | Trace:** S01,S02,S04; BG-02; C14; UJ-03. **Validation:** OD-01; OD-02,OD-04,OD-07.

Managers must be able to compare roster versions, unfilled slots, availability conflicts and short-notice absence impacts and record local options.

**Acceptance — GOL-FR-033.AC1:** The originally published plan remains available when amended. A sickness-related unfilled slot remains unresolved until external evidence supports cover; sub-head-chef rostering authority is not inferred.

#### GOL-FR-034 — Planned, actual and payable time

**C | Trace:** S01,S04,S05; BG-03; C14,C15,C16; UJ-03,UJ-06. **Validation:** OD-01; OD-03,OD-07.

Authorised users must be able to compare planned shifts, actual work/break intervals and separately determined payable time on aligned scopes.

**Acceptance — GOL-FR-034.AC1:** Unrostered work remains actual work; a rostered absence does not become worked hours. A missing clock-out has unknown duration rather than a roster-derived end; department allocations do not duplicate worked time.

#### GOL-FR-035 — Timesheet evidence and correction follow-up

**C | Trace:** S01,S04,S05; BG-03; C15,C22; UJ-08. **Validation:** OD-01; OD-02,OD-07,OD-14.

Authorised reviewers must be able to inspect timesheet status, missing/overlapping intervals and correction lineage and record local queries.

**Acceptance — GOL-FR-035.AC1:** Approval status does not prove payment. A proposed correction retains original evidence and reason; self-approval and verifier separation apply only when the validated policy requires them.

#### GOL-FR-036 — Labour cost interpretation and productivity

**C | Trace:** S01,S04,S05; BG-02,BG-03; C16,C18; UJ-03,UJ-06. **Validation:** OD-01; OD-07,OD-08,OD-09.

Authorised users must be able to compare forecast labour cost, provisional actual cost and final payroll results with explicit included components and hour bases.

**Acceptance — GOL-FR-036.AC1:** Unknown penalties/on-costs prevent a complete loaded-cost claim. Estimate and superseding final components are not both added to actual cost; sales-per-worked-hour and sales-per-paid-hour remain separately named.

#### GOL-FR-037 — Pay obligations and settlement evidence

**CC | Trace:** S04,S05; BG-05; C16; UJ-06. **Validation:** OD-01; OD-02,OD-07,OD-10.

Where justified and authorised, users must be able to review pay/contribution obligations, applicable dates and payment/receipt exceptions without operating payroll.

**Acceptance — GOL-FR-037.AC1:** Paid status requires allocation/evidence appropriate to the obligation. Unknown applicability or due-date basis prevents an overdue/compliance assertion; no universal rate or deadline is introduced.

### Financial and management review

#### GOL-FR-038 — Comparable financial results

**C | Trace:** S01,S04,S05; BG-01,BG-03; C18; UJ-01,UJ-06. **Validation:** OD-01; OD-03,OD-08,OD-09.

Users must be able to review revenue, inventory cost, COGS, labour, other expenses and profit measures on declared matching periods and bases.

**Acceptance — GOL-FR-038.AC1:** Missing cost coverage produces a partial/estimated or unavailable profit measure. Recipe cost, purchasing, cash paid and final COGS remain distinguishable; valued waste is not subtracted twice.

#### GOL-FR-039 — Budgets, targets and commitments

**C | Trace:** S04,S05; BG-02; C06,C18,C19; UJ-06,UJ-07. **Validation:** OD-01; OD-08,OD-09,OD-11.

Users must be able to compare actuals and exposure with approved budget/target versions and separate forecast revisions.

**Acceptance — GOL-FR-039.AC1:** Without an approved budget, budget variance is unavailable. A revised forecast does not overwrite the approved budget; favourable direction follows the agreed measure definition.

#### GOL-FR-040 — Comparable reporting populations

**C | Trace:** S01,S04,S05; BG-01,BG-03; C18,C19,C22; UJ-01,UJ-06. **Validation:** OD-01; OD-03,OD-08,OD-09.

Users must be able to identify comparable and incompatible periods, populations, categories and cost bases when reviewing changes.

**Acceptance — GOL-FR-040.AC1:** A comparison states duration, service scope, category version, tax/cost basis and coverage differences. Incompatible comparisons are qualified or unavailable; an unequal trading-week duration is not silently compared as equivalent.

### Forecasts and opening scenarios

#### GOL-FR-041 — Revenue forecast

**C | Trace:** S06,S02; BG-02; C19; UJ-02. **Validation:** OD-01; OD-03,OD-04,OD-08,OD-09,OD-13.

Owner/general manager users must be able to obtain a revenue forecast for a defined horizon/scope, with issue date, assumptions, units and uncertainty.

**Acceptance — GOL-FR-041.AC1:** The result states what revenue includes, evidence cutoff/coverage and forecast horizon. Inadequate history yields a labelled scenario or unavailable result; hindsight revisions cannot masquerade as the originally issued forecast.

#### GOL-FR-042 — Specific-product sales forecast

**C | Trace:** S06,S02; BG-02; C01,C06,C14,C19; UJ-02,UJ-04. **Validation:** OD-01; OD-03,OD-04,OD-05,OD-09,OD-13.

Users must be able to obtain a selected product forecast distinguishing expected sale quantities from expected monetary sales and any modifier/variant scope.

**Acceptance — GOL-FR-042.AC1:** A product forecast states sale units, product/version mapping, horizon, price assumptions for money and known availability constraints. Recorded zero sales do not imply zero demand during an evidenced stockout; unknown history is not invented.

#### GOL-FR-043 — Forecast and scenario assumptions

**C | Trace:** S02,S06; BG-02,BG-04; C19; UJ-02,UJ-07. **Validation:** OD-01; OD-04,OD-05,OD-09,OD-11.

Users must be able to inspect, compare and locally vary explicit assumptions without altering actual facts or published forecast history.

**Acceptance — GOL-FR-043.AC1:** Each scenario identifies author, version, scope, assumptions, changed inputs and qualified outputs. Weather, bookings and public context remain proposed drivers unless supported; a scenario is not labelled a confirmed causal forecast.

#### GOL-FR-044 — Forecast outcome evaluation

**C | Trace:** S02,S05,S06; BG-02; C19,C20; UJ-02. **Validation:** OD-01; OD-03,OD-04,OD-09,OD-13.

Users must be able to compare an issued forecast with eligible later actuals using an agreed horizon/error definition and evidence coverage.

**Acceptance — GOL-FR-044.AC1:** Evaluation references the forecast version issued before the outcome and matched actual population. Missing actuals or actual zero with a percentage-error denominator are handled under the agreed missing/denominator rule, not forced to zero error.

#### GOL-FR-045 — Second-pub decision evidence

**C | Trace:** S02; BG-04; C19,C20,C22; UJ-07. **Validation:** OD-01; OD-08,OD-11.

The owner must be able to compare candidate opening scenarios using current-pub evidence, new-site differences, resources and explicit evidence gaps.

**Acceptance — GOL-FR-045.AC1:** A scenario states transfer assumptions, alternative options and sensitivity for the selected decision area. Current-pub performance is not treated as guaranteed new-pub demand or profitability; financial outputs disclose included costs.

### External context and assurance

#### GOL-FR-046 — Purpose-bound external and internal context

**C | Trace:** S02; BG-02; C19,C20,C22; UJ-02,UJ-05. **Validation:** OD-01; OD-01,OD-10.

Users must be able to review relevant reviews, articles, booking context and internal work evidence, including read access to Asana where access is available and purpose validated.

**Acceptance — GOL-FR-046.AC1:** Each item has source, subject, occurrence/publication/receipt times and relevance/limitations. A review is not counted as a visit; read task status is distinct from independently verified completion. Weather requires a validated use; no unrestricted archive is created.

#### GOL-FR-047 — Applicable operational control evidence

**CC | Trace:** S01,S03,S04; BG-05; C13,C21; UJ-08. **Validation:** OD-01; OD-02,OD-07,OD-10.

Where applicability is established, authorised users must be able to review relevant safety, licence, competency, incident and corrective-action evidence.

**Acceptance — GOL-FR-047.AC1:** A pass/fail assertion has an applicable criterion/version, subject and observation. Unknown applicability or missing observation remains a gap; neither becomes a compliance pass or established breach.

#### GOL-FR-048 — Prioritised exception review

**C | Trace:** S01,S02,S05; BG-02,BG-03; C20,C22; UJ-01,UJ-08. **Validation:** OD-01; OD-01,OD-02,OD-09,OD-14.

Users must be able to see relevant unresolved exceptions with business effect, evidence state and ownership, under agreed prioritisation rules.

**Acceptance — GOL-FR-048.AC1:** An issue identifies type, affected facts/decision, first-observed/as-of times, unresolved amount/scope if known and assigned-or-unassigned owner. Unagreed thresholds remain pending; diagnostics do not displace the user’s business question.

### Local evidence and follow-up

#### GOL-FR-049 — Local annotation and correction proposal

**C | Trace:** S01,S02; BG-03,BG-05; C22; UJ-08. **Validation:** OD-01; OD-02,OD-10,OD-14.

Authorised users must be able to retain local observations and correction proposals alongside original evidence.

**Acceptance — GOL-FR-049.AC1:** Original fact, author, time, reason, proposed meaning/value and review status remain identifiable. Calculations do not silently substitute a proposal for a source fact; an explicitly hypothetical adjusted view is labelled and independently reproducible.

#### GOL-FR-050 — Follow-up responsibility and lifecycle

**C | Trace:** S02,S05; BG-02; C20,C22; UJ-01,UJ-08. **Validation:** OD-01; OD-02,OD-14.

Authorised users must be able to record local follow-up, proposed/accepted ownership, agreed due dates, escalation and verified closure.

**Acceptance — GOL-FR-050.AC1:** Unassigned work stays unassigned until ownership is established. Locally marked completion without required evidence remains unverified; cancellation, supersession, partial resolution and reopening retain history without changing connected tasks.

### Exports and report sharing

#### GOL-FR-051 — CSV export of permitted business information

**C | Trace:** S06; BG-01,BG-02,BG-05; C18,C20,C22; UJ-01. **Validation:** OD-01; OD-02,OD-09,OD-10,OD-13.

Users must be able to export authorised selected facts or report results as CSV with sufficient meaning for subsequent business use.

**Acceptance — GOL-FR-051.AC1:** Exported rows preserve grain/identity or result scope, meaningful headers, units, dates, currency/tax basis and missing states. Row count/totals match the selected authorised population; delimiters, quotes and line breaks do not corrupt records.

#### GOL-FR-052 — Readable formatted Excel reports

**C | Trace:** S06; BG-01,BG-02; C18,C20,C22; UJ-01. **Validation:** OD-01; OD-09,OD-10,OD-12,OD-13.

Users must be able to export a formatted Excel workbook whose business summary, comparisons and supporting detail are easy to read.

**Acceptance — GOL-FR-052.AC1:** Agreed report fields are readable without hidden/truncated critical labels or statuses; money, quantity, percentage and date formats preserve meaning. Summary and detail reconcile, with title, scope/period, as-of/status, units and caveats retained; numbers remain usable numbers.

#### GOL-FR-053 — Export completeness, provenance and safe use

**C | Trace:** S01,S06; BG-03,BG-05; C22; UJ-01,UJ-08. **Validation:** OD-01; OD-02,OD-09,OD-10,OD-13.

Exports must preserve applicable report definitions, evidence limitations and permissions and disclose truncation or omitted/restricted detail.

**Acceptance — GOL-FR-053.AC1:** A partial report stays marked partial in the file; an export cannot expose restricted staff/customer detail. Known zero and missing values remain distinguishable; untrusted text is not interpreted as executable spreadsheet content.


#### GOL-FR-054 — Business questions, filters and comparisons

**C | Trace:** S01,S06; BG-01,BG-02; C01,C06,C14,C18–C20,C22; UJ-01,UJ-02,UJ-06. **Validation:** OD-01,OD-02,OD-03,OD-09,OD-10,OD-12,OD-13.

Users must be able to obtain selected facts, summaries and comparisons for authorised business scopes and meaningful periods/dimensions without preparing a new cross-platform spreadsheet for each question. Candidate dimensions include product/variant/category, location/department, supplier and work role; individual worker detail requires its own permission and purpose.

**Acceptance — GOL-FR-054.AC1:** The selected scope, filters, sort/grouping and comparator are visible and retained in the result/export. Changing to a validated product or service-period scope yields corresponding eligible facts without changing canonical meaning; ratios recalculate from eligible components. Unsupported dimensions and restricted detail are explicitly unavailable rather than guessed or exposed.

## 5. Shared canonical semantics and trust

Shared contracts are business meanings, not prescribed fields in a database. They apply to every fact, report, forecast and export. “Required” means required to assert the named business fact or conclusion. Incomplete evidence may be retained and inspected, but must not be presented as a complete assertion. Known quantity and unknown cost, or known money and unknown product mapping, remain independently useful.

#### GOL-SEM-001 — Identity and duplicate evidence

**B | Trace:** S01,S04; BG-03; C22.

Each fact must retain stable venue/business identity, its grain, source identity/reference and evidence status. Names, amounts or display numbers alone must not merge facts; repeated copies of one fact must not create new activity.

**Acceptance — GOL-SEM-001.AC1:** Two identical legitimate sale lines remain two occurrences. Repeating the same source observation leaves business totals unchanged. Ambiguous identity/mapping is unresolved rather than guessed.

#### GOL-SEM-002 — Temporal meaning and attribution

**B | Trace:** S01,S04; BG-03; C22.

Occurrence/effective time, recorded/received time, publication time and calculation time must remain distinct, with known precision and timezone/offset. Reporting attribution must cite an agreed period policy/version; boundary inclusion must be explicit so a fact is counted once within an exclusive reporting partition. Inclusive-start/exclusive-end is the S04 candidate convention for OD-03 validation.

**Acceptance — GOL-SEM-002.AC1:** An unknown occurrence time is not replaced by receipt time. Overnight/DST elapsed work uses actual instants; a trading-day rule does not change payroll/calendar dates. Unresolved period assignment stays visible (OD-03).

#### GOL-SEM-003 — Quantity, currency, tax and cost bases

**B | Trace:** S01,S04; BG-03; C22.

Quantities must carry units/bases; money must carry currency, sign/direction and tax basis, including unknown. Conversions must have item/pack scope, positive factor and effective evidence; unit cost must state priced unit/date/basis.

**Acceptance — GOL-SEM-003.AC1:** Cases, litres and serves are not added without conversions. Mixed-tax values are not assigned a blanket tax rule. Compatible totals reconcile within an agreed rounding tolerance; incompatible bases remain separate (OD-05,OD-08,OD-09).

#### GOL-SEM-004 — Known zero and absence states

**B | Trace:** S01,S02,S04; BG-03; C22.

Every material value/relationship must distinguish known (including zero), unknown, not captured, not applicable with reason, and disputed. Missing includes absence of captured evidence; it is not a numeric value.

**Acceptance — GOL-SEM-004.AC1:** A confirmed zero stock count remains zero; no count remains uncounted. A disputed rate preserves conflicting evidence. Reports/exports do not coerce absence to zero, a false identity or artificial date.

#### GOL-SEM-005 — History, correction and authority

**B | Trace:** S01,S02,S04; BG-03,BG-05; C22.

Corrections and restatements must preserve original assertion, changed assertion, reason, author/evidence, time and supersession link. Approved/final status requires evidence appropriate to validated policy.

**Acceptance — GOL-SEM-005.AC1:** A late invoice changes a distinguishable result revision, not the original published report unnoticed. A local proposed correction never proves that source history has changed; absent approval evidence cannot claim approval (OD-02,OD-14).

#### GOL-SEM-006 — Derived measures and uncertainty

**B | Trace:** S01,S02,S04; BG-03; C19,C22.

Every derived result must retain definition/version, included input scope, as-of/period, units, status, exclusions, completeness and uncertainty appropriate to the assertion. Observed, estimated, scenario, provisional and final results must remain distinguishable.

**Acceptance — GOL-SEM-006.AC1:** A known aggregate without atomic detail is labelled aggregate-only. A missing denominator makes a ratio unavailable with reason; a forecast is never rendered as an observed outcome. Relevant assumptions accompany comparisons (OD-09).

#### GOL-SEM-007 — Completeness by decision population

**B | Trace:** S01,S02; BG-03; C20,C22.

Completeness must state expected population, required inputs, known covered population and exclusions/gaps. Unknown expected population must not yield an invented coverage percentage.

**Acceptance — GOL-SEM-007.AC1:** A report covering four of five known service periods states the omitted period and coverage basis. If total expected visits are unknown, it says denominator unknown; 100% ingestion does not prove complete business activity.

#### GOL-SEM-008 — Freshness and decision eligibility

**B | Trace:** S01,S02; BG-02,BG-03; C20,C22.

Users must be able to see last evidenced coverage, last received time and known delays/gaps for decision inputs, and whether an agreed freshness/completeness rule is met.

**Acceptance — GOL-SEM-008.AC1:** An old count newly received is not called fresh stock. If a decision rule is unset, eligibility is unvalidated; if violated, the result is qualified/unavailable under the agreed rule (OD-04,OD-06,OD-09,OD-13).

#### GOL-SEM-009 — Canonical meaning before source mapping

**B | Trace:** S01,S04; BG-03; C22.

Each contributing source concept must be mapped to the business meaning or explicitly left unmapped/conflicting. Source totals and aliases may be retained without making their definitions canonical.

**Acceptance — GOL-SEM-009.AC1:** Two differently defined “sales” totals retain tax/time/adjustment differences. An unmapped receipt pack is retained as pack evidence but cannot silently enter a stock-unit balance.

#### GOL-SEM-010 — Single recognition and reconciled relationships

**B | Trace:** S01,S04; BG-03; C02,C03,C08,C09,C12,C15,C22.

Parent totals, allocated children, revisions and linked observations must not represent additional activity. Reconciliation must expose residual differences rather than hide them in forced adjustments.

**Acceptance — GOL-SEM-010.AC1:** A discount parent and its allocations count once; transfer legs net venue-wide without new purchasing. A payroll estimate plus its final replacement is not double actual cost; unexplained residuals stay explicit.

## 6. Atomic operational information and minimum contracts

All contracts below are **C**, derived from S01 coverage and the S04 business proposal; detailed grains/fields/relationships require stakeholder validation. CC applies to conditional activity such as vouchers or applicable controls. Every contract inherits GOL-SEM-001–GOL-SEM-010, GOL-CON-001 and section 8 permissions. Unless stated otherwise, each fact belongs to one venue/business scope and carries stable identity, source/provenance, fact status, recorded time, occurrence/effective-time precision, units/money basis as relevant and correction lineage.

A conditional minimum is required only for the specific assertion described. A stock cost need not be known to retain a physical quantity; worker private details need not be captured to identify actual work. Lifecycle stages are retained where evidence exists; a known final fact does not imply that every preceding event was observed. Cardinalities below are business relationships, not software tables, record permissions or universal regulatory prescriptions. Each acceptance statement includes the missing-information behaviour required for that contract.

#### GOL-DR-001 — Venue

**C | Trace:** S01; S04 E01; C22. **Validation:** OD-03,OD-10.

**Grain and conditional minimum:** One enduring establishment: identity and identifying label. Named timezone is required for local-time attribution; legal operator/jurisdiction are required for an applicability claim.

**Relationships and invariants:** Venue owns many operational locations/periods/facts; name/operator changes retain identity and dated context.

**Acceptance — GOL-DR-001.AC1:** Unknown operator/jurisdiction leaves obligations unresolved, without erasing known venue activity.

#### GOL-DR-002 — Operational location

**C | Trace:** S01; S04 E02; C04,C09,C22. **Validation:** OD-02,OD-03,OD-05.

**Grain and conditional minimum:** One identified operating/holding area: venue, identity, label/type; capacity requires value, persons/seats basis and effective dates.

**Relationships and invariants:** One venue; zero/one parent and many children, with no hierarchy cycles. A stockholding assertion needs an identified holding location; department and physical area differ.

**Acceptance — GOL-DR-002.AC1:** An unknown area remains unassigned; parent/child aggregation counts activity once.

#### GOL-DR-003 — Reporting period

**C | Trace:** S01; S04 E03; C18,C19,C22. **Validation:** OD-03,OD-09.

**Grain and conditional minimum:** One bounded interval/type/scope: start, end, policy/version, timezone for local boundaries and label for a named service.

**Relationships and invariants:** One venue; optional parent in a declared hierarchy; end follows start. Exclusive partitions do not overlap within a scope; operational/pay/accounting periods differ.

**Acceptance — GOL-DR-003.AC1:** A midnight-crossing example is assigned by an explicit agreed rule while its original timestamp remains intact; unresolved assignment is shown.

#### GOL-DR-004 — Product

**C | Trace:** S01; S04 E04; C01,C02. **Validation:** OD-03,OD-05.

**Grain and conditional minimum:** One stable commercial concept: identity, description; asserted quantity requires sale unit/variant basis; category mapping has effective history.

**Relationships and invariants:** Many offerings/prices/sale lines; optional direct stock/recipe mapping. Product, ingredient and supplier pack are independent identities.

**Acceptance — GOL-DR-004.AC1:** An unidentified product leaves an unmapped sale and incomplete product mix, not deleted revenue; identical names do not force a merge.

#### GOL-DR-005 — Menu offering version

**C | Trace:** S01; S04 E05; C01,C05. **Validation:** OD-03,OD-05,OD-10.

**Grain and conditional minimum:** One effective presentation/availability context: product, menu/context identity, version, effective start and description. Allergen/availability assertions require supporting versioned evidence.

**Relationships and invariants:** One product; many contextual price terms; parallel contexts may exist. Effective withdrawal does not invalidate prior sales.

**Acceptance — GOL-DR-005.AC1:** A sale can be known without menu evidence; unknown availability/allergen evidence is not treated as available/allergen-free.

#### GOL-DR-006 — Price term

**C | Trace:** S01; S04 E06; C01,C02. **Validation:** OD-03,OD-08.

**Grain and conditional minimum:** One eligible price/context/version: product/offering, amount/currency, priced quantity/unit, effective start; tax basis required for inclusive/exclusive claims.

**Relationships and invariants:** One product, optional offering; overlapping eligible terms require a selection rule. Historical charged amount is independent.

**Acceptance — GOL-DR-006.AC1:** Zero price is valid; today’s price never recalculates past sales. Unknown intended price makes list-price reduction unavailable.

#### GOL-DR-007 — Customer check

**C | Trace:** S01; S04 E07; C02,C03. **Validation:** OD-03,OD-08.

**Grain and conditional minimum:** One commercial account with split/merge lineage: venue, identity and lifecycle status; timed opening/closure claims require relevant times. Monetary total requires currency/tax basis.

**Relationships and invariants:** Many sale lines/adjustments/payment allocations; many visits via service allocations. Empty/open checks are valid.

**Acceptance — GOL-DR-007.AC1:** Closed does not prove paid/fulfilled. Aggregate-only check evidence cannot establish missing product quantities or covers.

#### GOL-DR-008 — Sale line

**C | Trace:** S01; S04 E08; C01,C02,C05,C12. **Validation:** OD-03,OD-05,OD-08.

**Grain and conditional minimum:** One check item occurrence/revision: parent check, line identity, item reference/description, lifecycle status. Quantity claim requires quantity/unit; money claim requires amount/currency/basis; fulfilment claim requires relevant quantity/time evidence.

**Relationships and invariants:** Exactly one check; optional canonical product; many modifiers/adjustments. Partial fulfilment/cancellation is attributable; partitions cannot exceed amended ordered quantity.

**Acceptance — GOL-DR-008.AC1:** Missing fulfilment means unknown, not unfulfilled. Financial reversal never automatically restocks; zero-price fulfilled lines can consume stock.

#### GOL-DR-009 — Modifier selection

**C | Trace:** S01; S04 E09; C01,C02,C05,C11. **Validation:** OD-03,OD-05.

**Grain and conditional minimum:** One selected option occurrence: parent line, selection meaning, quantity/unit where meaningful, target for omission/substitution. Price/recipe effects require explicit inclusion/mapping basis.

**Relationships and invariants:** Exactly one sale line; optional mapped modifier product and component effects; repeated quantified selections can be legitimate.

**Acceptance — GOL-DR-009.AC1:** Price effect counts once; unknown substitution mapping leaves theoretical ingredient usage incomplete even when charged amount is known.

#### GOL-DR-010 — Commercial adjustment

**C | Trace:** S01; S04 E10; C02,C03. **Validation:** OD-02,OD-03,OD-08.

**Grain and conditional minimum:** One adjustment allocation: check or line target, type, signed amount/currency/basis, status; percent requires eligible basis; approved requires policy/evidence.

**Relationships and invariants:** Exactly one target; optional original fact, reason and linked money movement. Shared parent and allocated children are not additive.

**Acceptance — GOL-DR-010.AC1:** Amount-only credit creates neither stock return nor cash refund; unknown reason stays unknown and unresolved excess remains visible.

#### GOL-DR-011 — Money movement

**C | Trace:** S01; S04 E11; C03,C08,C16,C17. **Validation:** OD-08,OD-10.

**Grain and conditional minimum:** One money transfer/receipt/disbursement: amount/currency, direction/purpose, status, identified venue endpoint; dated cash requires occurrence precision.

**Relationships and invariants:** Many allocations/settlement components; original refund/payment link where known. Authorised, captured, received and settled status/time differ.

**Acceptance — GOL-DR-011.AC1:** Internal transfers are not revenue; pending authorisation is not settled cash. Unknown method does not invalidate an evidenced receipt; unknown target leaves it unapplied.

#### GOL-DR-012 — Payment allocation

**C | Trace:** S01; S04 E12; C03,C08,C16,C17. **Validation:** OD-08.

**Grain and conditional minimum:** One applied amount: payment, exactly one target identity/type, amount/currency, application direction/status. Cross-currency application requires conversion evidence.

**Relationships and invariants:** Target is one check, supplier charge/credit, customer liability, pay obligation or settlement obligation. Many allocations are possible; total cannot silently exceed available payment.

**Acceptance — GOL-DR-012.AC1:** Part payments reconcile independently; missing conversion or unknown target produces unresolved/unapplied balance, never invented settlement.

#### GOL-DR-013 — Customer liability entry

**CC | Trace:** S01; S04 E13; C03,C04. **Validation:** OD-08,OD-10.

**Grain and conditional minimum:** One deposit/voucher/credit balance change: liability identity or evidenced pool, entry type, amount/currency/direction; dated balance requires effective date.

**Relationships and invariants:** One liability scope; optional reservation/check; payment allocations and linked original entries. Recognition, expiry and tax are separately validated.

**Acceptance — GOL-DR-013.AC1:** Pooled evidence cannot prove individual redemption balances; unknown expiry never means expired. Redemption/issuance are not duplicate sales.

#### GOL-DR-014 — Cash reconciliation observation

**C | Trace:** S01; S04 E14; C17. **Validation:** OD-02,OD-08.

**Grain and conditional minimum:** One monetary holding count: holding/session identity, observed amount/currency and cutoff precision; variance requires expected amount and basis.

**Relationships and invariants:** One venue/holding scope; many relevant movements; optional previous opening observation. Recounts retain lineage.

**Acceptance — GOL-DR-014.AC1:** No count is unknown rather than zero; missing expected holding prevents variance, not observed cash reporting.

#### GOL-DR-015 — Settlement component

**C | Trace:** S01; S04 E15; C17. **Validation:** OD-08.

**Grain and conditional minimum:** One element of one provider settlement: provider, grouping/component identity, type, signed amount/currency; dated reconciliation needs statement/eligibility/receipt dates.

**Relationships and invariants:** One settlement group; optional original payment; bank receipt allocations. Fees, refunds/reserves and chargebacks retain separate meanings.

**Acceptance — GOL-DR-015.AC1:** A bank deposit without statement detail remains unmatched, not reconstructed gross sales; components explain net settlement with declared signs.

#### GOL-DR-016 — Supplier

**C | Trace:** S01; S04 E16; C06,C07,C08. **Validation:** OD-06,OD-10.

**Grain and conditional minimum:** One supplying counterparty: stable identity and distinguishing label/reference; terms, contacts and lead time require relevant dated evidence.

**Relationships and invariants:** Many pack terms/documents/payments; a branch/account is not automatically a separate legal supplier.

**Acceptance — GOL-DR-016.AC1:** Name similarity does not merge suppliers; unknown contact/tax identity does not erase a known receipt or claim verified compliance.

#### GOL-DR-017 — Supplier item and pack term

**C | Trace:** S01; S04 E17; C06,C07,C11. **Validation:** OD-05,OD-06,OD-08.

**Grain and conditional minimum:** One effective pack/item version: supplier, description/reference, purchase unit; stock equivalence requires stock-item mapping, pack contents and conversion; priced comparisons require money/unit/date/basis.

**Relationships and invariants:** One supplier; optional stock item; one stock item may have many supplier packs. Positive item-specific conversions and pack history are retained.

**Acceptance — GOL-DR-017.AC1:** Unknown conversion preserves pack evidence but prevents base-stock/per-litre claims; a replacement pack is not treated as the old size.

#### GOL-DR-018 — Purchase document

**C | Trace:** S01; S04 E18; C06,C07,C08. **Validation:** OD-06,OD-08.

**Grain and conditional minimum:** One separately identified order, receipt, invoice or credit: exactly one type, venue, counterparty, identity, status; dated assertion requires its own date; monetary invoice/credit requires currency/basis.

**Relationships and invariants:** Many document lines/matches; charges/credits can have many payments. Issuer/type qualify external numbers; orders, receipts, invoices and credits do not share identity merely by number.

**Acceptance — GOL-DR-018.AC1:** A receipt without order/invoice remains a physical fact. Missing detail supports only a labelled document total, not invented quantities; service invoices need not create stock.

#### GOL-DR-019 — Purchase document line

**C | Trace:** S01; S04 E19; C06,C07,C08. **Validation:** OD-05,OD-06,OD-08.

**Grain and conditional minimum:** One line on one typed document: parent, line identity, item/service description. Order quantity requires unit; receipt quantity requires disposition; charge/credit requires signed amount/currency; optional quantity charge requires quantity/unit.

**Relationships and invariants:** Exactly one parent; optional supplier/stock mapping; many matches/movements. Accepted/rejected portions do not overlap; lot/expiry is conditional.

**Acceptance — GOL-DR-019.AC1:** Unknown receipt price does not invalidate measured acceptance; unknown received quantity cannot be derived from invoiced money or post trustworthy stock.

#### GOL-DR-020 — Purchase match allocation

**C | Trace:** S01; S04 E20; C07,C08. **Validation:** OD-06,OD-08.

**Grain and conditional minimum:** One association between two typed endpoints: endpoint identities/levels, match type/basis; quantitative match requires quantity/unit or value/currency.

**Relationships and invariants:** Exactly two document or line endpoints; many-to-many matches support partial/consolidated documents. Allocation is not extra activity.

**Acceptance — GOL-DR-020.AC1:** Document match cannot prove line agreement; over-allocation is explicit. Missing matches leave independently known receipts/charges intact.

#### GOL-DR-021 — Stock item

**C | Trace:** S01; S04 E21; C09,C10,C11,C12. **Validation:** OD-05.

**Grain and conditional minimum:** One materially identifiable inventory concept: identity, description, base unit for quantity accounting; interchangeability basis for aggregation.

**Relationships and invariants:** Many locations/movements/counts/recipe/pack links; lot/expiry controls conditional. Units and non-interchangeability remain explicit.

**Acceptance — GOL-DR-021.AC1:** Unknown category does not erase known quantity; unknown base conversion prevents incompatible-pack aggregation.

#### GOL-DR-022 — Recipe version

**C | Trace:** S01; S04 E22; C01,C11,C12. **Validation:** OD-05.

**Grain and conditional minimum:** One output specification/version: output identity/description, positive output quantity/unit, effective start; complete ingredient assertion needs defined components and usable yield basis.

**Relationships and invariants:** One output concept, optional product/prepared-stock mapping; many components/sales/production inputs. Version attribution is explicit.

**Acceptance — GOL-DR-022.AC1:** Unknown yield/components prevents complete theoretical usage/cost; recipe cost never becomes evidence of actual COGS.

#### GOL-DR-023 — Recipe component

**C | Trace:** S01; S04 E23; C11,C12. **Validation:** OD-05.

**Grain and conditional minimum:** One input occurrence/preparation stage: recipe version, identifiable input, quantity/unit and gross/net basis; conversion and substitution effects where required.

**Relationships and invariants:** Exactly one recipe version; optional stock mapping/prepared-input dependency. Dependency cycles are invalid; component/recipe losses count once.

**Acceptance — GOL-DR-023.AC1:** Unmapped ingredient remains descriptive evidence but cannot prove stock-specific usage. Negative quantities are invalid; zero requires meaningful omission.

#### GOL-DR-024 — Stock movement

**C | Trace:** S01; S04 E24; C07,C09,C12. **Validation:** OD-02,OD-05.

**Grain and conditional minimum:** One item/location change leg: item, location, signed quantity/unit, type, effective time/cutoff precision, evidence status. Adjustment needs reason/basis; authorised status needs evidence; transfer needs counterpart or pending state.

**Relationships and invariants:** One originating fact per leg with other related evidence; grouped transfer/production legs. Counts are not movements; theoretical usage is not observed posting.

**Acceptance — GOL-DR-024.AC1:** Incomplete item/location/quantity leaves balance incomplete; transfer nets item quantity venue-wide, and negative balances stay visible. Source changes are observed read-only.

#### GOL-DR-025 — Wastage observation

**C | Trace:** S01; S04 E25; C12. **Validation:** OD-05,OD-08.

**Grain and conditional minimum:** One item/output loss occurrence: material identity, location/scope, occurrence precision, reason (including unknown); measured waste requires quantity/unit and measured/estimated method.

**Relationships and invariants:** Optional stock/recipe/batch and disposal links; several linked legs may represent wasted preparation inputs. Cost needs independent basis.

**Acceptance — GOL-DR-025.AC1:** Unknown quantity permits an incident, not a zero/complete measured loss; disposal/waste count once. A discrepancy alone never proves waste.

#### GOL-DR-026 — Stock count session

**C | Trace:** S01; S04 E26; C10. **Validation:** OD-02,OD-05.

**Grain and conditional minimum:** One intended count/recount exercise: venue, planned item/location population, cutoff and completion status.

**Relationships and invariants:** Many observations; optional previous session and movement-reconciliation policy. Completed status and observed population are separately meaningful.

**Acceptance — GOL-DR-026.AC1:** A completed session with missing observations remains incomplete for full stock measurement; unknown cutoff limits reconciled balance claims.

#### GOL-DR-027 — Stock count observation

**C | Trace:** S01; S04 E27; C10. **Validation:** OD-05.

**Grain and conditional minimum:** One item/location/optional lot observation/revision: session, item, location, observed quantity/unit and observation time or confirmed cutoff basis.

**Relationships and invariants:** Exactly one session/item/location; optional partial-container children and conversion. Recounts supersede; adjustments remain separate evidence.

**Acceptance — GOL-DR-027.AC1:** Zero is affirmative; no observation is uncounted. Unknown book quantity prevents variance, not physical reporting; duplicates/subcounts do not add twice.

#### GOL-DR-028 — Consumption assessment

**C | Trace:** S01; S04 E28; C11,C12. **Validation:** OD-05,OD-09.

**Grain and conditional minimum:** One item/scope/bounded interval/method revision: quantity/unit, method/basis, completeness/estimate status; variance requires both named compatible inputs.

**Relationships and invariants:** Many sale/modifier/recipe or count/movement inputs; assessment is not an additional stock movement. Residual exclusions and known losses are explicit.

**Acceptance — GOL-DR-028.AC1:** Missing endpoints/recipes/movements yield incomplete/unavailable usage; residual minus theoretical is unexplained, not automatically known waste.

#### GOL-DR-029 — Employee

**C | Trace:** S01; S04 E29; C13,C14,C15. **Validation:** OD-02,OD-07,OD-10.

**Grain and conditional minimum:** One worker: stable person identity and distinguishing label/reference; private/contact/competency data only for justified authorised purpose.

**Relationships and invariants:** Many assignments/shifts/timesheets/evidence; name/email/payroll alias alone does not establish unique identity. Historical work remains attributable after departure.

**Acceptance — GOL-DR-029.AC1:** Unknown worker interval remains unresolved attendance; missing unnecessary private fields does not invalidate identified work.

#### GOL-DR-030 — Position and work role

**C | Trace:** S01; S04 E30; C13,C14,C22. **Validation:** OD-02,OD-07.

**Grain and conditional minimum:** One duties definition/version: identity, label and distinguishing responsibility meaning; task competencies/delegations need their own applicable evidence.

**Relationships and invariants:** Many assignments and planned/actual allocations. Work role, employment classification, decision authority and software permissions differ.

**Acceptance — GOL-DR-030.AC1:** Title-only evidence leaves duties/authority unresolved rather than granting approval/access or proving qualifications.

#### GOL-DR-031 — Employment assignment

**C | Trace:** S01; S04 E31; C13,C16. **Validation:** OD-07,OD-10.

**Grain and conditional minimum:** One engagement/terms version: employee, identifiable employer/engagement and effective start; pay-verified claim requires applicable terms/classification and rule evidence.

**Relationships and invariants:** One employee/employer; many role/time/cost associations. Concurrent/re-engaged assignments remain distinguishable.

**Acceptance — GOL-DR-031.AC1:** Known attendance with unknown rate/classification remains usable work but unverified/incomplete pay; no award is inferred from workplace setting.

#### GOL-DR-032 — Roster version

**C | Trace:** S01; S04 E32; C14,C19. **Validation:** OD-02,OD-04,OD-07.

**Grain and conditional minimum:** One staffing plan version: scope, planning interval, version/status; publication-at claim needs publication time/evidence.

**Relationships and invariants:** Many planned shifts/demand/budget references; one venue scope; published and amended plans retain lineage.

**Acceptance — GOL-DR-032.AC1:** No roster is not an empty confirmed plan. Actual attendance never overwrites published planned hours.

#### GOL-DR-033 — Planned shift

**C | Trace:** S01; S04 E33; C14. **Validation:** OD-04,OD-07.

**Grain and conditional minimum:** One intended continuous assignment/slot: roster version, start/end and work scope; assigned claim requires worker; role-specific coverage requires defined role.

**Relationships and invariants:** Exactly one roster, zero/one employee, many actual intervals. Breaks and work allocations have their own basis; end follows start.

**Acceptance — GOL-DR-033.AC1:** Unfilled slot is valid; missing planned break is not no break. Overlap is an explicit planning conflict, not automatic duplicate actual work.

#### GOL-DR-034 — Timesheet

**C | Trace:** S01; S04 E34; C15,C16. **Validation:** OD-02,OD-07.

**Grain and conditional minimum:** One worker statement/version: employee, covered interval/scope, status; asserted submission/review/approval needs evidence/time.

**Relationships and invariants:** One employee; many actual intervals/cost links; overlapping statements deduplicate underlying work.

**Acceptance — GOL-DR-034.AC1:** Missing approval means unapproved, not unworked; empty confirmed no-work differs from missing intervals. Approved is not paid.

#### GOL-DR-035 — Actual work and break interval

**C | Trace:** S01; S04 E35; C15,C16. **Validation:** OD-03,OD-07.

**Grain and conditional minimum:** One worker/activity/allocation interval: employee, activity type, start; duration requires end; attributed role/department requires scope; break pay treatment is distinct.

**Relationships and invariants:** Optional timesheet/planned shift/assignment; many cost components; end cannot precede start; concurrent allocation sums do not exceed known work.

**Acceptance — GOL-DR-035.AC1:** Unknown clock-out means unknown duration, not rostered duration. Work/break overlaps remain unresolved and never double count; no break evidence means unknown break status.

#### GOL-DR-036 — Labour cost and entitlement component

**C | Trace:** S01; S04 E36; C16,C18. **Validation:** OD-07,OD-08.

**Grain and conditional minimum:** One worker/engagement/earning period/component/calculation revision: employee/engagement, type, period, amount/currency, status and cost/entitlement basis; rate formula requires quantity/rate evidence.

**Relationships and invariants:** Many time evidence/payment links; due, earned, accrued, paid and received times differ. Estimate/final replacement and accrual/payment cannot double cost.

**Acceptance — GOL-DR-036.AC1:** Unknown on-cost scope prevents complete loaded labour cost; known hours remain available. Unallocated payment cannot prove entitlement settled.

#### GOL-DR-037 — Reservation

**C | Trace:** S01; S04 E37; C04,C14. **Validation:** OD-03,OD-10.

**Grain and conditional minimum:** One booking party/request with amendment history: identity/status, intended date/time or pending time; booked-cover claim requires positive stated size.

**Relationships and invariants:** One venue; many visits/liability entries; contact identity only for justified booking purpose. Eligibility/no-show assessment is a separate rule.

**Acceptance — GOL-DR-037.AC1:** Unknown party size makes booked covers incomplete; missing visit link alone never establishes no-show; no invented customer profile is required.

#### GOL-DR-038 — Visit and actual covers

**C | Trace:** S01; S04 E38; C04,C05. **Validation:** OD-03,OD-09.

**Grain and conditional minimum:** One actual attendance episode: venue, identity, attendance evidence/time precision; covers require observed count, counting basis and observation/cutoff.

**Relationships and invariants:** Zero/one originating reservation; many checks via allocations; table/area history. Split parties and re-entry follow agreed rules.

**Acceptance — GOL-DR-038.AC1:** Booking size never substitutes for actual count. Recount/table move is not new people; unknown size permits visit count, not complete covers.

#### GOL-DR-039 — Service allocation

**C | Trace:** S01; S04 E39; C04,C18. **Validation:** OD-03,OD-09.

**Grain and conditional minimum:** One visit-to-check/line association: visit, check/line identity, basis/status; quantified attribution requires amount/currency or fraction and eligible sales basis.

**Relationships and invariants:** Exactly one visit/check and optional child line; many associations; whole-check/line allocations do not overlap or exceed eligible charges.

**Acceptance — GOL-DR-039.AC1:** Unquantified link is not precise spend attribution; unlinked populations remain visible; covers count once per visit, not per check.

#### GOL-DR-040 — Metric definition

**C | Trace:** S01; S04 E40; C18,C19,C22. **Validation:** OD-03,OD-08,OD-09.

**Grain and conditional minimum:** One effective metric definition/version: name, meaning, population/scope, formula/observation method, unit, time basis and missing/zero-denominator rule. Ratios name numerator/denominator; financial measures state tax/cost basis.

**Relationships and invariants:** Many measurements/plans; dependencies may reference other versions without circular calculation. Definition changes are versioned.

**Acceptance — GOL-DR-040.AC1:** Same label with different denominator/cost scope is not equivalent; undefined meaning cannot support an authoritative KPI.

#### GOL-DR-041 — Measurement

**C | Trace:** S01; S04 E41; C18,C20,C22. **Validation:** OD-09.

**Grain and conditional minimum:** One metric version/scope/period/scenario/result revision: metric version, scope, period/cutoff, status; available value has unit, unavailable has reason.

**Relationships and invariants:** Many underlying facts/measurements and optional comparable plan values; retain input coverage, calculation time, uncertainty and reconciliation state.

**Acceptance — GOL-DR-041.AC1:** Aggregate-only detail is disclosed; incomplete costs cannot prove final profit; incompatible currencies/tax/cost bases do not silently combine.

#### GOL-DR-042 — Plan and target value

**C | Trace:** S01; S04 E42; C19. **Validation:** OD-04,OD-08,OD-09,OD-11.

**Grain and conditional minimum:** One budget/forecast/target version: plan type/status, metric version, scope, period, value/unit, scenario/version; claimed approval requires evidence.

**Relationships and invariants:** One metric version, many comparable measurements; issue/publication/approval time and assumptions are explicit.

**Acceptance — GOL-DR-042.AC1:** Revised forecast does not overwrite budget; absent approved comparator makes budget variance unavailable, not actual minus zero.

#### GOL-DR-043 — Operational control evidence

**CC | Trace:** S01; S04 E43; C13,C21,C22. **Validation:** OD-02,OD-07,OD-10.

**Grain and conditional minimum:** One subject/control/incident/evidence assertion: subject identity, type, status/outcome, observation/validity basis; numeric reading needs value/unit; qualification claim needs identifiable issuer/evidence.

**Relationships and invariants:** One subject, many preceding/corrective links; conditional employee/stock/licence links and applicable criterion/version.

**Acceptance — GOL-DR-043.AC1:** No pass/fail without applicability/criterion; missing evidence is not compliance/breach. Claimed corrective completion requires verification evidence.

#### GOL-DR-044 — Workload observation or assumption

**C | Trace:** S01; S02,S06; derived extension for workload/context/local records/reporting; C05,C14,C19. **Validation:** OD-04,OD-09.

**Grain and conditional minimum:** One task/product/preparation workload assertion for a scope/period: identity, activity/output unit, effort quantity/unit or qualitative limitation, observed/estimated status, method/evidence and effective basis.

**Relationships and invariants:** Links to products/recipes/service tasks, skill requirements, work intervals or scenario assumptions where justified. Competing duties and parallel work are distinguished.

**Acceptance — GOL-DR-044.AC1:** Cocktail effort recorded as an estimate stays estimated; no evidence permits neither invented duration nor headcount-derived measured workload.

#### GOL-DR-045 — Contextual evidence item

**C | Trace:** S01; S02,S06; derived extension for workload/context/local records/reporting; C19,C20,C22. **Validation:** OD-01,OD-10.

**Grain and conditional minimum:** One review/article/internal-work/context observation: source/item identity, subject/venue relevance, type, source reference, publication/occurrence precision, received time and evidence/permission status.

**Relationships and invariants:** Optional booking, service, local follow-up or scenario relevance links. Author/contact details only for justified purpose; public opinion remains contextual.

**Acceptance — GOL-DR-045.AC1:** Duplicated articles count once as evidence, not additional attendance; missing publication date is unknown, not receipt date. Source task completion does not alone verify operational outcome.

#### GOL-DR-046 — Local annotation and correction proposal

**C | Trace:** S01; S02,S06; derived extension for workload/context/local records/reporting; C20,C22. **Validation:** OD-02,OD-10,OD-14.

**Grain and conditional minimum:** One authored local assertion/revision: target fact/decision, type, author/time, text or proposed value/meaning with units where needed, reason/evidence and review/status.

**Relationships and invariants:** Links original/superseded annotation and source fact separately. Authorised review changes proposal status, not source content.

**Acceptance — GOL-DR-046.AC1:** Original remains retrievable; proposal is not a corrected source fact. Sensitive annotations follow the same access purpose as their contents.

#### GOL-DR-047 — Local follow-up and decision record

**C | Trace:** S01; S02,S06; derived extension for workload/context/local records/reporting; C20,C22. **Validation:** OD-02,OD-14.

**Grain and conditional minimum:** One local action/decision lifecycle: identity, business issue/decision, source/insight link, proposed action, state, creator/time; owner, due date and decision/approval/closure evidence required only when claimed.

**Relationships and invariants:** Many annotations and external evidence links; accepted ownership, partial resolution, cancellation, reopening and verified completion are distinguishable.

**Acceptance — GOL-DR-047.AC1:** No owner is unassigned, not defaulted to a manager. Complete-without-evidence is unverified; local closure does not assert connected-system task execution.

#### GOL-DR-048 — Report specification and report instance

**C | Trace:** S01; S02,S06; derived extension for workload/context/local records/reporting; C18,C20,C22. **Validation:** OD-03,OD-09,OD-10,OD-13.

**Grain and conditional minimum:** One report definition/version and separately one prepared instance/revision: purpose, period policy, scope, selected metric versions/sections; instance has definition, week/period, as-of/prepared time, status and coverage.

**Relationships and invariants:** Many metric/results/evidence links; authorised audience/sign-off, cadence/deadline and export format belong to the agreed specification. Instances retain supersession lineage.

**Acceptance — GOL-DR-048.AC1:** Two revisions of the same week are distinguishable. Missing contributing platform makes relevant results partial/unavailable and the report visibly qualified, including exports.

## 7. Reporting and analytical contracts

All GOL-MET requirements are **C** proposals. S05 K01–K12 are source conventions, not approved Goldy's KPI definitions. The charter mandates unambiguous meanings; it does not mandate every proposed measure or formula. OD-09 must agree eligible population, units, numerator/denominator, financial basis, attribution, missing/zero handling, rounding/tolerance, comparability, owner and target if appropriate. OD-13 must choose the weekly report's measures; this catalogue is not its approved contents. Each result also satisfies GOL-DR-040–GOL-DR-042 and GOL-SEM-006–GOL-SEM-008.

GOL-FR-054 requires authorised inspection at the meaningful grain: venue, department/location, trading day/service period, product/variant/category, supplier, work-role and relevant employee scope. Useful breakdowns depend on validated responsibility and evidence, not on unlimited access to every dimension. Under GOL-SEM-010 and GOL-FR-054, aggregation preserves additive/non-additive meanings: ratios are recalculated from eligible components rather than averaged without a declared weighting; repeated covers or statement totals do not inflate subtotals. Current, previous comparable periods and budget/forecast comparators remain separately named.

#### GOL-MET-001 — Net sales and sales mix

**C | Trace:** S01; S05 K01; C01,C02,C18; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Eligible line consideration less agreed revenue reductions/reversals, excluding collected tax and separately classified liability/tip amounts; surcharge treatment is explicit. Product/category monetary mix divides compatible eligible net sales by total eligible net sales; quantity mix has its own sale-unit basis.

**Acceptance — GOL-MET-001.AC1:** Line/modifier/adjustment consideration reconciles without double counting. Unmapped products remain in known totals with mix coverage qualified; zero/missing denominator is unavailable, and negative reversal periods remain negative.

#### GOL-MET-002 — Spend per actual cover and average check

**C | Trace:** S01; S05 K02; C04,C18; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Spend per actual cover = eligible attributed net sales / matching actual covers. Average check = eligible check sales / eligible check count. Cover, check, split/merge and bar/food inclusion rules and charge-attribution coverage are explicit.

**Acceptance — GOL-MET-002.AC1:** Six booked persons, four actual covers and two checks never share a denominator by default. Unlinked sales or unknown cover count qualifies/unavailable spend per cover; missing counts never become booked size.

#### GOL-MET-003 — Service duration distribution

**C | Trace:** S01; S05 K03; C05,C20; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Eligible fulfilment instant minus order instant for a defined item or service stage; amended/partial orders have explicit eligibility. Report observed count/coverage and agreed distribution summaries.

**Acceptance — GOL-MET-003.AC1:** Missing service timestamp is excluded with visible coverage; check closure is not a proxy. Invalid time order is an exception, not negative duration silently averaged away.

#### GOL-MET-004 — Reservation no-show rate

**C | Trace:** S01; S05 K04; C04; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Explicitly assessed no-show reservations / eligible expected reservations under agreed cancellation and assessment-cutoff rules × 100. Booking-count and booked-person measures are separately defined.

**Acceptance — GOL-MET-004.AC1:** A missing arrival link remains unresolved without assessment evidence. Zero/missing eligible denominator produces unavailable rate; cancelled-ineligible bookings do not inflate it.

#### GOL-MET-005 — Stock balance and count variance

**C | Trace:** S01; S05 K05; C09,C10; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Book closing = opening + accepted receipts + transfers in + production outputs − transfers out − recorded consumption − recorded disposal ± other evidenced adjustments. Count variance = observed − book at matched item/location/cutoff.

**Acceptance — GOL-MET-005.AC1:** A count eight/book ten gives -2 physical units; absence of a count is not zero. Value variance additionally requires cost method/date; transfer/linked loss counted once.

#### GOL-MET-006 — Usage variance and recorded waste

**C | Trace:** S01; S05 K06; C11,C12; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Usage variance = named observed or residual depletion − theoretical usage at matched item/period/scope/unit. Recorded waste sums compatible evidenced waste quantities by reason; waste percentage needs its own agreed denominator.

**Acceptance — GOL-MET-006.AC1:** Do not sum litres with kilograms or count residual as known waste. Theoretical calculation shows recipe/modifier/yield coverage; known disposal is not subtracted twice in residual reconciliation.

#### GOL-MET-007 — COGS, gross profit and gross margin

**C | Trace:** S01; S05 K07; C01,C18; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** COGS is agreed carrying cost attributable to period goods sold. Periodic inventory calculations declare additions, conversion costs, returns, transfers and loss treatment. Gross profit = matched net revenue − COGS; gross margin % = gross profit / matched net revenue × 100. Category cost % matches category scopes.

**Acceptance — GOL-MET-007.AC1:** Purchases/payments alone cannot supply COGS. Missing valuation/cost coverage makes profit partial/estimated or unavailable; waste is not double-subtracted. Markup uses cost denominator and is separately named.

#### GOL-MET-008 — Labour cost and productivity

**C | Trace:** S01; S05 K08; C14,C15,C16,C18; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Labour % = defined labour cost / matched eligible net sales × 100. Sales per labour hour = matched net sales / explicitly worked or payable/paid hours. Included wage/penalty/allowance/super/leave/on-cost components and allocation method are explicit.

**Acceptance — GOL-MET-008.AC1:** Wages-only, loaded, forecast, provisional and final results are distinguishable. Accrual/payment and estimate/final replacements do not double cost; overlapped departmental allocations cannot inflate hours.

#### GOL-MET-009 — Prime cost

**C | Trace:** S01; S05 K09; C18,C19; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Where selected, prime cost = defined COGS + defined labour cost; prime cost % divides by matched net revenue. Both component scopes and non-overlapping inclusions are explicit.

**Acceptance — GOL-MET-009.AC1:** Missing one component means incomplete/unavailable prime cost, not the other component alone called total. No universal prime-cost target is adopted.

#### GOL-MET-010 — Budget variance and forecast error

**C | Trace:** S01; S05 K10; C19,C20; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Budget variance = actual − approved budget for same metric/version, period and population; favourable direction is measure-specific. Forecast error has agreed sign, issued version/horizon and eligible actual basis; absolute or relative summaries need explicit weighting/denominator rules.

**Acceptance — GOL-MET-010.AC1:** No approved budget means unavailable budget variance. Outcome evaluation uses the pre-outcome forecast version; zero actual in a relative-error calculation follows the agreed unavailable/alternative rule.

#### GOL-MET-011 — Reconciliation exception count, amount and age

**C | Trace:** S01; S05 K11; C02,C03,C07,C08,C10,C15,C17,C22; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Count and compatible outstanding amount of explicitly typed unresolved exceptions, as of a stated time. Age starts at the agreed business event; partial resolution changes remaining amount with history. Cash variance is observed − expected; settlement sign convention is declared.

**Acceptance — GOL-MET-011.AC1:** Resolved/superseded issues are not counted as current unresolved work. Unknown start/value remains unknown, not zero age/value; invoice, cash and staffing exception quantities are not summed as a single monetary loss.

#### GOL-MET-012 — Applicable control and verified action outcomes

**C | Trace:** S01; S05 K12; C13,C20,C21,C22; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Applicable overdue controls, expired required evidence and completed-and-verified actions use validated applicability, due/validity dates and completion criteria.

**Acceptance — GOL-MET-012.AC1:** Unknown applicability is reported separately from pass/fail or overdue. Locally marked complete but unverified work is not counted as verified resolution.

#### GOL-MET-013 — Forecast revenue and product-sales outputs

**C | Trace:** S01; S06; C01,C19; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Revenue outlook declares money/currency/tax basis and revenue scope. Product outlook declares product/variant, expected sale units and optionally distinct monetary sales under price assumptions. Both include issue time, evidence cutoff, horizon, version, driver assumptions and uncertainty.

**Acceptance — GOL-MET-013.AC1:** A quantity forecast does not appear as money; expected money is not units times an unstated price. Stockout-constrained sales, new products and missing history are disclosed; inadequacy produces qualified scenarios/unavailable results.

#### GOL-MET-014 — Availability, stockout and supplier outcomes

**C | Trace:** S01; S02,S05; C01,C06,C07,C12; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Stockout/availability incidents require direct observation or explicit evidenced unmet demand with time/scope. Supplier timeliness/in-full comparison needs promised and accepted quantities/times and agreed eligibility. Excess/usable holdings use agreed criteria.

**Acceptance — GOL-MET-014.AC1:** Zero sales alone is not a stockout; a rejected delivery is not full usable supply. Unknown promised time prevents an on-time score; unknown excess policy prevents authoritative excess classification.

#### GOL-MET-015 — Report automation outcome

**C | Trace:** S01; S06; C20,C22; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Report preparation effort is observed person time for assembly, checking and corrections under an agreed report definition; timeliness is ready/status time against the agreed deadline. Compare equivalent report scope/coverage and record manual interventions/reissues.

**Acceptance — GOL-MET-015.AC1:** Less preparation time with more hidden gaps or correction work is not unqualified improvement. No baseline or agreed deadline means the corresponding improvement/timeliness conclusion is unavailable.

#### GOL-MET-016 — Operating profit and cash commitments

**C | Trace:** S01; S01,S02,S05; C08,C16,C18,C19; BG-01–BG-03. **Validation:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion.

**Candidate contract:** Operating/net profit uses a validated account/expense/period scope beyond gross profit. Cash position, due payables, customer liabilities and future commitments have separate money/status/as-of definitions, not implicit profit classification.

**Acceptance — GOL-MET-016.AC1:** Missing rent/other included expenses prevents a complete net-profit claim. Open purchase commitments remain commitments rather than paid expenses; cash received cannot replace revenue recognition evidence.

## 8. Information access and role permissions

Validated responsibility, business purpose, record sensitivity, venue/department/worker scope, permitted operation and effective delegation determine access. Job titles alone do not. The following rules specify outcomes and validation duties, not an invented role-permission matrix. OD-02 and OD-10 remain open; baseline safeguards can be stated without approving detailed grants.

Candidate access needs to discuss: owner/general manager weekly reports and decision summaries; FOH manager front-of-house staffing/order evidence; head chef back-of-house roster/order evidence; sub-head chefs back-of-house ordering evidence; operational staff assigned task information and permitted local evidence; bookkeeping/payroll function financial and individual-pay detail where justified. These are candidate purposes, not current entitlements. General manager and venue manager boundaries, cross-department access, employee self-access and adviser access all require validation. Aggregate labour cost may serve planning without individual pay details.

#### GOL-ACC-001 — Validate explicit grants

**B | Trace:** S01,S02; C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Every grant must identify authorised person/function, responsibility/purpose, information category, scope, permitted operations, effective interval and approval/delegation evidence. Viewing, annotating, reviewing, defining reports and exporting are separate permissions.

**Acceptance — GOL-ACC-001.AC1:** A title-only account receives no inferred grant. A FOH staffing purpose does not automatically expose kitchen/private-pay records or allow report-definition changes.

#### GOL-ACC-002 — Enforce scope for every view and export

**B | Trace:** S01,S02; C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Access restrictions must apply consistently to summaries, detailed evidence, searches, comparisons, local records and CSV/Excel output.

**Acceptance — GOL-ACC-002.AC1:** A denied record cannot be recovered through a total drill-down or export. A user allowed only departmental results sees only that authorised scope; a summary cannot leak a restricted small-population value contrary to the validated policy.

#### GOL-ACC-003 — Sensitive worker/customer information

**B | Trace:** S01,S02; C13,C16,C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Collection and access to personal, employment, pay, health/absence and customer-contact information must have a validated purpose and appropriate minimum scope.

**Acceptance — GOL-ACC-003.AC1:** Staffing gaps can be shown without unjustified sickness details or individual wages. An ordinary walk-in need not have a customer profile; annotations cannot bypass personal-data restrictions.

#### GOL-ACC-004 — Operational responsibility and approval separation

**C | Trace:** S01,S02,S05; C20,C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Delegated performer/verifier/approver functions and any separation/self-approval limits must be enforceable for permitted local decisions and records after validation.

**Acceptance — GOL-ACC-004.AC1:** If policy requires independent verification, the same person cannot claim that verified status without the necessary role separation/evidence. If no such policy is validated, the PRD does not invent a universal separation rule.

#### GOL-ACC-005 — Changed duties and revoked access

**B | Trace:** S01; C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Access must follow effective responsibility/delegation changes and revocation while preserving attributable historical evidence.

**Acceptance — GOL-ACC-005.AC1:** After a validated revocation becomes effective, the user cannot newly view/export/revise restricted information. Historical authorship persists; already downloaded files are not claimed to be recalled or access-controlled retroactively.

#### GOL-ACC-006 — External and supporting participants

**CC | Trace:** S02; C16,C18,C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Supporting payroll/bookkeeping/adviser access must be explicitly authorised by purpose, information scope and duration, including relevant evidence-sharing limits.

**Acceptance — GOL-ACC-006.AC1:** An adviser can obtain only approved information during the agreed interval. Invitation or professional title alone does not establish indefinite full-venue access; electronic distribution is separately agreed.

#### GOL-ACC-007 — Accountability for sensitive local actions

**B | Trace:** S01,S02; C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Relevant grant changes, permitted sensitive exports, report-definition changes and local review/correction decisions must retain attributable who/what/when/scope and outcome evidence available only to authorised reviewers.

**Acceptance — GOL-ACC-007.AC1:** A sample changed definition/export can be attributed to its actor and authorised scope. An unknown historical source actor remains unknown; local accountability does not fabricate source approval.

#### GOL-ACC-008 — Purpose-bound source access and sharing

**B | Trace:** S02; C22; BG-05. **Validation:** OD-02,OD-10; OD-14 for local approvals.

Connected access and sharing must stay within authorised scope/purpose; a locally available source record does not authorise collecting every related record or distributing it to others.

**Acceptance — GOL-ACC-008.AC1:** A restricted internal task or staff record is not copied into a general weekly export. Automated report preparation grants no implicit email recipient or external messaging authority.

## 9. Operational exception handling

Exception handling is part of the ordinary decision, not a separate technical dashboard. GOL-FR-048 and GOL-FR-050 require users to see the affected decision, evidence state, owner or unassigned state, candidate response, due/escalation policy where agreed, and verified closure. OD-14 governs responsibility, materiality, deadlines and escalation. The following cases specialise existing requirement acceptance; they do not invent approval or escalation thresholds.

- **Unavailable, delayed or partial contributing platform:** GOL-FR-003/GOL-FR-004/GOL-DR-048/GOL-SEM-007/GOL-SEM-008. Qualify the affected report/forecast, show coverage and missing interval, retain the last available result with its own age, and identify the missing evidence. Do not relabel old or partial evidence as current/complete. Reissue retains lineage.
- **Duplicate, conflicting or unmapped facts:** GOL-SEM-001/GOL-SEM-009/GOL-FR-049. Preserve disagreement and original evidence; repeated evidence does not add activity. User may propose a correction or mapping with evidence; unresolved uncertainty is visible in affected measures.
- **Short-notice absence/unfilled capacity:** GOL-FR-032/GOL-FR-033. Show affected scope/time/skills and proposed cover or capacity alternatives; do not guarantee cover or expose private sickness detail without purpose. Action takes place externally.
- **Stockout, rejected delivery or uncertain supplier timing:** GOL-FR-011/GOL-FR-017–GOL-FR-022. Distinguish physical availability, forecast need, rejected/accepted supply, still-outstanding commitment and claims. Alternatives require explicit substitute/yield/conversion and applicable safety evidence; no automatic supplier order.
- **Incomplete count, negative stock, transfer mismatch or unexplained usage:** GOL-FR-023–GOL-FR-030. Show affected item/location/cutoff and missing counterpart/conversion; candidate recount/investigation does not become a posted adjustment, known waste or misconduct accusation.
- **Missing clock-out, unknown break, overlapping time or cost terms:** GOL-FR-034–GOL-FR-037. Preserve known work, flag unknown duration/cost/pay status, and seek external evidence. Roster is not a fallback for actual work; generic base rate is not verified pay.
- **Unmatched invoice/credit, unpaid check, cash or settlement difference:** GOL-FR-013–GOL-FR-016/GOL-FR-021/GOL-FR-022/GOL-FR-038. Keep independent obligation and money facts, distinguish timing from unexplained difference and retain remaining disputed value; no forced revenue/stock correction.
- **Late reversal/correction after publication:** GOL-SEM-005/GOL-DR-048/GOL-FR-002. Original report remains identifiable; corrected report has revision, reason and changed results. Correction-period policy remains OD-03/OD-08.
- **Insufficient history or changed conditions:** GOL-FR-041–GOL-FR-045. Qualify forecast/scenario, show evidence gaps and assumptions, and permit a decision to withhold reliance. Product withdrawal or stockout affects sales evidence; it is not proof of no demand.
- **Unknown/expired control applicability or evidence:** GOL-CON-004/GOL-FR-047/GOL-MET-012. Distinguish unknown applicability, expired required evidence, observed failure and missing observation. An operational urgency claim requires an applicable criterion and validated response owner.
- **Failed, oversized or incomplete export/report preparation:** GOL-FR-003/GOL-FR-051–GOL-FR-053/GOL-NFR-012. State failure/partial status and whether a usable complete artefact exists; disclose omissions and do not present truncated output as full.
- **Access denial or changed delegation:** GOL-ACC-001–GOL-ACC-008. Keep protected content concealed, explain the permitted business access path without leaking the record, and retain local accountability where required.

## 10. Non-functional business requirements

Baseline rules below are testable invariant outcomes. Timing, volume, availability and usability targets are candidate contracts awaiting stakeholder values; this PRD deliberately does not invent seconds, uptime percentages, recovery hours, retention years or universal service benchmarks. OD-12/OD-13 must bind those values before operational acceptance. Their absence blocks claims of readiness against those qualities, not completion of this candidate PRD.

#### GOL-NFR-001 — Decision-speed and report deadlines

**C | Trace:** S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-12,OD-13.

Agree maximum completion time for representative summary, forecast and CSV/Excel requests at validated business volumes, plus weekly report readiness deadline/cadence. Measure elapsed user time and stated evidence latency separately.

**Acceptance — GOL-NFR-001.AC1:** For the agreed workload/volume, record elapsed request-to-usable-result time against each approved bound. Until bounds and volumes exist, performance acceptance is pending; an old cached result cannot pass freshness by returning quickly.

#### GOL-NFR-002 — Business-time availability and degraded use

**C | Trace:** S02,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-04,OD-06,OD-13.

Agree needed operating/reporting windows, maximum disruption, support/escalation owner and what decisions may rely on last-known/partial information during disruption.

**Acceptance — GOL-NFR-002.AC1:** Within the agreed test window, interruption and qualified last-known behaviour are measured against approved limits; unavailable evidence is shown as unavailable. No continuous or offline operation is assumed.

#### GOL-NFR-003 — Recoverable local evidence and report history

**C | Trace:** S01,S02; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-10,OD-13,OD-14.

Agree acceptable loss/recovery limits for local annotations, scenarios, follow-up, report definitions and published report history; restoration must preserve identity, access and correction lineage.

**Acceptance — GOL-NFR-003.AC1:** A recovery exercise verifies the agreed latest retained records, relationships, status and access under the approved loss/time bounds. No value is declared until the stakeholder contract is set.

#### GOL-NFR-004 — Reproducibility and numerical reconciliation

**B | Trace:** S01; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-08,OD-09.

Reports, forecasts and exports must explain their calculation/version/input coverage; financial and quantity reconciliation must apply declared rounding/tolerance rather than invisible adjustments.

**Acceptance — GOL-NFR-004.AC1:** Identical definitions/inputs produce the same business results within agreed tolerance; a restatement is attributable. A sample rounding difference is exposed under its rule, not hidden as extra waste or sales.

#### GOL-NFR-005 — Confidentiality and authorised use

**B | Trace:** S01,S02; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-02,OD-10.

Business information must remain available only to authorised people and purposes, including summaries, local notes and exported content; sensitive access must retain the accountability required in section 8.

**Acceptance — GOL-NFR-005.AC1:** Attempted unauthorised detail, small-scope inference, export and annotation access reveals no protected information. Authorised operation remains within its validated purpose/scope; tests use approved handling rules.

#### GOL-NFR-006 — Usability for operational work

**C | Trace:** S01,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-01,OD-12,OD-13.

Owner/general manager and other validated roles must be able to complete agreed summary, evidence-check, forecast and export tasks without specialist data knowledge, under agreed task success/time/support bounds.

**Acceptance — GOL-NFR-006.AC1:** Representative users complete the agreed tasks and can explain period, unit, missing state and proposed action. Record completion rate, time, errors and assistance against agreed bounds; diagnostics do not replace the business task.

#### GOL-NFR-007 — Accessible interpretation and use

**C | Trace:** S01,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-12.

Agree relevant user accessibility needs and supported working conditions; critical meanings, qualifications and actions must remain understandable without relying solely on colour or hidden tooltips.

**Acceptance — GOL-NFR-007.AC1:** A user under each validated access condition can identify status/unit/scope and complete the agreed task. If formal accessibility conformance is required, name/version and test scope are agreed before claiming compliance.

#### GOL-NFR-008 — Business volume and growth envelope

**C | Trace:** S01,S02,S06; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-11,OD-13.

Agree maximum current-pub history, atomic activity, product/workforce/report volume, export size and concurrent use needed for the supported business tasks; opening scenarios do not imply multi-venue live scale.

**Acceptance — GOL-NFR-008.AC1:** Complete-report/export counts and reconciliation remain correct across the approved envelope, including large periods and simultaneous permitted requests. Limits and any partial result are visible; timing is measured under GOL-NFR-001.

#### GOL-NFR-009 — Record-class retention and lawful disposition

**CC | Trace:** S01,S03; C21,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-10.

After applicability verification, agree retention/retrieval/disposition for each relevant record class, preserving correction evidence and approved holds; retain only purpose-justified information.

**Acceptance — GOL-NFR-009.AC1:** For each adopted policy, required evidence is retrievable in its agreed readable form during the verified interval; due disposition follows the agreed authority and hold rule. No one-period-for-all retention rule or exemption is inferred.

#### GOL-NFR-010 — Business-readable dates, units and formats

**B | Trace:** S01,S06; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-03,OD-05,OD-08,OD-09.

Reports and exports must express agreed local reporting time, unambiguous date meaning, currency/tax basis, quantity units and missing/estimate states consistently; elapsed durations retain their actual instant basis.

**Acceptance — GOL-NFR-010.AC1:** An overnight/DST case and a mixed-tax/pack case are interpretable without assuming calendar date equals trading date or case equals serve. Spreadsheet display/precision does not change values.

#### GOL-NFR-011 — Interpretability of forecast uncertainty

**C | Trace:** S02,S06; C19,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-04,OD-09,OD-12.

Users must receive forecast limitations and assumptions in business terms sufficient to assess reliance, including known constraints, historical coverage and explicitly defined uncertainty basis.

**Acceptance — GOL-NFR-011.AC1:** A representative user distinguishes a qualified scenario from an evidence-supported forecast and identifies key assumptions. Uncertainty ranges are not called guaranteed bounds; an opaque confidence score does not substitute for evidence coverage.

#### GOL-NFR-012 — Complete and usable report/export artefacts

**C | Trace:** S06; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection. **Validation:** OD-10,OD-12,OD-13.

CSV and Excel output must be readable in agreed recipient tools, preserve meaningful types/characters and include all authorised selected information or explicitly disclose omission/failure.

**Acceptance — GOL-NFR-012.AC1:** Open representative files in agreed tools; verify row count, money/units/dates, quoting, missing states, readable formatting and totals. Untrusted text cannot execute as spreadsheet content; the original selection is not presented as fully exported after truncation.

## 11. Scope, explicit non-goals and prioritisation

### In scope

The complete charter's business information domains; canonical atomic semantics and minimum contracts; business role/responsibility/access validation; candidate reporting, reconciliation, operational insight, forecast and exception workflows; local evidence/follow-up; weekly report automation and CSV/formatted Excel exports; qualified current-pub evidence for second-pub opening planning; conditional control evidence only after applicability and purpose are established.

**[NOTE FOR PM — OD-01]** S06 gives reporting automation, forecasting and usable insights a stronger intent signal. It does not approve a release/MVP boundary, select the weekly report’s exact measures, rank all operational decisions or permit dropping other charter domains. No MVP feature cut or delivery sequence is claimed. Candidate scope is comprehensive so stakeholders can choose using real decision evidence; any later subset must preserve and explicitly account for unmet charter requirements.

### Explicit non-goals

- Writing/executing rosters, orders, bookings, payments, payroll, stock adjustments or external tasks in connected systems; replacing transactional operating systems.
- Autonomous staffing, purchasing, financial release, safety decisions or business-policy creation; automated report preparation is the requested automation boundary here.
- Treating a proposed correction as a corrected source fact or a local/external task status as verified business completion without evidence.
- Guaranteeing forecast accuracy, absence cover, product supply, demand, profit or successful opening; inventing lost sales from zero sales or causal explanations from correlation.
- Live second-pub operations, consolidated multi-venue management, or assuming identical new-site customers, service model and economics.
- Marketing pitch generation, publicity optimisation, customer profiling without justified purpose, or an unrestricted archive of reviews/articles/internal records.
- A universal award, wage rate, food-safety rule, liquor-control rule, privacy exemption, accounting valuation method or retention period without verified applicability.
- Adopting source/vendor calculations as canonical meaning, reconstructing unsupported atomic data from aggregates, or allowing source availability to define business need.
- Prescribing screens, navigation, branding, devices, architecture, schemas, algorithms or integrations beyond the true read-only boundary and requested CSV/Excel business outputs.
- Application repository inspection, code/database/infrastructure changes, migrations, deployments, implementation stories, sprint planning or any unrequested downstream workflow.
- Sending reports/messages to external recipients in this engagement. Future automated distribution, recipient consent, sharing method and authority are OD-13 decisions, not presumed by report preparation or export.

## 12. Candidate success evaluation

All success measures below are proposals with **no approved baseline, numeric target, cadence or ranked priority**. Record evaluator, eligible population, comparable baseline, observation method, target if appropriate, review date and evidence coverage under OD-09/OD-13. Acceptance of information integrity and product capability is different from demonstrating commercial improvement; neither guarantees causation.

- **SM-01 — Weekly report effort and readiness:** GOL-MET-015; validates GOL-FR-001–GOL-FR-004 and GOL-FR-051–GOL-FR-053. Compare equivalent report assembly/check/correction person time and availability at the agreed weekly deadline. **Counter-metrics:** report gaps, unnoticed basis differences, reissue/reconciliation effort and manual intervention; speed must not hide defects.
- **SM-02 — Quick actionable evidence:** sample validated decisions and record whether the user can identify the relevant fact/change, limitation and next authorised action; validates GOL-FR-005/GOL-FR-006/GOL-NFR-006. **Counter-metrics:** unsupported actions, unnecessary escalations, misleading summaries and inaccessible protected detail.
- **SM-03 — Forecast usefulness and error:** GOL-MET-010/GOL-MET-013; validates GOL-FR-041–GOL-FR-044. Compare issued forecasts with eligible actuals at agreed horizons and ask whether evidence was usable for selected decisions. **Counter-metrics:** excluded/unavailable forecasts, false certainty, stockout-constrained history and hindsight revisions. Do not improve reported error by excluding difficult cases without disclosure.
- **SM-04 — Staffing explanation and service outcomes:** compare matched services' workload assumptions, planned skills/capacity, actual work and observed fulfilment; validates GOL-FR-031–GOL-FR-036. **Counter-metrics:** service deterioration, unfilled work and missing time/cost coverage; a lower labour percentage alone is not success.
- **SM-05 — Replenishment explanation and availability:** GOL-MET-005/GOL-MET-006/GOL-MET-014; validates GOL-FR-017–GOL-FR-030. Compare evidenced shortages, usable holdings, waste/excess and delivery exceptions against stated horizons. **Counter-metrics:** excess stock, spoilage, uncertain counts/conversions and unrealistic delivery assumptions; over-ordering is not automatically improved supply.
- **SM-06 — Defensible opening planning:** sample agreed opening decisions for traceable evidence, alternatives, transfer assumptions, financial cost coverage and sensitivity; validates GOL-FR-045. **Counter-metrics:** untested new-site assumptions, cost omissions and false precision. Before opening, success is qualified planning; operating success requires later observation.
- **SM-07 — Trust and verified follow-up:** sample ordinary/exception records for reproducible meaning, legitimate missing/zero states, coverage and verified resolution; validates GOL-SEM-001–GOL-SEM-010/GOL-FR-048–GOL-FR-050. **Counter-metrics:** falsely closed work, unresolved disagreements hidden from measures, sensitive overcollection and unexplained source differences.

## 13. Open stakeholder decisions and assumption register

Every item remains **OPEN**. Proposed resolving participants below are consultation owners, not assigned Goldy's job duties or grants. Completion of this PRD retains these decisions; resolution requires actual evidence and recorded stakeholder agreement. Requirements remain candidate until their dependencies are met. OD-01 is a broad selection/priority gate; narrower requirements may be validated individually without treating the entire catalogue as approved.

### OD-01 — Which decisions and capabilities matter, and in what priority?

**Preserved from S02.** S06 reports that owner/general manager principally want automation, weekly reporting, forecasts, quick actionable insights and exports. This narrows the direction, but the report's decision purpose and cross-capability ranking remain open. Staffing, stock, performance/exception review, opening and evidence follow-up are still candidates; neither examples nor document order select priorities.

**Resolve with:** owner, general manager, relevant departmental managers/head chef/sub-head chefs and people performing the reporting work. **Evidence:** actual decision/report sessions, business impact, frequency/deadline, information gaps, alternatives, success measures and disagreements. **Revisit/gate:** before confirming decision-specific scope, primary UX tasks, KPI prominence or any release sequence. Affected: all candidate FRs; SM-01–SM-07.

### OD-02 — Responsibilities, authority, delegation and permissions

Preserve reported FOH rostering/ordering, head-chef BOH rostering/ordering and sub-head-chef ordering; validate actual performers, owners, verifiers, approvers, spend limits, self-approval and absence cover. Confirm owner/GM/venue/kitchen manager distinctions, staff and supporting-function duties; separate access from responsibility.

**Resolve with:** owner/GM and relevant actual role holders. **Evidence:** ordinary/exception cases, delegation records and justified information need. **Gate:** before access grants, local approvals or confirmed role journeys. Affected: GOL-ACC-001–GOL-ACC-008 and role-dependent FRs/DRs.

### OD-03 — Operating periods, department boundaries, attendance and attribution

Agree service periods, cutoff/timezone, department/location scope, calendar/reporting boundaries, actual-cover rules (bar-only, children, re-entry, split parties), sales/fulfilment/closure/late-reversal attribution and comparability.

**Resolve with:** service managers and report/accounting interpreters. **Evidence:** actual overnight service, split bookings/checks and correction examples. **Gate:** before adopting period/cover metrics or weekly period contract. Affected: GOL-DR-001–GOL-DR-003/GOL-DR-037–GOL-DR-040; time-dependent METs and FRs.

### OD-04 — Demand, workload, forecast horizons and absence response

Agree expected demand/effort observations, skill needs, competing duties, forecast horizons/lead times, acceptable uncertainty, decision deadlines and absence-cover process. Validate weather/product-mix relationships rather than assuming causation; forecasts may lack adequate evidence.

**Resolve with:** owner/GM, FOH managers/head chef and actual reporting users. **Evidence:** issued decisions and historical observations, preparation/service workload samples and absence cases. **Gate:** before confirming staffing rules or forecast fitness/targets. Affected: GOL-FR-031–GOL-FR-036/GOL-FR-041–GOL-FR-044/GOL-DR-044.

### OD-05 — Usable stock, recipes, yields, conversions and counts

Agree stock locations/items, usable-stock criteria, pack/portion/base conversions, effective recipes/modifier effects, yield/loss conventions, count practice/cutoffs, negative-balance treatment, conditional lot traceability and adjustment authority.

**Resolve with:** FOH/head chef/sub-head chefs, receiving/counting staff and relevant accounting interpreter. **Evidence:** real Aperol/parma preparation, receipts, counts and discrepancies. **Gate:** before trustworthy quantity-based stock/consumption contracts. Affected: GOL-DR-017–GOL-DR-028 and stock/recipe FRs/METs.

### OD-06 — Replenishment terms, ordering horizons and supply policy

Agree delivery schedules/lead times, outstanding-order status, safety stock/rounding, substitutions, supply commitments and when uncertain quantities are usable. No safety-stock or three-way approval rule is established.

**Resolve with:** actual ordering/receiving managers and delegated approver. **Evidence:** one order-to-delivery-to-credit chain, late/partial supply and shortage decisions. **Gate:** before confirmed replenishment proposals/supplier comparisons. Affected: GOL-FR-017–GOL-FR-022 and purchasing contracts.

### OD-07 — Employment applicability, work and labour-cost rules

Agree employer, applicable instrument/classification, break/pay treatment, effective rates/entitlements, loaded-cost components, department allocation, competency obligations and approved time/correction practice. No universal hours-times-base-rate or award assumption.

**Resolve with:** actual payroll/bookkeeping function, managers and appropriate employment adviser. **Evidence:** applicable current authoritative rules and representative pay/time cases. **Gate:** before pay/compliance/cost conclusions. Affected: GOL-DR-029–GOL-DR-036/GOL-FR-031–GOL-FR-037/GOL-MET-008.

### OD-08 — Commercial and accounting meaning

Agree comp/void/refund, tips/surcharges, deposits/vouchers, tax/recognition, price/discount basis, inventory valuation, loss treatment, COGS/expense/profit coverage, payable/liability/settlement treatment and rounding tolerance.

**Resolve with:** responsible accounting/bookkeeping function and business decision owners. **Evidence:** actual transactions, receipts, credits, payroll/account statements and verified applicable policy. **Gate:** before authoritative monetary KPI/report/scenario conclusions. Affected: commercial/money/cost DRs, GOL-FR-012–GOL-FR-016/GOL-FR-038–GOL-FR-040, financial METs.

### OD-09 — Metric contracts, targets and decision eligibility

Agree definitions, units, comparable populations, baselines, target direction/value, evaluation cadence, accepted evidence, freshness/completeness thresholds, denominator rules and how partial results may be used. No universal benchmark or numerical improvement target is supplied.

**Resolve with:** each selected measure's accountable evaluator and supporting interpreters. **Evidence:** ordinary and exception sample calculations, comparison sessions and stakeholder outcome criteria. **Gate:** before confirmed KPI/forecast acceptance or success claims. Affected: all GOL-MET requirements, SMs and GOL-DR-040–GOL-DR-042.

### OD-10 — Source purpose/access, privacy, retention and applicable controls

Agree relevant source availability/coverage and lawful purpose/permissions/retention, including bookings, reviews/articles and internal platforms such as Asana; validate personal-data minimisation and external access. Establish actual jurisdiction, licence/entity/employment/accounting/privacy/food-business applicability and current record-class duties.

**Resolve with:** authorised business/source owners and relevant legal/privacy/records/safety/licence advisers. **Evidence:** actual authorised records and current applicable authoritative references. **Gate:** before sensitive access, collection, retention or compliance adoption. Affected: GOL-CON-004/GOL-ACC-001–GOL-ACC-008/GOL-FR-046–GOL-FR-047/GOL-NFR-009. Asana is an explicit relevant source example, not an obligation to adopt its task structure or connect every platform.

### OD-11 — New-pub operating assumptions and scope

Agree location, customer segments, capacity, service model, resources, opening date and financial assumptions, selected decision areas and evidence-transfer criteria. The brief's approximately 6–12 months from 14 October 2026 is a reported expectation, not a committed launch date. Live/consolidated operations remain outside scope pending separate decision.

**Resolve with:** owner and relevant actual opening decision-makers. **Evidence:** new-site plan and local observations, alternatives and sensitivity. **Gate:** before relied-on opening scenarios or scope expansion. Affected: GOL-FR-045/GOL-CON-005.

### OD-12 — Working conditions, simplicity and usability

Agree supported working surfaces/devices/recipient tools, role task conditions, accessibility needs, summary/detail balance, export presentation and representative task success/time bounds. The brief did not establish a form factor; S06 requests simple fast insights and usable spreadsheet output, without prescribing navigation.

**Resolve with:** actual owner/GM/department/staff users and reporting recipients. **Evidence:** narrated sessions and evaluation of agreed tasks/artefacts. **Gate:** before confirmed primary UX hierarchy or usability/accessibility acceptance. Affected: GOL-FR-005/GOL-FR-053/GOL-NFR-001/GOL-NFR-006/GOL-NFR-007/GOL-NFR-012.

### OD-13 — Weekly-report, forecast and export operating contracts

**New detail gate from S06, preserving S02 target/timing/source unknowns.** Agree report contents/decision purpose, contributing platforms, grain, week cutoff, cadence/deadline, sign-off, reissue rules, readers and whether any automated distribution is wanted/authorised. Agree CSV versus formatted Excel uses, required history/volume/concurrency, deadline/performance/availability/recovery bounds, forecast products/horizons and report-automation baseline.

**Resolve with:** owner/GM, actual report preparer, data interpreters and intended recipients. **Evidence:** an existing business weekly spreadsheet/example report (no application repository inspection), timed preparation process, output review and representative ordinary/failed-source weeks. **Gate:** before claiming report/forecast/export operational readiness. Affected: GOL-FR-001–GOL-FR-006/GOL-FR-041–GOL-FR-044/GOL-FR-051–GOL-FR-053/GOL-DR-048 and quantitative NFRs.

### OD-14 — Local correction, follow-up and exception authority

Agree local annotation/correction states, materiality/prioritisation, work ownership, due/escalation rules, verifier/closure evidence and who may reopen or cancel. Do not treat a local record as external execution.

**Resolve with:** owner/GM and actual operational/evidence owners. **Evidence:** one disputed fact and one completed, partially resolved and reopened action. **Gate:** before confirmed follow-up lifecycle and action-outcome measures. Affected: GOL-FR-048–GOL-FR-050/GOL-DR-046–GOL-DR-047/GOL-MET-011–GOL-MET-012.

### Assumption register

- **AS-01 — [ASSUMPTION — UNVALIDATED] Weather and product mix can explain predictable workload/usage variation.** Preserved from brief. The hot-day Aperol/cocktail examples are reported observations without measured relationships. OD-04 requires suitable observations; no causality or predictive validity assumed.
- **AS-02 — [ASSUMPTION — UNVALIDATED] Sufficient evidence can be obtained for useful forecasts and opening scenarios.** Preserved from brief. OD-04/OD-10/OD-11/OD-13 require coverage and transferability assessment; inadequate evidence produces explicit gaps/qualified scenarios.
- **AS-03 — [ASSUMPTION — UNVALIDATED] The candidate UJ-01–UJ-08 narratives reflect useful future sessions.** They organise the proposed requirements, not validated workflows. OD-01/OD-02/OD-12 require real-session narration and confirmation; illustrative names carry no factual identity.
- **AS-04 — [ASSUMPTION — UNVALIDATED] The detailed S04 grains, relationships and S05 metric formulas are fit for Goldy's.** C/CC contracts preserve their proposal status. OD-03/OD-05/OD-07/OD-08/OD-09 require real evidence; charter coverage/semantic safeguards remain baseline even if candidate boundaries change.
- **AS-05 — [ASSUMPTION — UNVALIDATED] Automating the selected weekly report materially reduces total reporting effort.** S06 establishes intent, not measured benefit or a quantified current process. OD-13/SM-01 require assembly, checking and rework baselines of comparable scope.

S06’s automation/forecast/export intent and S02’s reported rostering/ordering duties are reported evidence, not new assumptions and not approval of all derived detail. No unvalidated jurisdiction, policy, permission, numerical target, device choice or priority has been silently supplied.

## 14. Acceptance, validation and document completion

### Requirement-level evidence

For each selected requirement, validation records the stable ID, current status/wording, capability/decision/constraint trace, resolved dependencies, authorised evaluator, evidence population, ordinary-case result and applicable exception/missing-data result. Requirement acceptance is pass, fail, blocked by evidence/decision, or not applicable with justification. Candidate requirements can be inspected/tested conceptually but cannot be claimed stakeholder-approved merely because their acceptance example is coherent. B safeguards remain applicable independently of unselected candidate features.

For GOL-NFR timing/availability/volume/usability/recovery and selected KPI improvement claims, bind the OD values and population before evaluation; an unset bound is **blocked/pending**, not “pass”. Atomic detail unavailable in a source is an information gap, never proof of compliance with a complete-detail contract. Revisions preserve stable IDs and prior agreements; scope rejection retires the requirement with rationale rather than reassigning its number.

### Cross-domain acceptance cases

These cases supplement individual ACs; sample amounts/quantities are test fixtures, not real Goldy's observations or adopted policies.

- **AT-01 — Weekly report ordinary and failed-source weeks:** GOL-FR-001–GOL-FR-006/GOL-DR-048/GOL-MET-015. An agreed complete week reconciles selected metrics without manual re-entry; an unavailable contributing source yields qualified affected results and explicit report readiness. Reissue retains original history and measures checking/rework effort.
- **AT-02 — CSV and Excel fidelity/access:** GOL-FR-051–GOL-FR-053/GOL-NFR-012/GOL-ACC-002. A selected authorised population with zero, missing, disputed values, quoted text and units matches exported row counts/totals. Excel summaries are readable and numeric values usable. Restricted detail is absent; untrusted text does not execute; omitted detail is disclosed.
- **AT-03 — Revenue/product forecast:** GOL-FR-041–GOL-FR-044/GOL-MET-013. A revenue forecast identifies basis/horizon; a product forecast separates units and money. Insufficient history/stockout-constrained observations are qualified. Outcome evaluation references the original issued forecast and matched actuals, not hindsight revisions.
- **AT-04 — Six booked, four attended, two checks:** GOL-FR-008/GOL-DR-037–GOL-DR-039/GOL-MET-002. Record intended six, observed four, checks two and qualified charge-attribution coverage; split/table changes do not inflate covers. Missing arrival is not automatic no-show.
- **AT-05 — Sale, deposit and payment:** GOL-FR-012–GOL-FR-014/GOL-DR-007–GOL-DR-013. An unpaid 100 sale and a separate 100 deposit remain different sale/receipt/liability facts under the validated recognition policy; a 60 payment allocation yields 40 outstanding, without inventing fulfilment or stock return.
- **AT-06 — Partial receipt and consolidated invoice:** GOL-FR-019–GOL-FR-022/GOL-DR-018–GOL-DR-020. Ten packs delivered/eight accepted/two rejected contribute only accepted converted stock; multi-document matches reconcile without duplication. Missing conversion leaves pack facts known and base balance incomplete; a non-stock expense never creates stock.
- **AT-07 — Stock count, transfer and waste:** GOL-FR-023–GOL-FR-030/GOL-MET-005–GOL-MET-006. Observed eight/book ten yields -2 variance; two-bottle internal transfer nets zero venue-wide; known waste/disposal counts once. No automatic adjustment, theft/waste label or source write occurs.
- **AT-08 — Planned, worked and paid time:** GOL-FR-033–GOL-FR-037/GOL-DR-032–GOL-DR-036. A changed roster retains publication history; unrostered work remains known, missing clock-out remains duration unknown, departmental allocation does not duplicate hours, unknown on-costs prevent complete loaded-cost claim. Overnight/DST/calendar meanings remain distinct.
- **AT-09 — Reconciliation, late correction and financial incompleteness:** GOL-FR-016/GOL-FR-038–GOL-FR-040/GOL-SEM-005/GOL-MET-007. Gross settlement 100/fee 3/bank 97 reconciles independently from sales. Missing COGS/other costs prevents final profit. A late reversal produces an identifiable revised report under the adopted attribution policy.
- **AT-10 — Local proposal and follow-up:** GOL-CON-001/GOL-FR-049–GOL-FR-050/GOL-DR-046–GOL-DR-047. A proposal retains original evidence; local completion without external/verification evidence is unverified. Unassigned, cancelled, partially resolved and reopened actions remain distinct; no connected write occurs.
- **AT-11 — Opening and applicability:** GOL-FR-045/GOL-FR-047/GOL-CON-004–GOL-CON-005. New-site scenarios identify transfer assumptions and missing cost/context evidence; no guaranteed demand/profit or live second-pub total. Unknown applicable rule does not become compliance/breach; expired required evidence needs a verified requirement.
- **AT-12 — Duplicate/aggregate-only source and denied detail:** GOL-SEM-001/GOL-SEM-006–GOL-SEM-009/GOL-ACC-001–GOL-ACC-008. Repeated evidence does not inflate totals; a known aggregate discloses unavailable atomic detail and unknown expected population does not yield 100% completeness. Denied detail stays protected in summaries, local notes and exports; revoked scope cannot be newly accessed.

### Completion versus readiness

This PRD is complete when the source coverage, stable IDs, candidate/baseline distinction, explicit non-goals, minimum contracts, acceptance and preserved open decisions have been checked and the native documents saved. Stakeholder-approved decision requirements, operational acceptance and business benefit remain separate gates.

OD-01/OD-02 block confirmed decision selection, authority and primary role priorities; OD-03/OD-08/OD-09 block authoritative reporting semantics; OD-10 blocks adoption of sensitive collection/access and legal/control duties; OD-12/OD-13 block quantitative quality and recurring-report readiness. Other OD items block the specific dependent candidates. They do not require invented decisions to finish this PRD. No downstream workflow, implementation inspection or build authorisation follows from document finalisation.

Supporting coaching/validation prompts and source-reconciliation/review notes are in [addendum.md](addendum.md). The canonical decisions and overrides are recorded in [.memlog.md](.memlog.md).
