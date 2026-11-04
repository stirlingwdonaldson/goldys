# Open decisions — Goldy's Unified Data Platform

Date: 14 October 2026 (Australia/Melbourne). Status: supporting documentation; stakeholder validation and operational acceptance pending.

Authority: [SRS v1.1](SRS.md), especially its source precedence and obligation/maturity rules. Existing SRS identifiers are retained. These documents explain the baseline and its proposals; they do not approve a capability, assign a grant, choose architecture or establish implementation acceptance. Revision 1.1 extends proposed information contracts under DR-049–DR-055 and existing IDs, as recorded in [REVISION-LOG.md](REVISION-LOG.md). These supporting documents mirror that SRS revision; they do not independently adopt policies or approve capabilities. Validation questions remain in [OPEN-DECISIONS.md](OPEN-DECISIONS.md).

## Register purpose and status

This register consolidates every OD-01–OD-14 decision, AS-01–AS-05 assumption and QP-01–QP-10 unbound acceptance parameter in the SRS, together with underlying [BMAD brief questions](briefs/brief-goldys-requirements-2026-10-14/addendum.md), [PRD questions](prds/prd-goldys-requirements-2026-10-14/prd.md#13-open-stakeholder-decisions-and-assumption-register), [coaching prompts](prds/prd-goldys-requirements-2026-10-14/addendum.md#prompts-for-later-stakeholder-validation), [domain validation questions](../research/business-domain-model.md#decisions-requiring-stakeholder-validation) and [UX behaviour](ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md). Subquestions elaborate inherited unknowns under the existing OD IDs; they add no requirements, policies, priorities or new OD identities.

All decisions remain **OPEN**, except the agreed three contexts and responsive-web direction within OD-12 and the explicit preservation of equal, unranked management home choices within OD-01. These refinements do not settle detailed navigation, supported devices, accessibility conformance, role grants or business priority.

Responsible stakeholders below are proposed consultation/resolution functions, not named assignees or evidence of approval authority. **Named accountable stakeholder, due date and final approver are not assigned in the source material.** Each remains to be validated under OD-02/GOV-001. No invented date or title-derived permission closes a decision.

For each eventual resolution retain participant/date, supporting evidence, chosen rule/value, rationale, disagreement, authorising stakeholder, affected requirement/version and change history. Unknown applicability or absent bounds is blocked for the affected acceptance assertion, not passed or marked not applicable. Document completion does not resolve the questions (GOV-003).

## Business decision register

### OD-01 — Decision selection and priority

**Status:** OPEN. **Responsible stakeholders:** Owner, GM and relevant role holders; include FOH, head chef, distinct kitchen/venue managers, sub-head chefs and staff as their actual work requires. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** **Preserved from S02.** S06 reports that owner/general manager principally want automation, weekly reporting, forecasts, quick actionable insights and exports. This narrows the direction, but the report's decision purpose and cross-capability ranking remain open. Staffing, stock, performance/exception review, opening and evidence follow-up are still candidates; neither examples nor document order select priorities.

**Unanswered questions:**

- Which actual decisions recur, how often, with what impact and success measures? Which proposed capability/journey deserves adoption and primary placement?
- What is the relative priority of reporting, outlook, coverage, supply, service, financial interpretation, follow-up and opening work? The two management home choices remain equally offered; any personal default needs explicit choice.
- Which features should be removed, consolidated or reached contextually because their primary placement lacks evidence of value?

**Evidence / resolution needed (SRS):** Actual report/decision sessions, frequency, impact and success measures.

**Affected contracts / acceptance consequence (SRS):** All proposed capabilities/functions, GOV-002 and primary UX; reporting/forecast/export intent narrows direction without ranking all work.

**Requirement-level reverse lookup:** GOV-002, BC-001, BC-002, BC-003, BC-004, BC-005, BC-006, BC-007, BC-008, BC-009, BC-010, BC-011, BC-012, BC-013, BC-014, BC-015, BC-016, BC-017, BC-018, BC-019, BC-020, BC-021, BC-022, DR-045, FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, FR-016, FR-017, FR-018, FR-019, FR-020, FR-021, FR-022, FR-023, FR-024, FR-025, FR-026, FR-027, FR-028, FR-029, FR-030, FR-031, FR-032, FR-033, FR-034, FR-035, FR-036, FR-037, FR-038, FR-039, FR-040, FR-041, FR-042, FR-043, FR-044, FR-045, FR-046, FR-047, FR-048, FR-049, FR-050, FR-051, FR-052, FR-053, FR-054, NFR-006, UX-001, UX-006, UX-007, UX-008, UX-009, UX-018, INT-006, GOV-003. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-01; BMAD PRD section 13 OD-01; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-02 — Responsibilities, delegations and grants

**Status:** OPEN. **Responsible stakeholders:** Owner/GM and actual role holders; grant/approval authority must itself be validated. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Preserve reported FOH rostering/ordering, head-chef BOH rostering/ordering and sub-head-chef ordering; validate actual performers, owners, verifiers, approvers, spend limits, self-approval and absence cover. Confirm owner/GM/venue/kitchen manager distinctions, staff and supporting-function duties; separate access from responsibility.

**Unanswered questions:**

- Who performs, owns the outcome, verifies evidence and approves consequential action for each adopted workflow? What are delegation/spend limits, self-approval rules and absence cover?
- Are GM/venue manager and head chef/kitchen manager distinct people/functions? What duties do operational staff, sub-head chefs and supporting participants actually hold?
- Who may view, annotate, review, define reports, export or administer which data for which purpose/scope/effective interval? Who may authorise/revoke grants?

**Evidence / resolution needed (SRS):** Performer/owner/verifier/approver, limits, absence cover and purpose-based permissions.

**Affected contracts / acceptance consequence (SRS):** GOV-001, ACC-001–ACC-008 and role-dependent FR/UX; preserve reported FOH/head/sub-head duties.

**Requirement-level reverse lookup:** GOV-001, DR-002, DR-010, DR-014, DR-024, DR-026, DR-029, DR-030, DR-032, DR-034, DR-043, DR-046, DR-047, DR-051, DR-055, FR-005, FR-006, FR-013, FR-015, FR-020, FR-021, FR-022, FR-025, FR-026, FR-031, FR-032, FR-033, FR-035, FR-037, FR-047, FR-048, FR-049, FR-050, FR-051, FR-053, FR-054, NFR-005, ACC-001, ACC-002, ACC-003, ACC-004, ACC-005, ACC-006, ACC-007, ACC-008, UX-001, UX-002, UX-003, UX-004, UX-005, UX-007, UX-008, UX-018. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-02; BMAD PRD section 13 OD-02; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-03 — Periods, scope, covers and attribution

**Status:** OPEN. **Responsible stakeholders:** Service managers and reporting/accounting interpreters. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree service periods, cutoff/timezone, department/location scope, calendar/reporting boundaries, actual-cover rules (bar-only, children, re-entry, split parties), sales/fulfilment/closure/late-reversal attribution and comparability.

**Unanswered questions:**

- What is the actual venue timezone, trading cutoff, named service interval and reporting/pay/accounting calendar? How do overnight tabs, DST and unequal weeks affect attribution?
- What are department, location, holding and capacity scopes? How are parent/child totals kept non-duplicating?
- How are bar-only guests, children, split parties, table moves, re-entry, split/merged checks and reservation outcomes counted? What evidence and cutoff establishes a no-show?
- Which times attribute ordered, fulfilled, closed, financially recognised or later-reversed activity, and what policy governs late corrections/comparability?

**Evidence / resolution needed (SRS):** Venue timezone/cutoff, service periods, department/location boundaries, re-entry/splits, sale/fulfilment/closure and late-correction policies.

**Affected contracts / acceptance consequence (SRS):** DR-001–DR-003, DR-037–DR-040, time-dependent FR/MET; no calendar/trading/pay equality.

**Requirement-level reverse lookup:** DR-001, DR-002, DR-003, DR-004, DR-005, DR-006, DR-007, DR-008, DR-009, DR-010, DR-012, DR-035, DR-037, DR-038, DR-039, DR-040, DR-048, DR-049, DR-050, DR-051, DR-052, DR-053, DR-054, DR-055, FR-001, FR-002, FR-004, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-027, FR-034, FR-038, FR-040, FR-041, FR-042, FR-044, FR-054, NFR-010, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-03; BMAD PRD section 13 OD-03; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-04 — Demand, workload and forecast fitness

**Status:** OPEN. **Responsible stakeholders:** Owner/GM, FOH managers, head chef and actual reporting users. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree expected demand/effort observations, skill needs, competing duties, forecast horizons/lead times, acceptable uncertainty, decision deadlines and absence-cover process. Validate weather/product-mix relationships rather than assuming causation; forecasts may lack adequate evidence.

**Unanswered questions:**

- What observations or estimates describe product/task effort, required skills, competing duties and parallel work? Is weather/product mix predictively useful on measured evidence?
- What lead time, horizon, coverage/freshness, fitness and uncertainty permit a staffing or forecast decision? How are stockout-constrained history and new products handled?
- What is the actual short-notice absence-cover process, who acts, and what can reasonably be expected without private sickness details?

**Evidence / resolution needed (SRS):** Work/skill observations, competing duties, horizons/lead times, evidence fitness, uncertainty and absence response.

**Affected contracts / acceptance consequence (SRS):** FR-031–FR-036, FR-041–FR-044, DR-044, NFR-011; no proven weather causality.

**Requirement-level reverse lookup:** DR-032, DR-033, DR-042, DR-044, DR-051, FR-009, FR-031, FR-032, FR-033, FR-041, FR-042, FR-043, FR-044, NFR-002, NFR-011, REL-004, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-04; BMAD PRD section 13 OD-04; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-05 — Stock, recipes, yields, conversions and counts

**Status:** OPEN. **Responsible stakeholders:** Actual ordering, receiving, counting and preparation people; FOH/head chef/sub-head chefs and relevant accounting interpreter. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree stock locations/items, usable-stock criteria, pack/portion/base conversions, effective recipes/modifier effects, yield/loss conventions, count practice/cutoffs, negative-balance treatment, conditional lot traceability and adjustment authority.

**Unanswered questions:**

- Which item/location/lot populations and stock states are usable? How are negative balances, partial containers, uncounted items and count cutoffs treated?
- Which products/variants, supplier packs, base units and portion conversions are equivalent, effective-dated and evidence-supported?
- Which recipes/components, modifier additions/omissions/substitutions, usable yields and preparation loss conventions fit actual operations? How is the applicable historical version selected?
- What receiving/counting/consumption/waste/adjustment evidence exists, and who may propose or verify corrections? Is conditional batch/lot/expiry tracking applicable?

**Evidence / resolution needed (SRS):** Usable-stock criteria, item/location/unit/pack mappings, recipes/modifiers, count cutoffs, loss/yield and adjustment evidence.

**Affected contracts / acceptance consequence (SRS):** DR-017–DR-028, FR-023–FR-030, physical MET; conditional lot tracking.

**Requirement-level reverse lookup:** DR-002, DR-004, DR-005, DR-008, DR-009, DR-017, DR-019, DR-021, DR-022, DR-023, DR-024, DR-025, DR-026, DR-027, DR-028, DR-050, DR-052, DR-053, FR-007, FR-011, FR-017, FR-018, FR-019, FR-020, FR-023, FR-024, FR-025, FR-026, FR-027, FR-028, FR-029, FR-030, FR-042, FR-043, NFR-010, UX-008, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-05; BMAD PRD section 13 OD-05; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-06 — Supply horizon and purchasing policy

**Status:** OPEN. **Responsible stakeholders:** Actual ordering/receiving people and the delegated consequential-action approver. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree delivery schedules/lead times, outstanding-order status, safety stock/rounding, substitutions, supply commitments and when uncertain quantities are usable. No safety-stock or three-way approval rule is established.

**Unanswered questions:**

- What are supplier schedules, promised/actual delivery times, lead times and outstanding-order states? What defines the replenishment horizon?
- Is a safety-stock, pack rounding, substitution or purchasing constraint policy applicable and authorised? What uncertainty permits a candidate quantity?
- Which cases need document/line matching or approval? A universal three-way approval rule remains unadopted.

**Evidence / resolution needed (SRS):** Delivery promises/lead times, outstanding-order states, pack rounding, substitutions and any safety stock/control.

**Affected contracts / acceptance consequence (SRS):** FR-017–FR-022; no assumed safety-stock or universal three-way approval.

**Requirement-level reverse lookup:** DR-016, DR-017, DR-018, DR-019, DR-020, DR-052, DR-053, FR-017, FR-018, FR-019, FR-020, FR-021, NFR-002, REL-004. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-06; BMAD PRD section 13 OD-06; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-07 — Employment and labour meaning

**Status:** OPEN. **Responsible stakeholders:** Actual payroll/bookkeeping function, relevant managers and appropriate employment adviser. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree employer, applicable instrument/classification, break/pay treatment, effective rates/entitlements, loaded-cost components, department allocation, competency obligations and approved time/correction practice. No universal hours-times-base-rate or award assumption.

**Unanswered questions:**

- Who is the actual employer and what employment instrument/classification and effective terms apply? Which current authoritative evidence validates them?
- How do breaks, work allocation, payable hours, wage/penalty/allowance/super/leave/on-cost components and accrual/payment differ? What defines wages-only versus loaded cost?
- Who provides, reviews and approves time/cost corrections, and which duties require evidenced competency or validity?

**Evidence / resolution needed (SRS):** Actual employer/instrument/classification, break/pay terms, effective entitlements, cost scope, allocation and competency rules.

**Affected contracts / acceptance consequence (SRS):** DR-029–DR-036, FR-031–FR-037, MET-008; no generic rate or hours-times-base-rate rule.

**Requirement-level reverse lookup:** DR-029, DR-030, DR-031, DR-032, DR-033, DR-034, DR-035, DR-036, DR-043, DR-051, FR-031, FR-032, FR-033, FR-034, FR-035, FR-036, FR-037, FR-047, CON-004. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-07; BMAD PRD section 13 OD-07; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-08 — Commercial and accounting meaning

**Status:** OPEN. **Responsible stakeholders:** Responsible accounting/bookkeeping function and business decision owners. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree comp/void/refund, tips/surcharges, deposits/vouchers, tax/recognition, price/discount basis, inventory valuation, loss treatment, COGS/expense/profit coverage, payable/liability/settlement treatment and rounding tolerance.

**Unanswered questions:**

- How are comp/void/refund, tip/surcharge, discount, deposit/voucher/credit, tax, liability, settlement and financial recognition classified?
- What inventory valuation, conversion cost, return/transfer/loss treatment and period COGS policy applies? What distinguishes theoretical ingredient cost from reconciled actual cost?
- Which expenses/account scope support gross, operating or net profit? Which cash/payable/commitment measures are independent?
- What monetary/quantity precision, rounding and reconciliation tolerance is agreed?

**Evidence / resolution needed (SRS):** Refund/comp/void, tips/surcharge, liability/GST, recognition, valuation, COGS/loss/expense/profit scope and rounding.

**Affected contracts / acceptance consequence (SRS):** Money/commercial DR, FR-012–FR-016, FR-038–FR-040, financial MET/NFR-004; no chosen valuation method.

**Requirement-level reverse lookup:** DR-006, DR-007, DR-008, DR-010, DR-011, DR-012, DR-013, DR-014, DR-015, DR-017, DR-018, DR-019, DR-020, DR-025, DR-036, DR-040, DR-042, DR-049, DR-050, FR-004, FR-012, FR-013, FR-014, FR-015, FR-016, FR-019, FR-021, FR-022, FR-028, FR-029, FR-036, FR-038, FR-039, FR-040, FR-041, FR-045, NFR-004, NFR-010, REL-002, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016, CON-004. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-08; BMAD PRD section 13 OD-08; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-09 — Metric contracts and decision eligibility

**Status:** OPEN. **Responsible stakeholders:** Each selected measure's accountable evaluator and supporting interpreters. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree definitions, units, comparable populations, baselines, target direction/value, evaluation cadence, accepted evidence, freshness/completeness thresholds, denominator rules and how partial results may be used. No universal benchmark or numerical improvement target is supplied.

**Unanswered questions:**

- For each selected metric, what are definition/version, eligible population, time basis, units, cost/tax basis, numerator/denominator, exclusions and comparability?
- What baseline, favourable direction, target if needed, evaluator and outcome-review cadence are agreed? Is optional prime cost selected?
- How are covered/expected/excluded populations measured, and what freshness, partial reliance and missing/zero denominator rules establish decision eligibility?
- Which forecast-error sign/weighting/relative-error convention applies to original issued versions and zero/missing actuals?

**Evidence / resolution needed (SRS):** Populations, definitions, units, baselines, comparability, target/direction if needed, freshness/coverage, denominator and partial reliance rules.

**Affected contracts / acceptance consequence (SRS):** DR-040–DR-042, MET-001–MET-016, BR-006–BR-008, REL-003–REL-004; no universal thresholds.

**Requirement-level reverse lookup:** GOV-002, DR-003, DR-028, DR-038, DR-039, DR-040, DR-041, DR-042, DR-044, DR-048, DR-049, DR-050, DR-053, DR-054, DR-055, FR-001, FR-002, FR-004, FR-005, FR-006, FR-008, FR-009, FR-010, FR-018, FR-028, FR-030, FR-036, FR-038, FR-039, FR-040, FR-041, FR-042, FR-043, FR-044, FR-048, FR-051, FR-052, FR-053, FR-054, NFR-004, NFR-010, NFR-011, UX-011, UX-013, REL-002, REL-003, REL-004, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-09; BMAD PRD section 13 OD-09; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-10 — Source purpose, privacy, records and applicable controls

**Status:** OPEN. **Responsible stakeholders:** Authorised business/source owners and relevant privacy, records, employment, accounting, safety, licence or legal advisers. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree relevant source availability/coverage and lawful purpose/permissions/retention, including bookings, reviews/articles and internal platforms such as Asana; validate personal-data minimisation and external access. Establish actual jurisdiction, licence/entity/employment/accounting/privacy/food-business applicability and current record-class duties.

**Unanswered questions:**

- Which external records are available, authorised and complete at the required grain? What source mapping, purpose, access and correction/deletion limits apply?
- Which reviews/articles/weather/internal work records, including Asana where relevant, support a validated question and permitted collection/retention?
- What jurisdiction, operator/entity, licence, employment, food/safety, privacy and recordkeeping rules actually apply? Which current authoritative evidence establishes applicability?
- Which sensitive worker/customer fields and small-population inference rules are justified? What time-limited adviser and export/distribution access is permitted?
- For each record class, what retention trigger/duration, retrieval form, hold and disposition authority applies?

**Evidence / resolution needed (SRS):** Actual access/coverage, lawful purpose, sensitive scope, jurisdiction/entity/licence/food/privacy/employment applicability and record-class policies.

**Affected contracts / acceptance consequence (SRS):** ACC, INT, FR-046–FR-047, DR-043, NFR-009, CON-004; no inferred exemption.

**Requirement-level reverse lookup:** DR-001, DR-005, DR-011, DR-013, DR-016, DR-029, DR-031, DR-037, DR-043, DR-045, DR-046, DR-048, DR-051, DR-055, FR-003, FR-006, FR-011, FR-014, FR-016, FR-031, FR-037, FR-046, FR-047, FR-049, FR-051, FR-052, FR-053, FR-054, NFR-003, NFR-005, NFR-009, NFR-012, ACC-001, ACC-002, ACC-003, ACC-004, ACC-005, ACC-006, ACC-007, ACC-008, INT-001, INT-006, INT-007, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016, CON-004. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-10; BMAD PRD section 13 OD-10; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-11 — New-pub assumptions and future scope

**Status:** OPEN. **Responsible stakeholders:** Owner and actual opening decision-makers. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree location, customer segments, capacity, service model, resources, opening date and financial assumptions, selected decision areas and evidence-transfer criteria. The brief's approximately 6–12 months from 14 October 2026 is a reported expectation, not a committed launch date. Live/consolidated operations remain outside scope pending separate decision.

**Unanswered questions:**

- What is known about new-site location, customers, capacity, menu/service/operating model, resources, costs and opening date?
- Which opening decisions are selected, and what current-pub evidence, local observations and transfer assumptions justify each alternative/sensitivity?
- Does any future request expand to live second-pub/consolidated operations? Such scope remains outside this baseline until separately decided.

**Evidence / resolution needed (SRS):** New-site location/customers/capacity/model/resources/date/costs, transfer evidence and selected scenario questions.

**Affected contracts / acceptance consequence (SRS):** FR-045, CON-005, NFR-008; reported 6–12 months is not a launch commitment.

**Requirement-level reverse lookup:** DR-042, FR-039, FR-043, FR-045, NFR-008, CON-005. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-11; BMAD PRD section 13 OD-11; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-12 — Working conditions, accessibility and usability

**Status:** OPEN. **Responsible stakeholders:** Actual owner/GM/department/staff users, report recipients and people with relevant assistive needs. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree supported working surfaces/devices/recipient tools, role task conditions, accessibility needs, summary/detail balance, export presentation and representative task success/time bounds. The brief did not establish a form factor; S06 requests simple fast insights and usable spreadsheet output, without prescribing navigation.

**Unanswered questions:**

- Which devices, browsers, touch/keyboard/assistive conditions and report-recipient tools are supported? Which real task sessions establish usable summary/detail balance?
- What task completion/time/error/assistance bounds and formal accessibility standard/version, if any, apply?
- Which proposed home modules, labels, layout and disclosures are usable? Exact visual tokens, type scale, colours, dimensions, grids and breakpoints remain unselected in DESIGN; these are presentation choices, not approved new features.

**Evidence / resolution needed (SRS):** Devices/browsers, assistive needs, task conditions/bounds and presentation; any formal accessibility standard/version.

**Affected contracts / acceptance consequence (SRS):** UX/NFR-006–NFR-007 and QP-06; responsive web and three contexts are agreed, detailed coverage remains open.

**Requirement-level reverse lookup:** GOV-002, FR-005, FR-052, FR-054, NFR-001, NFR-006, NFR-007, NFR-011, NFR-012, UX-001, UX-006, UX-009, UX-012, UX-014, UX-016, UX-017, UX-018. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-12; BMAD PRD section 13 OD-12; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-13 — Report, forecast and export operating contract

**Status:** OPEN. **Responsible stakeholders:** Owner/GM, actual report preparer, interpreters, authorised source owners and intended recipients. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** **New detail gate from S06, preserving S02 target/timing/source unknowns.** Agree report contents/decision purpose, contributing platforms, grain, week cutoff, cadence/deadline, sign-off, reissue rules, readers and whether any automated distribution is wanted/authorised. Agree CSV versus formatted Excel uses, required history/volume/concurrency, deadline/performance/availability/recovery bounds, forecast products/horizons and report-automation baseline.

**Unanswered questions:**

- What does the real weekly spreadsheet contain, who prepares/checks/reads/signs it, which decisions use it, and what are its period/grain/metric/section definitions?
- Which platforms, cadence, deadline/readiness/sign-off, partial-source behaviour and reissue history are agreed? Who may alter a report definition?
- Which revenue/product/variant forecast outputs, units, horizons and price/availability assumptions are required?
- Which CSV dialect, formatted Excel layout, metadata/missing-state conventions, recipient tools, row/volume/concurrency envelope and performance/availability/recovery bounds are required?
- What are the equivalent assembly/checking/correction effort baseline and evaluation period? Is distribution wanted, and what separate recipient/channel/cadence authority would be needed? Preparation/export does not authorise distribution.

**Evidence / resolution needed (SRS):** Real weekly spreadsheet/process baseline, fields/readers/week/cadence/deadline/sign-off/reissue, forecast products/horizon, exports/tools, volume/availability/recovery and separate distribution authority.

**Affected contracts / acceptance consequence (SRS):** FR-001–FR-006, FR-041–FR-044, FR-051–FR-054, DR-048, MET-015 and quantitative NFR/QP.

**Requirement-level reverse lookup:** DR-044, DR-048, FR-001, FR-002, FR-003, FR-004, FR-041, FR-042, FR-044, FR-051, FR-052, FR-053, FR-054, NFR-001, NFR-002, NFR-003, NFR-006, NFR-008, NFR-012, UX-017, UX-018, INT-005, INT-007, REL-003, REL-004, MET-001, MET-002, MET-003, MET-004, MET-005, MET-006, MET-007, MET-008, MET-009, MET-010, MET-011, MET-012, MET-013, MET-014, MET-015, MET-016. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-13; BMAD PRD section 13 OD-13; brief/UX refinements as noted above. No stakeholder answer is supplied.

### OD-14 — Local proposal/follow-up authority

**Status:** OPEN. **Responsible stakeholders:** Owner/GM and actual operational/evidence owners, with the permitted verifier/approver. **Named accountable owner / decision due date:** unassigned; validate authority and timing.

**Inherited planning question/scope:** Agree local annotation/correction states, materiality/prioritisation, work ownership, due/escalation rules, verifier/closure evidence and who may reopen or cancel. Do not treat a local record as external execution.

**Unanswered questions:**

- What annotation/proposal review states, materiality/prioritisation criteria and correction authority apply? A proposal remains separate from source truth.
- Who accepts ownership, sets any due date, escalates, verifies closure and may reopen/cancel? Which completion evidence proves external work and which separation of duties is required?
- What is the agreed treatment of partial resolution, supersession, failed/uncertain saves, retries/concurrent edits and retained/recovered local inputs?
- Are notification recipients/channels/schedules, urgent interruption, escalation and suppression needed and authorised? Nothing in report automation selects them.

**Evidence / resolution needed (SRS):** Correction states, prioritisation/materiality, ownership/acceptance, due/escalation, verifier/closure and reopen/cancel policy.

**Affected contracts / acceptance consequence (SRS):** FR-048–FR-050, DR-046–DR-047, MET-011–MET-012, UX-011/013/014; local state is not external execution.

**Requirement-level reverse lookup:** GOV-001, DR-046, DR-047, FR-026, FR-035, FR-048, FR-049, FR-050, NFR-003, ACC-001, ACC-002, ACC-003, ACC-004, ACC-005, ACC-006, ACC-007, ACC-008, UX-011, UX-013, UX-014, REL-006, GOV-003. This expands explicit Dependencies fields; shared applicability and affected-contract text also apply.

**Source:** SRS section 19 OD-14; BMAD PRD section 13 OD-14; brief/UX refinements as noted above. No stakeholder answer is supplied.

## Assumption register

All five assumptions remain unvalidated. Reported duties, user intent, agreed read-only/local-record boundaries and agreed contexts are evidence classifications, not assumptions. Client timezone establishes no venue timezone or legal jurisdiction.

| Existing ID | Unvalidated assumption | Responsible validating stakeholders | Decision gates / affected requirements | Evidence needed and consequence if unsupported |
|---|---|---|---|---|
| AS-01 | Weather/product mix explains predictable demand/workload/usage variation | Owner/GM, FOH/head chef and forecast/report users | OD-04; DR-044, FR-032/FR-041–FR-044/FR-046, MET-010/MET-013, NFR-011 | Suitable observed task, demand, product, availability and contextual evidence; supplied cocktail/Aperol examples establish no causality. Unsupported driver stays assumption/context, not measured prediction |
| AS-02 | Sufficient authorised evidence can be obtained for useful forecasts/opening scenarios | Authorised source owners, forecast users and opening decision-makers | OD-04/OD-10/OD-11/OD-13; INT-001–INT-002/INT-006, FR-041–FR-046, DR-042, NFR-011 | Validate grain, coverage, permission, fitness and transferability; inadequate evidence produces qualified scenario/unavailable result, without deleting domain needs |
| AS-03 | UJ-01–UJ-08 represent useful future sessions | Actual role holders and report recipients | OD-01/OD-02/OD-12; GOV-001–GOV-002, UX-001–UX-018 and journey-dependent FR | Narrate real ordinary/exception sessions; confirm decision value and scope. Journeys remain candidates; illustrative people are not actual user evidence |
| AS-04 | Proposed E01–E43 grains/cardinalities and KPI conventions fit Goldy's | Relevant service, ordering/preparation/counting, payroll and accounting interpreters; metric evaluators | OD-03/OD-05/OD-07/OD-08/OD-09; DR-001–DR-043, MET-001–MET-016, BR-001–BR-010 | Real forms/facts and independent ordinary/exception calculations. Revise proposed boundaries if needed while preserving charter coverage and semantic safeguards |
| AS-05 | Automation materially reduces total reporting effort | Actual report preparer/checker, owner/GM and MET-015 evaluator | OD-13/OD-09; FR-001–FR-006/FR-051–FR-053, DR-048, MET-015, NFR-001 | Equivalent scope/coverage baseline and observation of assembly, checking, correction/reissue and deadlines; generation speed alone cannot establish benefit |

## Unbound quality parameters

No value or policy below is a default. SRS section 11.1 retains QP identity; quantitative acceptance remains blocked until the appropriate stakeholders adopt the parameter and evidence verifies it.

| Parameter | Unanswered binding question | Affected requirements | Resolution gates | Responsible stakeholders |
|---|---|---|---|---|
| QP-01 | What maximum request-to-usable-result time applies per task/workload, report cadence/deadline and latency eligibility? | NFR-001; FR-003; related reporting/question workflows | OD-12/OD-13/OD-09 | Actual users, report preparer/recipients and metric evaluators |
| QP-02 | What operating/reporting windows, disruption allowance, degraded reliance and support/escalation responsibility apply? | NFR-002; UX-012/UX-017 and affected operational workflows | OD-04/OD-06/OD-13 | Actual service, ordering and reporting users plus authorised support function |
| QP-03 | What local-record/history loss, recovery time, retained recovery point and restore population is acceptable? | NFR-003; DR-046–DR-048; REL-006 | OD-10/OD-13/OD-14 | Authorised records owners, report users and local-record owners |
| QP-04 | What precision, rounding and tolerance applies to each money/quantity measure? | NFR-004; BR-003; financial/physical MET | OD-08/OD-09 | Accounting interpreter and selected measure evaluators |
| QP-05 | Which populations and small-scope/derived inferences require protection, under what approved handling rules? | NFR-005; ACC-002–ACC-003 | OD-02/OD-10 | Validated grant authority, business/source owners and privacy adviser |
| QP-06 | Which task completion/time/error/assistance bounds, devices/browsers, assistive needs and formal accessibility standard/version apply? | NFR-006–NFR-007; UX-016–UX-018 | OD-01/OD-12/OD-13 | Actual role holders, report recipients and users with relevant assistive needs |
| QP-07 | What atomic/history/product/workforce/report/export sizes and concurrent workload envelope apply? | NFR-008; NFR-001/NFR-012 where measured | OD-11/OD-13 | Owner/opening decision-makers, source/report owners and authorised operating-capacity function |
| QP-08 | Per record class, what purpose/applicable rule, retention trigger/duration, readable retrieval, holds and disposition authority apply? | NFR-009; ACC-003/ACC-008; BR-005/REL-005 | OD-10 | Actual business/records owners and relevant records/privacy/employment/accounting advisers |
| QP-09 | What forecast fitness/uncertainty and user-interpretation criteria apply to each decision? | NFR-011; FR-041–FR-044; MET-010/MET-013 | OD-04/OD-09/OD-12 | Forecast decision-makers, metric evaluators and actual users |
| QP-10 | Which recipient tools, CSV dialect/metadata/missing-state conventions, readable formatting and volume cases apply? | NFR-012; FR-051–FR-053; INT-007 | OD-10/OD-12/OD-13 | Report preparer/recipients and authorised privacy/source owners |

NFR-010 also depends on actual local-time, unit and tax meaning under OD-03/OD-05/OD-08. These unresolved business meanings are not filled by this document's Melbourne date context.

## Additional inherited boundaries requiring explicit future choice

These are cross-references to existing questions, not additional requirements or newly numbered decisions:

- **Visual presentation:** brand palette, fonts, scale, spacing, grids, exact breakpoints and contrast/conformance scope remain unselected in DESIGN; OD-12/QP-06. No mockup or selected visual identity is delivered here.
- **Notifications/distribution:** recipients, channel, schedule, escalation, suppression and urgency policy remain unresolved; OD-09/OD-10/OD-13/OD-14. Automatic report preparation and contextual exports authorise neither email nor messaging.
- **Employment/accounting/control law and retention:** actual entity/activity/jurisdiction, operative policy/standard/rule and record-class applicability need current authoritative verification by the responsible function; OD-07/OD-08/OD-10. Existing research is derivation with stated limits, not adoption of rates, deadlines, exemptions or control thresholds.
- **Second venue, native/offline and operational execution:** live second-pub consolidation, native mobile/offline queues/push/camera and external operational writes remain excluded until a separate scope decision. OD-11/OD-12/OD-13 and CON-001/CON-003/CON-005–CON-006 preserve those boundaries. Their exclusion is not an unanswered permission to implement them now.
- **Source versus software:** the saved implementation findings distinguish unavailable source/policy evidence from software gaps. Resolving software does not establish source coverage, stakeholder policy or acceptance; OD-03–OD-10/OD-13/OD-14 remain independent gates.

## Proposed additions register

**New requirement additions proposed by this supporting package: none.** No new requirement IDs, numerical defaults, authority assignments or integration commitments have been introduced. DR-044–DR-048 and MET-013–MET-016 are already SRS records and retain their proposed maturity; they are not additions made here. Detailed inherited entity and UX proposals retain existing requirement references and validation gates.

If a later stakeholder answer requires scope beyond the SRS, record it explicitly as a proposed addition with business question/rationale, proposing stakeholder/date, affected goal/capability and existing requirements, proposed contract/acceptance criteria, dependencies/risks and status. Promote it only through a separately evidenced scope/SRS revision; retain old IDs and history. This is the existing change-governance boundary, not authorisation to start another workflow.

## Completion boundary

These open questions intentionally remain unanswered. The supporting artefacts are saved documentation, not business approvals, implementation plans or operational acceptance evidence. No application code, database, infrastructure, source planning document or SRS was changed. Work stops with the five requested documents.

## Revision 1.1 validation subquestions

These elaborations record the proposed SRS corrections before stakeholder adoption. They retain existing OD identities, all still OPEN; no fixture answers a production question. See [REVISION-LOG.md](REVISION-LOG.md) and [worked fixtures](SRS-VERIFICATION-FIXTURES.md).

- **OD-03:** What boundary policy/version and timezone govern canonical attribution, availability recurrence exceptions and service-stage instants? Inclusive-start/exclusive-end remains a candidate (BR-002, DR-003/DR-051/DR-054; AT-08.BOUNDARY).
- **OD-04/OD-07:** Which confirmed declarations, dated exceptions and partial offers govern each interval; what adopted precedence resolves conflicts? Private absence reasons are unnecessary for coverage (DR-051, FR-031/FR-033; AT-08.AVAIL).
- **OD-05/OD-06:** Which holding conditions and commitments affect usable versus uncommitted supply, with what quantity and overlap evidence? Which original/current/accepted revised delivery promise governs the horizon or performance score? Lot/reservation evidence is conditional (DR-052/DR-053, FR-017–FR-019, MET-014; AT-07.HOLD/AT-06.PROMISE).
- **OD-08:** Which recognition allocations/classification versions, valuation methods, landed costs, credits, returns and loss scope apply? How are accounting and operational cost populations reconciled once? Which tax components and header/credit allocations support exclusive derivation, and how do liability releases settle/reverse charges? (DR-012/DR-049/DR-050, BR-003, FR-038, MET-007/MET-016; AT-05.REDEEM/AT-05.TAX/AT-09.POS/INC/REV).
- **OD-09:** Which service event pairings, amendment/remake eligibility and event/unit weighting define duration populations? Which selected promise baseline and supplier denominator define scores? Which deadline-miss versus still-overdue control population is intended? (DR-054/DR-053/DR-055, MET-003/MET-014/MET-012).
- **OD-10:** Which adopted control obligations, triggers/schedules, due windows and subject/observation fulfilment rules are applicable? Unknown applicability remains unresolved, with no inferred legal duty (DR-043/DR-055, FR-047; AT-11.DUE).
- **OD-13:** Which comparison window includes report assembly, checking and correction/reissue effort across revisions; how is shared person-time allocated and an observed baseline gathered? No time-tracking interface is mandated (DR-044/DR-048, MET-015; AT-01.EFFORT).
