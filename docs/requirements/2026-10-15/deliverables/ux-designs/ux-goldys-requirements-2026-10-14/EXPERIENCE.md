---
name: "Goldy's"
description: "Role-centred operational evidence, decisions and KPI feedback"
status: final
approval_status: stakeholder-validation-pending
created: 2026-10-14
updated: 2026-10-14
intent: create
mode: coaching
sources:
  - ../../../PROJECT_CHARTER.md
  - ../../SCOPE_AND_WORKFLOW_SEQUENCE.md
  - ../../briefs/brief-goldys-requirements-2026-10-14/brief.md
  - ../../prds/prd-goldys-requirements-2026-10-14/prd.md
  - ../../../research/business-capability-map.md
  - ../../../research/business-domain-model.md
visual_identity: DESIGN.md
---

# Goldy's — Experience Spine

## Foundation

**Role → Responsibility → Decision → Evidence → Action → KPI feedback.** Each primary experience must support this complete chain. A useful data source, existing feature or available calculation earns no navigation position by itself.

The agreed experience has three groups: **Front of house (FOH), Back of house (BOH), Upper management**. These are responsibility contexts, not permission bundles or three newly defined job titles. They contain the individual roles below. Responsive web supports reporting on the website and quick checks on phones. No UI framework, application architecture or vendor is selected. [DESIGN.md](DESIGN.md) is the minimal visual contract; this document owns behaviour. Both spines take precedence over any later illustrative mockup or imported design.

The current pub is the operating context. Second-pub work is explicitly labelled opening planning, never a second live venue or consolidated trading total. Connected systems remain read-only. Goldy's can retain authorised local annotations, correction proposals, scenarios, decisions and follow-up; consequential operating actions occur through existing external processes. No screen can publish a roster, submit an order, change a booking, post stock, alter pay, release money or update an external task.

### Decision status and evidence

- **Agreed:** the governing chain; three experience groups; responsive web; specification-only screens; upper-management report-led and overview-led home alternatives remain unranked; diagnostics and administration are secondary unless central to the active responsibility.
- **Reported:** FOH managers roster and order FOH items; the head chef rosters and orders BOH items; sub-head chefs also order BOH items. Owner/GM seek reporting automation, forecasts, actionable insights and exports; the owner needs opening-planning evidence.
- **Proposed:** screen names/compositions, all detailed KPI accountabilities, unreported duties and ordering of candidate work. These are UX hypotheses for validation, not permission grants or an implementation backlog. “Must” within a proposal describes its behavioural contract if adopted.
- **Unresolved:** PRD OD-01–OD-14 remain open except that this session resolves the three-group structure and responsive-web direction within OD-12, and explicitly preserves the upper-management priority choice within OD-01. Device/browser coverage, task-time targets and accessibility conformance are still open.

Stable `GUX-` identifiers name UX decisions and acceptance checks. They supplement the PRD's `GOL-` requirements without changing their B/C/CC status. A completed specification is not stakeholder approval of every candidate workflow.

In compact traces, `FR-`, `MET-`, `ACC-`, `SEM-`, `CON-`, `DR-` and `NFR-` mean the corresponding `GOL-` identifiers in the source PRD. Numeric ranges include both endpoints. C01–C22, E01–E43 and K01–K12 refer to the capability map/domain model, not new UX requirements.

### GUX-01 — Operational value test

For every proposed feature, identify its group and responsible role; the responsibility/evidence status; decision and question; timely evidence; permitted local or external action; outcome measure; and whether a simpler existing surface suffices. A primary destination additionally needs recurring independent work that cannot be served by a section, drill-down or contextual entry. Unknown frequency or impact is a validation gap, not justification for prominence.

Primary work shows the decision, relevant evidence qualification and next permitted action together. Technical details can be deferred; a limitation that changes reliance cannot. The feature dispositions and screen tiers below are proposed placements, not an agreed ranking of staffing, stock, financial or opening decisions.

## Responsibility and KPI Contracts

No source establishes a complete, validated accountability matrix. This section records what is known, what is proposed and what must be established with actual role holders. For every consequential decision validate performer, outcome owner, evidence verifier, action approver, delegation limits and absence cover separately (OD-02). Do not equate head chef with kitchen manager or GM with venue manager.

### GUX-02 — Upper management

**Actual/reported scope:** owner and GM are intended report/insight recipients; owner is the opening-evidence recipient. Report preparation, report sign-off, cross-business intervention and financial approval duties are not confirmed.

**Candidate responsibilities and KPIs:** owner: financial sustainability, resource choices and opening assumptions; GM: cross-business interpretation, recurring reporting and coordination; venue manager: service coordination and unresolved cross-department exceptions, only if delegated. Candidate feedback is K01 sales/mix, K07 gross profit/margin, K08 labour/productivity, K09 prime cost if selected, K10 budget/forecast variance, K11 reconciliation and K12 verified actions. PRD MET-015 report effort/timeliness and MET-016 operating profit/cash commitments supplement K01–K12. No title establishes individual accountability or a target.

| Role/function | Decisions and questions | Evidence at decision | Actions and KPI return | Secondary or hidden |
|---|---|---|---|---|
| Owner | Which result can I rely on? What merits intervention? Which opening assumption needs testing? | Qualified weekly results, compatible comparisons, financial/cash distinctions, commitments, original forecasts, scenario sensitivity and missing costs | Save decision/scenario, request evidence or record follow-up; act externally within validated authority; return to comparable results and assumption outcomes | Transaction feeds, individual pay/contact data, source diagnostics and administration; opening planning secondary until an active opening decision warrants a pin |
| General manager | Is this report ready? Which change needs whose response? What does the outlook change? | Report status/revision, meaningful changes, contributing coverage, departmental context, forecast limitations and follow-up ownership | Review/export within grants, record questions and agreed ownership; coordinate externally; revisit MET-015, K10–K12 and selected operating KPIs | Ingestion, raw records and configuration; private employment detail unless independently justified |
| Venue manager | What needs coordination during the selected service? What remains unresolved across departments? | Demand versus workload, availability, coverage/skills, service evidence, exception owner and due basis | Record/escalate local follow-up; coordinate service externally; revisit K03, K08, K11–K12 where assigned | Owner financial/opening detail and specialist payroll evidence unless required; no new manager-only experience group |
| Bookkeeping/payroll or adviser | Is a difference timing, classification, allocation or missing evidence? What prevents a reliable cost/pay conclusion? | Independently typed sales, payments, settlements, invoice/credit matches, cost/valuation coverage and scoped work/pay evidence | Save explanation/proposed correction, export permitted evidence, seek external correction; revisit K07–K08/K11 and MET-016 as applicable | Operational staffing/order controls, unrelated personal records and general full-venue access; supporting access is purpose-bound and time-limited |

Bookkeeping/payroll/advisers are conditional supporting functions within the appropriate authorised context, not assumed new positions or automatic Upper management membership. The same applies to evidence stewardship and administration.

### GUX-03 — Front of house

**Actual/reported scope:** FOH managers roster and order FOH items. Booking coordination, beverage specifications, cash custody, service verification and staff duties are candidate assignments from C01–C17, not established authority.

**Candidate KPI accountability:** manager: scoped K01 product/sales mix, K02 covers/check measures, K03 service, K04 assessed no-shows, K05 stock/count variance, K06 known waste/usage, K08 labour/coverage, K10 forecasts, K11 exceptions and K12 verified actions; MET-014 availability/supplier outcomes. Relevant subsets must be agreed. FOH operational staff have no established sales, margin or labour-cost accountability.

| Role/function | Decisions and questions | Evidence at decision | Actions and KPI return | Secondary or hidden |
|---|---|---|---|---|
| FOH manager | Do expected workload and skills match coverage? What stock is needed through the next delivery? What changed in arrivals, availability or service? | FOH roster version, availability/skills without private absence reasons, bookings versus actual visits/covers, product mix/effort assumptions, usable stock cutoff, outstanding supply, pack conversions and observed service times | Save coverage/order scenario, inspect exceptions, record follow-up; roster/order/service changes externally; revisit coverage, service, availability, waste and original forecast error | BOH personnel/pay, venue-wide finance, supplier administration, raw ingestion and configuration; cash/booking detail only when those responsibilities are assigned |
| Operational staff working FOH | What evidence or follow-up is relevant to my assigned work? What should I report or escalate? | Assigned service/task context, relevant offering/modifier instructions and their version, own permitted work evidence and issue status | Add permitted local observation/proposal, report missing evidence, see response; perform work externally; feedback is verified issue handling, not an invented personal performance score | Manager-wide forecasts, cost/margin/individual pay, other workers' records, customer contact and diagnostics unless separately justified |

A bar manager is not assumed to be a separate role. Beverage-related duties attach to the responsible FOH person after validation. Customer contact is shown only for a justified task; an ordinary walk-in needs no customer profile.

### GUX-04 — Back of house

**Actual/reported scope:** head chef rosters and orders BOH items; sub-head chefs order BOH items. Kitchen manager is a distinct charter role whose equivalence to head chef is unconfirmed. Recipe/yield ownership, receiving, counting, waste and food-control duties remain proposed.

**Candidate KPI accountability:** responsible BOH manager/head chef: K03 service, K05 stock/count variance, K06 usage/recorded waste, K08 labour/coverage, K10 forecast error, K11 exceptions, K12 verified actions and MET-014 availability/supplier outcomes. Theoretical ingredient cost/yield and K07 category cost/margin are shown only with their own basis and assigned responsibility. Sub-head chefs' candidate outcomes concern scoped supply/availability and order follow-up; do not infer labour accountability. BOH staff have no established department KPI ownership.

| Role/function | Decisions and questions | Evidence at decision | Actions and KPI return | Secondary or hidden |
|---|---|---|---|---|
| Head chef | What preparation/skills are needed? Is coverage credible? What ingredients are needed until replenishment? | Food/product outlook and uncertainty, preparation effort, recipe/modifier/yield versions, usable ingredients/count cutoff, pack conversions, outstanding receipts, BOH roster/skill coverage | Save scenario or proposal, request count/recipe evidence, record follow-up; roster/order/preparation changes externally; return to service, availability, waste, coverage and forecast outcomes | FOH staffing, individual pay, venue-wide accounts, ingestion and technical settings unless justified |
| Sub-head chef | What should be ordered, why and by when? What uncertainty could change the quantity? | BOH demand/recipe assumptions, usable supply, outstanding orders, conversions, delivery horizon and evidence gaps | Save purchasing scenario/follow-up, prepare scoped evidence for external ordering; return to accepted supply and availability outcomes | Rostering and labour-cost work by default until responsibility is validated; no authority inferred to approve spend or a head chef's work |
| Kitchen manager | Which kitchen responsibilities am I actually assigned? | Only evidence needed for validated duties; reuse head-chef patterns when those duties overlap | Same scoped local actions and outcome feedback as the assigned duty | No duplicate kitchen-manager dashboard; no head-chef equivalence or rostering authority inferred |
| Operational staff working BOH | What preparation/task evidence applies? What issue needs escalating? | Relevant versioned instructions, assigned issue context, permitted observation history; safety/allergen assertions only when evidenced | Add local observation/proposal and follow-up evidence; actual counts, receiving, disposal and preparation remain in external processes; see verification response | Department financial/labour summaries, peer records, purchasing authority and diagnostics unless separately required |

## Information Architecture

### GUX-05 — Three contexts, shared decision surfaces

The context selector shows only responsibility contexts supported by effective grants. Switching context changes relevant navigation, scope and home composition; it cannot grant access. A multi-responsibility person can switch among the three groups without a new identity. Single-context users need no persistent selector taking screen space. Every page names its current context, department/area, period and as-of time; retained preferences cannot silently carry an incompatible scope across contexts.

The following destination sets are **proposed**, conditional on validation of recurring work. Entries without an authorised purpose are omitted. A useful isolated task is reached contextually instead of earning another main destination.

| Context | Proposed navigation | Scope and limits |
|---|---|---|
| Upper management | Home; Reports; Outlook; Follow-up | Home exposes two equal, unranked alternatives: Weekly review and Operational overview. Reports includes performance questions and financial investigation; Outlook includes revenue/product forecasts and secondary Opening planning. No separate Analytics, Insights or Exports destination |
| Front of house | Home; Service; Stock & supply; Staffing; Follow-up | Service includes attendance, availability and service evidence; Stock & supply includes replenishment, receiving/count/waste evidence and recipe/pour references; Staffing is conditional on duty/grant. Staff see only relevant work entries, not a manager menu with unusable controls |
| Back of house | Home; Preparation & service; Stock & supply; Staffing; Follow-up | Preparation includes recipe/yield references and service evidence. Sub-head-chef navigation ordinarily omits Staffing pending confirmation; staff receive scoped work entries. No independent recipe catalogue in primary navigation without a recurring recipe-management responsibility |

Contextual **View evidence**, **Explain measure**, **Compare**, **Export** and **Record follow-up** actions reuse shared surfaces. Account/help preferences are secondary. A restricted **Evidence & administration** utility exposes only the authorised stewardship or administration work. Its entry is never a universal ingestion dashboard. A validated evidence steward can pin their evidence queue to Home in their existing context; this does not create a fourth group.

### Surface register and closure

| ID / surface | Reached from | Decision purpose and key flow |
|---|---|---|
| S01 Context home | Entry/context switch | Find relevant decisions and resume work; UJ-01, UJ-03–UJ-05, UJ-07–UJ-08 |
| S02 Weekly review and report history | Reports; Home alternative | Assess report usability, compare changes, export retained revision; UJ-01 |
| S03 Performance question and financial explanation | Report/overview measure; Reports | Answer scoped business questions; distinguish money, cost and accounting effects; UJ-06 |
| S04 Outlook and scenario comparison | Outlook; departmental forecast link | Revenue/product forecast, assumptions, issued-version outcomes; UJ-02 |
| S05 Coverage review | Staffing; department Home | Workload/skills versus plan and actual work; UJ-03 |
| S06 Stock through next delivery | Stock & supply; department Home | Explain supply, quantity options and uncertainty; UJ-04 |
| S07 Service/attendance or preparation review | Service / Preparation & service | Interpret arrival, fulfilment and availability exceptions; UJ-05 |
| S08 Evidence explanation | Contextual View evidence / Explain measure | Definition, qualified breakdown and original evidence; all UJs, especially UJ-06/UJ-08 |
| S09 Follow-up and local record detail | Follow-up; contextual action | Ownership, proposals, external evidence and verified closure; UJ-01–UJ-08 |
| S10 Opening planning | Outlook secondary entry or user pin | Compare current-pub evidence with new-site assumptions; UJ-07 |
| S11 Evidence & administration utility | Secondary utility; authorised work link | Resolve evidence/definition/grant issues within actual responsibility; UJ-08a |
| S12 Export review and result | Export within report/question/evidence | Confirm authorised scope, format and qualifications; UJ-01/UJ-06 |

S03–S08 are reusable page/detail patterns, not extra global destinations. S11 separates business evidence stewardship from restricted source/administrative details within one secondary utility; access to one does not grant the other. Actual data ingestion and source repair are external authorised processes, not specified connector controls. S12 is a contextual flow, not a file-management product. No entity E01–E43 implies its own screen.

| Surface | Proposed attention tier and operational reason |
|---|---|
| S01 | Entry: orient to responsibility and resume a relevant decision; its contents do not establish feature priority |
| S02 / S03 overview entry | Equal unranked Upper management alternatives: recurring report reliance versus cross-business interpretation |
| S04 | Primary candidate Upper management outlook; contextual FOH/BOH evidence for an upcoming decision |
| S05 / S06 / S07 | Same unranked departmental candidate tier: assigned staffing, replenishment, preparation/service decisions; no approval of relative impact |
| S03 financial detail / S08 / S12 | Contextual supporting work: explain, qualify and hand off the active decision; specialist financial work may become primary for its validated owner |
| S09 | Recurring scoped follow-up candidate, plus contextual action entry; closes ownership and outcome feedback |
| S10 | Secondary opening planning; pinnable when an active opening responsibility makes it recurring work |
| S11 | Secondary utility; a validated steward/administrator may make relevant work primary through a personal pin within an existing group |

## Role-specific Home Screens

### GUX-06 — Upper management: two unranked alternatives

No default is selected between **Weekly review** and **Operational overview**. First entry presents equally prominent, clearly labelled choices with one-sentence purposes. Choosing one opens that composition; remembering a personal choice requires explicit “Use this as my home”. A return preference is personalisation, not a business priority. Both remain directly reachable from Home. Do not use position, badge counts, preselection or onboarding to imply stakeholder ranking; evaluate both with the same tasks before choosing an organisational default.

**Weekly review composition:** context/period/as-of header; report readiness and revision; candidate decision summaries and qualified comparisons; relevant unresolved follow-up; report history and export. Each summary names the question it supports and links to explanation. Show missing/late evidence beside affected measures before export. The home is a report decision surface, not a configuration wizard or a wall of every KPI.

**Operational overview composition:** context/service-period/as-of header; relevant unresolved business questions grouped by department; selected comparable outcome summaries; outlook relevant to an upcoming decision; follow-up and links to reports. No automated priority score until materiality and deadlines are agreed. Reuse report measures rather than creating different totals. Unknown evidence and no known issue remain distinct.

The owner and GM share these alternatives. Differences follow confirmed responsibilities and grants, not separate navigation structures. Opening planning can be pinned for an active owner decision; it does not displace routine operating work by default. Specialist financial/evidence work can be personally pinned only within granted purpose.

### GUX-07 — Front of house home

Composition: FOH scope and selected service; relevant decision entries for service/attendance, coverage and stock through next delivery; unresolved assigned/unassigned follow-up within responsibility; selected outcome feedback. These are unranked candidate modules: no agreed business order or numerical priority is implied. Visible modules depend on duties and entitlements. A decision-changing warning stays beside its module even if detailed diagnostics are hidden.

A manager can review a coverage gap without seeing a private illness reason; a staff member sees relevant assigned work/issue status rather than department margin or team wages. A booking count is never labelled actual covers. A quick check can open evidence or record a permitted observation without crossing into roster/order execution.

### GUX-08 — Back of house home

Composition: BOH scope and selected preparation/service interval; relevant entries for preparation/availability, stock through next delivery and coverage; unresolved follow-up; selected outcome feedback. Head-chef coverage is supported by the reported duty; sub-head chefs' home includes ordering evidence without implying rostering. Kitchen managers receive only confirmed duty modules. Staff receive relevant task evidence and issue response.

Show preparation quantities separately from sales quantities; yield/conversion gaps qualify any ingredient proposal. Known waste and unexplained usage are separate. This is not a live kitchen display or a replacement order/preparation system. FOH/BOH module order and KPI prominence remain open for real-session validation.

## Feature Value and Screen Priority

### GUX-09 — Dispositions by operational value

The tiers below describe **attention within an active responsibility**, not feature development order. **Primary** means recurring decision work if validated. **Contextual** supports that work at the decision point. **Secondary** is specialist/infrequent work. **Conditional** has no committed prominence until purpose/duty is established. **Excluded** violates the agreed scope.

| Proposed capability | Value test and critique | Placement / trace |
|---|---|---|
| Weekly reporting and report history | Reduces repeated assembly and exposes reliance/reissue decisions; value must include checking/rework, not just generation speed | Primary candidate for Upper management, unranked against overview; S02; FR-001–006, MET-015 |
| Operational overview / actionable insights | Useful only when it names a decision, evidence and possible response; generic trends or another dashboard duplicate reports | Equal unranked Upper management home alternative; contextual departmental findings; S01/S03; FR-005,048,054 |
| Revenue/product outlook | Useful when horizon, sale units and uncertainty match a real upcoming decision; a forecast without an action horizon adds false precision | Primary candidate Upper management Outlook; contextual departmental link; S04; FR-041–044 |
| Staffing | Supports reported FOH/head-chef rostering decisions; revenue/headcount alone cannot establish workload | Primary candidate for assigned departmental duty; S05; FR-031–036; no blanket staff/sub-head-chef access |
| Stock, replenishment and supplier evidence | Supports reported ordering decisions through a delivery horizon; a stock list alone does not explain an order | Primary candidate departmental Stock & supply; S06; FR-017–030 |
| Recipes, menus, prices and modifiers | Explain preparation, historical demand and ingredient need; separate top-level catalogues add navigation without a proven independent job | Contextual in S06/S07/S08; FR-007,027–028; promote only for validated recurring specification responsibility |
| Bookings, covers, capacity and service | Supports arrival/service decisions if observations exist; a booking browser or transaction feed alone provides little decision support | Primary candidate FOH Service / BOH Preparation & service within duty; S07; FR-008–011 |
| Commercial, cash, supplier and labour reconciliation | Distinguishes timing, classification and actual unresolved differences; inappropriate for every manager's home | Contextual S03/S08; can be primary work for a validated supporting function; FR-012–016,021–022,034–040 |
| Financial KPI catalogue | Definitions matter; displaying every available ratio creates clutter and unsupported profit claims | Selected outcomes in S02/S03; definitions in S08; MET-001–012,016; no universal KPI dashboard |
| Opening scenarios | Useful for a specific owner decision; every current-pub relationship may fail to transfer | Secondary S10, personally pinnable for active work; FR-045; no second live-venue switch |
| Local annotations, correction proposals and follow-up | Closes the decision-to-action-to-feedback chain; a second unrestricted task manager would duplicate external systems | Contextual entry plus scoped Follow-up destination S09; FR-049–050 |
| CSV / formatted Excel | Explicit stakeholder intent and useful decision handoff; requires scope/qualification preservation | Contextual S12; FR-051–053; no primary Exports destination |
| Reviews, articles, weather and Asana context | Valuable only for a named business question; sentiment does not establish demand and external task status is not verified completion | Conditional contextual evidence S08; FR-046; no primary news, reviews or task-feed archive |
| Applicable controls/competency | May matter to assigned duties; missing evidence cannot prove pass or breach | Conditional contextual S07/S08/S09, then Home only for assigned actionable work; FR-047 |
| Definitions, provenance and evidence quality | Necessary to judge reliance; deep diagnostics distract from the business consequence | Qualifications inline; explanation S08; stewardship queue S11; SEM-001–010, FR-006,048–050 |
| Ingestion, source diagnostics, raw exploration | Helps an authorised steward resolve an affected decision; technical usefulness alone does not justify business navigation | Secondary S11 and bounded S08 drill-down; no universal Data/Sources/Raw records destinations |
| Access/system administration | Necessary only for the authorised administrator's duty; account ownership or role name does not prove that duty | Secondary restricted S11; ACC-001–008; no global administration home |
| External execution, automated approvals or an unrestricted query console | Crosses read-only/human-authority boundaries or lacks a justified business purpose | Excluded; CON-001,003,006 |

**Attention priority rule:** within the active work, put the decision and any reliance-changing warning first; relevant evidence and permitted action next; comparable KPI feedback alongside or immediately after; supporting detail behind explanation; technical configuration last. A known imminent issue can lead only under an agreed urgency/owner rule, with the reason visible. Until then let people filter/sort by explicit dates, type and ownership; no invented severity thresholds, red/green performance grades or automated prioritisation.

## Voice and Tone

Use short operational questions and precise verbs: “What changed?”, “What is missing?”, “What could this affect?”, “Record follow-up”, “Save scenario”, “Export Excel”. Never say “Order placed”, “Roster published”, “Payroll approved” or “Source corrected” after a local save. Use “Proposal saved in Goldy's. Make the change through your usual authorised process.”

Show “Not captured”, “Unknown”, “Disputed”, “Not applicable — {reason}” and actual `0` distinctly; a blank or dash must not carry several meanings. Example: “FOH actual hours are incomplete: clock-out evidence is missing. Labour cost is provisional.” A count discrepancy is “Unexplained usage variance”, not “Theft” or “Waste” without evidence. “No known issues in the available evidence” does not mean “All clear”.

Forecast copy states “Expected product sales: {quantity and sale unit}” separately from revenue with currency/tax basis. Explain the range's actual meaning; do not call scenarios guaranteed bounds. Express local dates unambiguously, with explicit reporting timezone and trading/service boundaries. This client's timezone is not proof of the venue's reporting policy.

## Component Patterns

### GUX-10 — Shared behavioural vocabulary

Visual anatomy is in DESIGN.md. Every component must work through keyboard, touch and assistive technology; essential qualifications never depend on hover.

| Component | Behavioural contract |
|---|---|
| Context and scope header | Names responsibility context, authorised area, interval, comparator, as-of and revision/status. Scope changes are explicit; reject incompatible retained selections with a reason. Preserve safe return context |
| Navigation and home choice | Three contexts only; applicable destinations within each. Upper-management alternatives have equal standing and no default. Personal pins/remembered home cannot expand permissions |
| Decision summary | Names question, affected scope, relevant result/comparator, qualification and next permitted action. Links to evidence and outcome; no unsupported causal diagnosis or decorative score |
| Measure and comparison | Label unit, definition/version, actual/estimate/final, numerator/denominator where relevant, tax/cost basis and coverage. Open explanation; recalculate ratios from matched components on filter changes |
| Exception notice | Names affected decision/result, known limitation, owner/unassigned state and permissible response. Inline at relevant measure; persistent for reliance-changing conditions; not a transient toast |
| Evidence explanation | Moves from plain explanation → definition/eligible components → original permitted evidence. Preserve grain, identities, effective/recorded times, source and correction lineage. Aggregate-only evidence ends honestly; restricted detail stays inaccessible |
| Business question controls | Explicit period/product/variant/area/supplier/work-role and comparator where supported. Applied filters visible and clearable; unsupported/restricted dimensions unavailable with safe explanation; no schema knowledge required |
| Evidence list | Business headings, stable sort, labelled pagination/result count and selection scope. Totals disclose page versus full-result population. Identical legitimate occurrences remain distinct; no unbounded raw-record wall |
| Forecast and scenario comparison | Separate actual history, issued forecast, changed local scenario and original-version error. Assumptions/horizon/units/uncertainty stay visible; local changes never rewrite actuals or issued history |
| Local record and follow-up | Explicit Save; retain source, author, reason, time, proposal, owner state, due basis and verification evidence as applicable. Unsaved/failed saves remain visible; no implicit external action or self-approval |
| Export review | Recap authorised scope/filters, result revision, format, inclusion/omission and caveats; export grant rechecked. Return progress, available file or failure. Partial results remain partial in the artefact; generation is not delivery to another person |
| Disclosure and dialog | Expand supplementary detail in place or open a focused detail page. One dialog at a time, labelled heading and explicit close; return focus to trigger. Do not put the main evidence hierarchy inside nested dialogs |
| Save and progress feedback | Announce editing/saving/saved/failed locally; never imply persistence until confirmed. Keep failed input for retry where permitted; cancel safely, avoid duplicate records on retry and state if outcome is uncertain |

Evidence explanations retain historical offering/price/category and recipe/modifier versions, typed purchase documents/matches, stock movement/count/usage distinctions, independent work/pay states and metric/plan versions from E01–E43 and DR-001–048. They do not reconstruct unavailable detail or require one page per entity. Show expected population, covered population and exclusions; if the expected population is unknown, do not invent a coverage percentage. Show last evidenced coverage separately from receipt/calculation time: a newly received old count is still old stock evidence. Where an agreed eligibility rule exists, show whether it is met; otherwise eligibility remains unvalidated. No source's own label or successful import establishes canonical meaning or full business coverage.

A ratio with a missing or zero denominator is unavailable with its reason, never zero or infinity; any alternative outcome measure needs an agreed definition. Known negative observations or reversal periods retain their sign. A favourable/unfavourable judgement needs the measure's agreed direction and comparator, not a generic assumption that an increase is good.

### GUX-11 — Decision-to-feedback loop

A local decision/follow-up references the question, evidence snapshot/revision, scope/period, chosen response or “defer reliance”, responsible person or unassigned state, and candidate outcome measure. Due date and outcome review interval are set only under agreed policy or explicit permitted input. No silent default deadline or automatic assignment.

Proposed lifecycle: **Proposed → ownership accepted → in progress → completion claimed → verified closed**. Cancelled, partially resolved, superseded and reopened are distinct outcomes with history. Ownership acceptance and independent verification apply only where validated authority/policy requires them; no universal separation rule is invented. Unverified external task status cannot skip verification. A local correction is not substituted for a source fact; a hypothetical adjusted view is clearly labelled.

At outcome review, show original decision/forecast, external action evidence if available, eligible later actuals, matched KPI definition/population and remaining uncertainty. If the actuals are missing or comparison is incompatible, feedback is unavailable/qualified. A changed result alone does not prove that the action caused it. Cancellation and local “done” do not inflate verified-action outcomes.

## State Patterns

### GUX-12 — Surface-wide state contract

Applies to S01–S12; specialisations follow. Do not confuse “no records in this selection”, “evidence not connected/captured”, “no permission” and “known zero”.

| State | Required behaviour |
|---|---|
| Initial load / refresh | Keep scope/header and structure stable; show labelled progress. Do not flash zero KPIs. Background refresh does not move keyboard focus or silently switch selected revisions |
| Empty / no selection / no match | Explain which condition applies and offer a relevant permitted next step: choose scope, broaden filters, view last report or record an evidence need. No invitation to connect a source unless that duty is established |
| Missing / partial / disputed | State affected population/interval/inputs and consequence for reliance beside the result; preserve usable independent facts. Do not fabricate missing values or turn disputed evidence into consensus |
| Last-known / stale | Retain its actual cutoff and age, distinguish unavailable current evidence. Freshness threshold is pending OD-09/13; show timestamps without inventing a “current” badge |
| Error | Identify failed task and safe recovery. Retain selection/local input where authorised; show uncertainty if save/export outcome is unknown. A report failure does not erase prior revisions |
| Offline / connection lost | No offline working or queued writes promised. Previously displayed information may remain with its age only while access permits; new results/saves fail visibly. Reconnect, recheck access and retry explicitly |
| Permission denied / revoked | Navigation/search omit inaccessible content. A direct link gives a neutral unavailable message and safe return, without revealing protected identity/counts. Recheck export/save grants; revoke newly accessible cached views. Downloaded files cannot be recalled |
| Focus / open detail | Reading and focus order follow decision → qualification → evidence → action → outcome. Back/close restores selection, scroll and trigger focus where valid |
| New revision / concurrent edit | Keep viewed report/scenario revision identifiable; offer comparison/open latest. A local edit conflict preserves permitted draft and asks the user to reconcile; never silently overwrite another decision |

### Surface specialisations

- **S01 Home:** no validated responsibility/grant → neutral “Your work context has not been confirmed”, with an identified access/responsibility contact if configured; otherwise explain that confirmation is needed through the business's external process. No guessed role dashboard or invented approver. No known issues under incomplete evidence carries a coverage qualification. Switching context preserves no unauthorised content.
- **S02 Report:** preparing, available-qualified, incomplete, failed, superseded and externally validated-final status remain distinct. Goldy's preparation success alone does not establish finality/sign-off. Late evidence produces a linked revision with changed results/reason, never silent replacement. Schedule/deadline and automated notifications remain OD-13.
- **S03 Performance:** unavailable COGS/cost coverage prevents final margin/profit claims. A missing budget prevents budget variance. Incompatible period/population compares with qualification or is unavailable. Sales, receipts, liabilities, purchases, expenses and cash commitments stay separate.
- **S04 Outlook / S10 Opening:** inadequate history or changed/new products → unavailable forecast or explicit scenario; never invented demand. Original issue time/version is retained. Opening assumptions never appear as observed second-pub results.
- **S05 Coverage:** unfilled slot, unknown availability, expired required competency, no roster, missing clock-out, unknown break and unknown cost scope differ. No roster-derived actual end, guaranteed replacement, generic wage rule or private illness detail.
- **S06 Stock:** uncounted item, unknown conversion/cutoff/yield, partial/rejected receipt, uncertain delivery, unmatched transfer and negative balance remain separate. Missing conversion prevents unqualified base-unit/order recommendation. Recount/proposed adjustment is local; variance is not automatically disposal or theft.
- **S07 Service:** booked attendance, actual visits/covers, checks, partial/unobserved fulfilment and observed delay are separate. Missing fulfilment time is not check closure. Zero sales alone is not stockout/lost demand.
- **S08 Evidence:** aggregate-only, unmapped, duplicate, disputed and restricted detail have different explanations. Repeated source evidence does not add activity; two genuine identical facts remain distinct. Original evidence stays visible alongside a permitted proposal.
- **S09 Follow-up:** unassigned, awaiting acceptance, due without agreed policy, completion claimed, unverified/partial closure and reopened are explicit. A missing due rule is not “overdue”; external completion and local status are independently labelled.
- **S11 Utility:** unavailable source/failed collection names the affected business evidence first. Raw/source detail only for the authorised resolver. Unknown control applicability is neither compliance nor breach. Grant change requires its own authority and attributable evidence; no editable controls for unspecified administration functions.
- **S12 Export:** preparing/ready/failed/partial/cancelled states retain selection and report version. Disclose row count/omission/truncation, types and units; prevent restricted detail and spreadsheet text execution. A failed export offers retry without claiming a complete file or sharing it automatically.

### GUX-13 — Warnings and interruptions

Warn at the place where a limitation changes a decision. Use an inline persistent notice for partial evidence, disputed inputs, incompatible comparison, unsupported recommendation or failed save. A technical outage appears on Home only when it affects that person's work; the detail and repair route remain secondary. A generic platform-health badge is not a substitute for a business consequence.

Interrupt only to prevent losing unsaved local work, confirm a consequential permitted local change such as cancellation/verification/grant alteration, or address a validated urgent operational condition. Do not ask confirmation for ordinary viewing, filtering or opening evidence. No invented criticality, urgency, stale-data limit or legal threshold. An urgent instruction needs verified applicability, response owner and procedure; otherwise state the evidence gap.

Warnings include the affected decision, what is known/unknown, scope/as-of, consequence, permitted response and owner/unassigned state. Acknowledge/dismiss changes presentation only; it cannot repair evidence or close the underlying issue. Repeated warnings are consolidated by the business issue, retaining impacts on each affected measure. Notification recipients, channels, schedules, escalation and suppression rules remain unconfigured until agreed; report automation never implies authorisation to email or message others.

## Interaction Primitives

### GUX-14 — Predictable exploration and local action

Click/tap labelled controls; no hover-only actions, mandatory drag, swipe-only commands or gesture-dependent evidence access. Keyboard operation covers all filters, disclosure, comparisons, local saves and export. Optional shortcuts are discoverable and can be disabled; no unmodified single-character shortcut is required. A user can reach decisions through navigation without a command palette or natural-language prompt.

Drill-down preserves scope, period, comparator and revision. Back restores prior context; deep links recheck access. Detail level never silently changes a monetary/physical basis. Filters/groupings and sorting are explicit, persist only as preferences within permitted scope, and are retained in exported selections. Explain why a dimension or comparison cannot be used rather than offering guessed results.

Local records use explicit Save and Cancel. Leaving unsaved work offers save/discard/stay; failed save retains authorised input and a retry path. Do not promise persistence across reload/session expiry or offline operation without an agreed recovery contract. Read-only external links, where available and authorised, are labelled “Open source record”; opening a link claims neither execution nor completion.

### GUX-15 — Permissions versus personalisation

**Permissions** determine which person may view, annotate, review, define reports, export or administer which information, for what purpose/scope and effective interval. They require validated grant evidence (ACC-001–008). No group, title, navigation tab, pin or saved filter establishes a grant. Sensitive aggregate inference and local notes follow the same restrictions as underlying evidence.

**Personalisation** selects an authorised home alternative, pins a relevant destination, reorders permitted modules, sets compatible default periods or remembers display preferences. It cannot expand access, change metric meaning/targets, determine external authority, conceal mandatory reliance warnings, approve report finality or alter other users' defaults. Selecting a group is not impersonation or privilege switching.

Example: a person with FOH roster and BOH ordering duties can use both contexts, with Staffing visible only for FOH. Pinning BOH Staffing grants nothing. Aggregate labour planning does not require individual wage/medical detail. Revocation takes effect for new views/exports/local writes and invalidates retained unauthorised selections; historical authorship is preserved.

## Accessibility Floor

### GUX-16 — Usable interpretation and operation

These are proposed behavioural requirements supporting NFR-006/007, not a claim of certified conformance. Formal standard/version, supported assistive technology and representative user needs remain OD-12; no external standard is adopted or legal obligation inferred in this specification.

- Complete each core journey using keyboard alone, with visible focus, a skip-to-content route, semantic headings/landmarks and no keyboard traps. Modal focus is contained appropriately and restored on close; page transitions announce the new context/title.
- Give every control an accessible name, role and state. Forms have persistent labels, associated error messages and a reachable error summary. Never rely on a placeholder, colour, chart position or hover tooltip for a critical meaning.
- Expose results as labelled numbers/text and structured tables or equivalent lists. Charts, if later used, have text summaries and equivalent data access. Announce units, estimate/actual status, scope, missing state and comparison direction.
- Support text resizing/zoom and narrow reflow without hiding decision qualifications/actions. Dense evidence tables may scroll inside a labelled region, with keyboard access and persistent row/column meaning; the main decision content must reflow.
- Touch targets and spacing must support deliberate selection under validated phone-use conditions; numerical target sizing remains a design/accessibility validation item. No tiny icon-only critical action or precision gesture.
- Announce save/error/export status without repeatedly interrupting the reader during background refresh. Do not auto-dismiss critical errors or move focus when new results arrive. Avoid motion; respect reduced-motion preferences if motion is later added.
- Exported workbooks have meaningful sheet/column names and reading order; statuses/caveats appear in text and survive printing or removal of formatting. CSV values preserve missing states and units through the agreed companion metadata/header convention.
- Give warning meaning through labels and structure as well as any future visual treatment. Visual contrast/font choices remain pending DESIGN.md decisions; no blanket accessibility claim from a component library.

## Responsive & Platform

### GUX-17 — Website reporting and phone quick checks

The same qualified facts, permission rules and local-action boundaries apply at every width. The user agreed responsive web; supported browsers, devices, network conditions and quantitative usability bounds remain open.

On larger screens, keep navigation, current scope and the decision summary readily visible; supporting evidence can sit beside the main question when space permits. Full report comparison, financial explanation and export remain website tasks. At narrower widths use a labelled navigation disclosure, one reading column and stacked comparisons; show the decision and its qualification before supplementary detail. Breakpoints follow content fit during later design validation, not an assumed device catalogue.

Phone quick checks cover finding a relevant question, reading the qualified result, opening explanation, recording a permitted local note/follow-up and seeing outcome status. Do not strip units, coverage, freshness or scope to fit a card. Access to larger reporting tasks remains available, with readable detail or an explicit continuation path; no silent mobile-only data reduction. Preserve selection and unsaved state when rotating/resizing. No native app, push notification, camera workflow or offline queue is assumed.

## Inspiration & Anti-patterns

The reference is the user's operational chain, not the current application or a competitor. No implementation was inspected and no visual reference was supplied.

Reject an ingestion-first home, a menu of every canonical entity, a universal KPI wall, a generic “Insights” feed without a decision, separate Exports navigation, unexplained confidence scores, universal performance targets, staff league tables inferred from variance, and simulated transactional controls. Consolidate contextual evidence, forecasts and follow-up around the active business question. Keep specialist work available for its validated owner without requiring everyone to navigate through it.

## Key Flows

PRD names and UJ identifiers are preserved verbatim. Alex, Casey, Morgan, Taylor, Jamie, Sam and Riley are illustrative protagonists from the PRD, not verified employees or observed sessions. Device details below apply the agreed web/phone direction; precise work timing, duties and outcome owners remain candidates.

### UJ-01 — Alex prepares the weekly report

1. Alex, representing the GM, opens Upper management on the website and explicitly chooses Weekly review; this does not settle the home-default decision (S01/S02).
2. Alex selects the agreed week/scope, sees report revision, as-of and preparation/coverage status, and reviews qualified findings.
3. Alex opens a material change in S03/S08, checks comparable populations and any missing contribution, then returns without losing scope.
4. Alex records a local question/follow-up in S09 where permitted and chooses Export (S12); the review restates scope, revision, caveats, format and omitted detail.
5. **Climax:** Alex obtains a readable, qualified CSV or Excel report and can explain whether it is usable and what remains unresolved; no platform-to-spreadsheet reconstruction is required for this agreed report.
6. Alex later reviews corrections, comparable preparation/checking effort and verified follow-up, using MET-015/K11–K12 after validation.

Failure: late/missing evidence leaves the report incomplete; last-known values retain their own dates. Export preserves incompleteness; unauthorised export is unavailable. A reissue links to the retained original. Trace: FR-001–006,049–054; OD-01,03,08–10,12–14.

#### UJ-01a — Alex checks the report definition

1. From S02 Alex opens the report's definition in S08 to inspect purpose, week boundaries, selected measures, comparators, contributing evidence, intended readers and sign-off rules.
2. If Alex has a separate report-definition grant, S11 provides scoped local definition review/change through the Local record pattern; otherwise the definition stays read-only and Alex records a request in S09.
3. **Climax:** any permitted change has its own version, attribution and affected-report scope, without overwriting prior results, granting a recipient access or authorising automated distribution.

Failure: unresolved report policy remains explicit; view/export permission alone does not permit definition changes. Trace: FR-002; DR-048; ACC-001,007–008; OD-02–03,09–10,13–14. This is a supporting branch, not another navigation destination.

### UJ-02 — Alex examines a revenue or product-sales forecast

1. Alex opens Outlook (S04), selects revenue or a specific product, horizon and permitted scope.
2. Alex checks original issue time, historical coverage, sale units versus money, price/availability assumptions and uncertainty.
3. Alex compares alternatives locally, sees changed assumptions without overwriting the issued forecast, and opens evidence where needed (S08).
4. **Climax:** Alex can state whether to rely, qualify or defer, and records a scenario/decision or follow-up (S09) for discussion and external action.
5. Later Alex compares the original forecast with eligible actuals under the same metric/population, with K10/MET-013 feedback.

Failure: insufficient history/new product yields unavailable forecast or labelled scenario; zero sales during known unavailability are not zero demand. No invented confidence or causal weather claim. Trace: FR-041–044,054; OD-01,03–05,09,12–14.

### UJ-03 — Casey and Morgan review service staffing

1. Casey uses FOH; Morgan, the head-chef representative, uses BOH. Each opens their authorised Coverage review from Home/Staffing (S05).
2. Each compares expected demand and product/preparation effort with skills, availability and the named roster version; missing workload/cost evidence stays visible.
3. Each inspects a gap, saves a local coverage scenario/follow-up (S09) and takes any roster amendment/publication through the external authorised process.
4. **Climax:** each can explain the coverage choice, unresolved slot and evidence limitation without assuming demand equals workload or a proposal equals a published roster.
5. Each returns to matched plan, observed work and service outcomes, with scoped K03/K08/K10/K12 feedback.

Failure: sickness leaves unfilled coverage without private medical detail; missing clock-out leaves actual duration unknown. No sub-head-chef rostering inferred. Trace: FR-031–036; OD-02–04,07,09,12,14.

### UJ-04 — Casey and Morgan assess stock through the next delivery

1. Casey opens FOH Stock & supply; Morgan or the responsible sub-head chef opens BOH Stock & supply (S06).
2. They establish item/location, usable-stock/count cutoff, next-delivery horizon, outstanding accepted supply and uncertainty.
3. They inspect demand, effective recipes/modifiers/yields, supplier packs/conversions and quantity alternatives (S04/S08 as needed).
4. They save a qualified local scenario/follow-up (S09), then order through the existing external authorised process.
5. **Climax:** the responsible person can explain a proposed quantity and what could change it, without Goldy's placing an order or implying approval.
6. They later inspect accepted/rejected/partial delivery, availability, waste and original-forecast outcomes (K05–K06/K10–K11/MET-014).

Failure: missing count, conversion, yield or credible delivery timing prevents an unqualified recommendation. Invoice/payment alone is not usable stock. Trace: FR-017–030; OD-01–03,05–06,09,12,14.

### UJ-05 — Taylor reviews a service or guest-demand exception

1. Taylor, representing the yet-to-be-confirmed service coordinator, uses the appropriate FOH/BOH context on a phone and opens the relevant service/preparation question (S07).
2. Taylor compares intended bookings, observed attendance/covers and service/availability evidence, with the applicable scope and as-of visible.
3. Taylor opens the relevant evidence (S08), discusses a response externally and records permitted follow-up (S09).
4. **Climax:** Taylor can distinguish an observed delay/availability issue from a missing observation and identify the responsible follow-up rather than inferring cause from check counts.
5. Taylor later reviews observed service duration/availability and verified resolution (K03/K12/MET-014).

Failure: missing arrival/fulfilment stays unknown; six booked, four actual covers and two checks remain different. Review sentiment is optional context, not attendance or duration evidence. Trace: FR-007–011,046,048–050; OD-01–03,09–10,12,14.

### UJ-06 — Alex and Jamie explain a weekly financial difference

1. Alex opens a qualified financial difference from a report/overview in S03; Jamie joins only within explicitly authorised supporting access.
2. They separate sales/adjustments, liabilities, payments/allocations, settlements, supplier obligations, inventory costs and labour bases.
3. They use S08 to examine permitted evidence, unmatched components, effective periods and aggregate/detail coverage; customer/worker details remain scoped.
4. They retain a local explanation/proposed correction/follow-up (S09), seek external correction and use S12 for a permitted evidence export if needed.
5. **Climax:** they can explain whether the difference reflects timing, classification, actual performance or missing evidence, without a forced balancing entry or false final-profit label.
6. On new evidence they compare a linked report revision and remaining K11 exceptions; K07–K09/MET-016 outcomes appear only where cost scopes support them.

Failure: missing valuation/expenses, unmatched settlement or unknown labour terms qualify results. A gross settlement of 100 less fee 3 and bank receipt 97 does not reduce product sales by 3. Trace: FR-012–016,021–022,037–040,054; OD-02–03,07–10,12–14.

### UJ-07 — Sam compares opening scenarios

1. Sam, representing the owner, opens secondary Opening planning in Outlook or an explicitly chosen personal pin (S10).
2. Sam selects a decision area and current-pub evidence, then states new-site differences in location, customers, capacity, operating model, resources and costs.
3. Sam compares local scenario versions and sensitivity, opening evidence for transfer assumptions (S08).
4. **Climax:** Sam can explain which assumption supports the option and what remains untested, saving a local decision/evidence need (S09) without presenting the proposed pub as live operations.
5. Sam revisits assumption tests and evidence coverage; only later eligible observations support operating-outcome comparisons. No pre-opening operating KPI improvement is invented.

Failure: insufficient new-site evidence or missing costs prevents an unqualified forecast/profit conclusion. Trace: FR-043–045; CON-005; OD-01,08–12,14.

### UJ-08 — Riley investigates an evidence gap and follows it up

1. Riley, an authorised contributor/steward in one of the three contexts, opens an affected business result/issue (S08/S09), not a general raw-data feed.
2. Riley checks original facts, status, definition and affected decision, preserving unknown/disputed/aggregate-only distinctions.
3. Riley saves a permitted local observation/proposed correction with reason and records proposed or accepted ownership in S09; unresolved ownership remains explicit.
4. External evidence/correction is sought through the authorised process; local completion claims remain unverified until the required evidence and authority exist.
5. **Climax:** the issue becomes verified closed, partially resolved or explicitly unresolved with history; the source fact is never silently replaced by the proposal.
6. Riley returns to the affected measure/revision and K11/K12 feedback; reopening preserves prior closure evidence.

Failure: missing verifier authority or unsupported external completion prevents verified closure; failed saves retain permitted draft input. Trace: FR-047–050,053; ACC-001–008; OD-02,09–10,12,14.

#### UJ-08a — Riley handles specialist evidence work

1. Riley enters S11 through a contextual affected-evidence link or secondary utility, only with an effective stewardship/administrative grant.
2. Riley sees affected business questions and scoped unresolved evidence first, then permitted source/definition/lineage details.
3. Riley records a local proposal/follow-up and seeks source repair externally. A grant/definition change is available only as a separately authorised local function with attribution; unspecified system settings are not designed here.
4. **Climax:** the limitation and its business consequence are explained with accountable next steps; Riley returns to S08/S09 and verifies any later evidence change.

Failure: title-only access, revoked grants or unresolved source permission exposes no protected raw detail or editable administration. This is a supporting branch of UJ-08, not a new source requirement. Trace: FR-006,048–050; ACC-001–008; SEM-001–010; OD-02,09–10,14.

## Verification and Open Decisions

### GUX-18 — Acceptance of the experience

Run the following with representative role holders and permitted sample evidence once stakeholder validation is authorised. Measure completion, time, errors, assistance and ability to explain meaning; pass thresholds remain OD-12/13. This specification does not claim user testing has occurred.

| Check | Required observable result | Source trace |
|---|---|---|
| UX-AC01 — three contexts | FOH, BOH and Upper management organise work; head chef/sub-head chef/staff duties are not flattened into the same navigation or grant | GUX-02–05,15; ACC-001–006; OD-02 |
| UX-AC02 — unranked home choice | Upper management can choose report-led or overview-led home with no organisational default; personal preference changes neither rights nor KPI policy | GUX-06,15; OD-01,12 |
| UX-AC03 — full decision chain | For each surfaced decision, a representative user finds responsibility, question, qualified evidence, permitted response and matched feedback or explicit feedback gap | GUX-01–04,10–11; FR-005–006,048–050 |
| UX-AC04 — report and export | UJ-01 produces matching authorised scope/revision, readable numbers/labels, qualifications and missing states in CSV/Excel; missing source and failed export are distinguishable | GUX-10,12; FR-001–006,051–054; NFR-012 |
| UX-AC05 — semantics at decision | Explain the booked/actual/check example, stock count eight/book ten, money versus sales, planned/actual/payable work, and cost/profit coverage without entering diagnostics | GUX-03–04,10,12; SEM-001–010; MET-001–016 as selected |
| UX-AC06 — forecast/outcome | Distinguish revenue from product units, scenario from actual/issued forecast and incomplete actuals from forecast accuracy | GUX-10–12; FR-041–045; MET-010,013 |
| UX-AC07 — local versus external action | Saving proposed coverage, order, correction or completion changes no connected fact and claims no external action; verified closure has applicable evidence | GUX-11,14; CON-001,003; FR-049–050 |
| UX-AC08 — privacy and personalisation | A denied worker/source record cannot be retrieved through context switching, pins, totals, search, notes, drill-down or export; revocation invalidates new use | GUX-05,12,15; ACC-001–008 |
| UX-AC09 — responsive and accessible use | Complete the selected website reporting and phone quick-check tasks through keyboard/touch/assistive conditions; scope, caveats, focus and failed-save recovery remain understandable | GUX-10,12–17; NFR-006–007,010–012 |
| UX-AC10 — navigation value | Every primary destination has a validated recurring responsibility/journey, every surface has a flow entry and no technical utility displaces business work merely because it exists | GUX-01,05,09; CON-006; OD-01–02,12 |

### Remaining validation, without inventing policy

- **OD-01/02:** actual priorities, duty boundaries, KPI owners and permission/delegation evidence; preserve two upper-management alternatives. Candidate navigation/home modules cannot become agreed business priorities by being drawn here.
- **OD-03–09:** operating periods, workload/skills, usable supply, accounting/employment meaning, metric contracts/comparability, targets and evidence eligibility. These gate reliance and warning thresholds, not retention of domain coverage.
- **OD-10/14:** purpose-bound sensitive/source access, applicable controls, local proposal/ownership/verification authority, escalation and retention. No current legal applicability or policy was established here.
- **OD-11:** opening assumptions, transferability and any later second-venue operating scope.
- **OD-12/13:** representative sessions, exact devices/browsers and accessibility needs; report contents, deadline/sign-off, forecast horizons/products, export recipient tools, notification/distribution authority, quantitative performance/recovery/task criteria. Responsive web and specification-only composition are agreed, not an approved usability baseline.

All S01–S12 surfaces are **spine-only**, explicitly accepted by the user; no visual mockups, application code, architecture, implementation plan or later workflow is part of this deliverable. Coverage and traceability results, plus four-source reconciliation, are recorded in [validation-coverage-traceability.md](validation-coverage-traceability.md).
