---
type: domain
topic: Australian pub operational information model
decision: Establish independent business semantics and evidence for the SRS
source: Native web research; supporting digests in digests/
status: complete
created: 2026-10-14
updated: 2026-10-14
project_context: PROJECT_CHARTER.md; scope only, not evidence
implementation_inspected: false
verified_load_bearing_claims: 4
unverified_load_bearing_claims: 7
---

# Australian pub operations: domain research for the SRS

## Executive finding

Model the venue through distinct business facts: what customers ordered and received, what money moved, what stock arrived or was consumed, and what staff planned, worked and were paid. Government record guidance and hospitality competencies support these distinctions; a single daily sales or cost total cannot explain their differences. [1][2][3][4]

The strongest accounting distinction is between ingredient estimates, inventory valuation, period expenses and cash payments. The strongest compliance distinction is applicability: jurisdiction, employer coverage, legal structure and food-business activities determine obligations. Neither a single site nor the presence of a kitchen resolves these questions. [5][6][7][8][9]

**Evidence key:** **Practice** = documented government/hospitality practice; **Obligation** = authoritative rule, conditional on applicability; **Convention** = optional operational definition; **Proposal** = research-derived SRS recommendation, not an industry-mandated schema; **Decision** = requires venue/accountant confirmation. High confidence means corroborated across publishers; medium means one authoritative publisher or documented convention without independent verification. No numerical performance targets are established.

Scope is one Australian pub with bar and kitchen service. Only PROJECT_CHARTER.md was used as project context. No Goldy's implementation, existing software schemas or PRD were inspected. Publication dates and retrieval limitations appear in the source register; all sources were accessed 14 October 2026. This is a domain report, not an exhaustive compliance assessment.

## SRS implications and unresolved decisions

**Cross-domain finding:** revenue, consumption, cash and labour recognition occur on different timelines. Preserve their facts and attribution rules so reconciliation can explain differences. **Recommendation:** use the proposed entity/event distinctions for SRS discovery; source support is strongest for separate records and workflow stages, while exact fields/cardinalities remain proposals.

Before adopting requirements, confirm: (1) state/council, licence, legal entity, GST/accounting basis and employment coverage; (2) trading cutoff/service periods and covers; (3) sale/refund/comp/voucher/deposit policies; (4) units, yields, recipe versions, stock/count and cost scopes; (5) actual responsibilities and approvals; (6) KPI definitions, targets, and missing-data behaviour. Venue interviews, actual business forms and accountant/regulator applicability checks resolve these decisions. No research finding establishes Goldy's current practices.

## 1. Atomic events and source facts

**Practice:** hospitality stock receipt involves checking actual goods against orders, inspecting condition and recording discrepancies. Roster competencies distinguish planned staffing from completed shifts. Business records distinguish sales, expenditure, bank and stock records. [1][2][3]

**Proposal — minimum event families:**

- **Sales/service:** check opened; line ordered/amended; modifier selected; price/discount applied; preparation or fulfilment recorded; line cancelled/voided; check closed; return/refund authorised and completed. Separate a cancellation from a financial reversal and from physical disposal.
- **Money:** tender received/allocated; refund paid; deposit or voucher issued/redeemed; cash float/movement/count; processor settlement and fee; supplier payment. A customer payment and a bank deposit can occur on different dates.
- **Purchasing/stock:** order submitted/approved; goods received/accepted/rejected; supplier invoice/credit received; stock transferred; preparation batch produced; stock consumed/wasted; count observed; discrepancy investigated/adjusted.
- **Labour:** roster published/changed; employee starts/ends work or break; timesheet corrected/acknowledged/approved; pay calculated/paid; super submitted/received; leave or competency changed.
- **Demand/control:** reservation made/amended/cancelled; party arrives/no-shows; actual covers recorded; safety check and corrective action; licence/qualification evidence renewed.

For each event propose an identity, business object/line reference, occurrence time with local offset, recorded time, actor, quantity/unit or currency/amount, status, reason, authorisation where applicable and source-document reference. Preserve original facts and correction links; do not silently overwrite finalised evidence. Distinguish zero, unknown, missing and not applicable. These fields are design proposals; Fair Work specifically restricts employment-record alterations to error correction. [7]

## 2. Canonical entities and relationships

**Proposal**, grounded in the workflows above rather than a vendor data model:

- **Venue → operational areas/stock locations.** Bar, kitchen, cellar and store are locations or responsibility areas inside one venue; they need not be separate businesses.
- **Product → menu offering/price version → sale line.** A saleable pint, dish or modifier is distinct from a purchased keg, ingredient or preparation batch. Preserve historical descriptions, prices and tax amounts. A sale line may have several modifiers and adjustment allocations.
- **Check/order → lines; payment ↔ checks through allocations.** Accommodate split tenders, partial payments, tabs and reversals. A refund references the original financial facts where known; it does not automatically restock food or drink.
- **Recipe version → ingredient quantities/yields → saleable output.** Ingredient and output units require explicit conversions. A supplier item/pack maps to a stock item; one supplier item is not necessarily one menu product. Ingredient/allergen information and substitution effects need versioned evidence and agreed service procedures.
- **Supplier → purchase order/receipt/invoice/credit; payment ↔ invoices through allocations.** Partial deliveries and consolidated invoices require relationships beyond one order = one receipt = one invoice.
- **Stock item + location → movements/count observations; optional lot/batch.** A count is an observation at a cutoff; an adjustment is the explained change to the book balance.
- **Employee → employment/classification history → roster/work intervals → payroll results.** Position, task assignment, responsibility and access permission are distinct. Employees may work across bar and kitchen allocations.
- **Reservation/party → actual visit → covers/checks/table assignments.** Booked party size is not actual attendance; checks and covers are not interchangeable. Customer identity may be unnecessary for ordinary walk-in service.
- **Trading day/service-period instance → attributable facts; budget/metric definition → comparable period results.** Keep accounting/pay periods separately.

Receipt inspection, recipe costing and shift-record competencies support these concepts, but do not prescribe these cardinalities. [2][3][4] Three-way purchase matching, mandatory batch tracking and separation of approvals are optional controls to agree with the venue.

## 3. Sales, purchasing, labour and inventory meanings

**Sales conventions:** define gross item sales before reductions, net sales after agreed discounts and revenue refunds, and amounts collected separately. Square's documented definitions exclude tax, tips and gift-card issuance from product sales; Lightspeed also distinguishes takings, open accounts and partial payments. These are examples of differing conventions, not canonical Australian rules. [10][11]

**Decision:** define item versus check discounts, complimentary service, pre-fulfilment voids, prepared-but-cancelled items, amount-only refunds, surcharge/tip treatment and recognition timestamps. Zero-price consumption still affects stock. Voucher issuance, redemption and customer deposits need separate liability/receipt facts and accountant-confirmed revenue/GST treatment; do not count issuance and redemption twice.

**Purchasing practice:** ordered, physically accepted, invoiced and paid quantities/values answer different questions. Capture substitutions, damaged/rejected goods and supplier credits. Receipt evidence does not replace invoice evidence for GST. [2][12]

**Labour practice/obligation:** rostered hours forecast coverage; work intervals show attendance; payable hours and payroll results reflect the applicable employment instrument. Breaks, minimum engagements, penalties, overtime, leave and annualised arrangements prevent a universal hours × base-rate calculation. Award coverage depends on the employer and actual duties; a pub kitchen does not automatically establish Restaurant Award coverage. [3][6][7][13]

**Inventory convention:** reconcile opening quantity + accepted receipts + transfers in + production − transfers out − consumption − recorded waste ± adjustments to book closing quantity. Counts establish observed stock. Internal transfers net to zero venue-wide. Theoretical consumption uses sold quantities, effective recipe/modifier quantities and yields; physical consumption comes from counts and movements. Their difference is a variance to investigate, not proof of theft or waste. Government guidance supports comparing stocktakes with records and setting reorder points using demand and lead times. [14]

## 4. Accounting and cost glossary

**Obligation, conditional:** AASB 102 measures inventory at the lower of cost and net realisable value; cost includes relevant purchase/conversion costs, excludes recoverable taxes and reflects discounts/rebates. It distinguishes abnormal waste expense and cost assignment using FIFO or weighted average for interchangeable stock. Physical rotation and accounting cost flow are different concepts. Confirm whether AASB reporting requirements apply to the entity. **Medium; single publisher.** [5]

**Practice/conventions:** Business Victoria gives opening stock + purchases − closing stock as a usual periodic COGS formula. Adapt it to agreed scope and separately classified movements/losses; do not subtract waste twice. [15]

- **Net revenue:** eligible sales consideration after agreed revenue reductions, excluding collected GST; define other inclusions explicitly.
- **Recipe ingredient cost:** ingredient cost for a batch divided by usable portions; a theoretical estimate dependent on recipe, yields and price basis. Hospitality competencies explicitly cover portions and raw-ingredient costs. [4]
- **COGS:** carrying cost attributable to goods sold in the period; periodic depletion may include losses unless separately classified. Purchases and supplier cash payments are not synonyms.
- **Gross profit / margin:** net revenue − COGS; margin percentage = gross profit ÷ net revenue × 100. Markup uses cost as denominator and is different. These formulas follow the profit definitions in [15].
- **Labour cost:** distinguish wages only from a defined loaded amount including relevant super, leave accruals and other on-costs. Record allocation rules; avoid counting leave twice through accrual and payment.
- **Prime cost:** optional management subtotal of agreed COGS + agreed labour cost. Define included costs before using it; no uniform Australian pub definition was established.
- **Operating/net profit and cash flow:** use an accountant-approved account scope; gross profit is before other expenses. Bank receipts/payments are cash movement, not an interchangeable profit measure. [1][15]

GST-inclusive and exclusive values must coexist. For a wholly taxable amount at the standard rate, included GST = amount ÷ 11; do not apply this to an entire mixed-tax transaction. Tax classification and adjustments require source tax facts, not a blanket food flag. **High for formula; food classification remains medium/search-extract evidence.** [16][17]

## 5. Service period and trading day

**Practice:** hospitality training recognises preparation, active service and end-of-service tasks. It does not establish universal lunch/dinner times. [18]

**Convention/Decision:** a trading day is a venue-defined reporting interval, potentially crossing midnight. Square distinguishes a reporting-day start from its payment-transfer cutoff: a bank-settlement day is not necessarily the operational day. [19]

Propose explicit local timezone, cutoff, start/end timestamps and effective dates; service periods may differ between bar and kitchen. For a hypothetical 04:00 cutoff, a sale at 01:00 Tuesday belongs to Monday's operational day while retaining its actual Tuesday timestamp. Agree attribution by order, fulfilment, closure or adjustment time; treatment of open tabs, late corrections, overnight labour and daylight-saving transitions. Do not change payroll calendar/public-holiday dates to match a management reporting cutoff. Reservations and booked covers remain distinct from actual service demand.

## 6. Responsibilities and operating decisions

Budget and roster competencies describe functions and decisions, not a universal job-title hierarchy. [3][20] The following is a **proposed interview map**, to validate rather than install as permissions:

- **Owner:** financial sustainability → funding/pricing/budget decisions → reconciled profit, cash commitments and trends → revise budget/approve expenditure → margin and cash availability.
- **General/venue manager:** whole-service performance → staffing, supplier and exception decisions → demand, hours, stock and unresolved variances → adjust operations/approve corrections → productivity and budget variance.
- **Kitchen manager:** food availability/yield/safety → preparation and ordering decisions → recipes, counts, bookings, receipt quality → change preparation/order quantities → food cost, waste and control exceptions.
- **FOH/bar manager:** service capacity and beverage control → floor/bar staffing and availability decisions → actual covers, queues, stock and work coverage → reassign tasks/replenish → service time and sales per labour hour.
- **Operational staff:** accurate fulfilment and evidence → serve, receive, record and escalate → item/recipe/task instructions and relevant competencies → complete tasks/report exceptions → fulfilment and record completeness.
- **Payroll/bookkeeping function:** lawful pay and financial reconciliation → resolve discrepancies → approved time, instruments, invoices and settlement evidence → reconcile/release authorised payments → correction backlog and overdue obligations.

One person may hold several responsibilities. Confirm who proposes, performs, verifies and approves each action, along with limits and delegation. Payroll confidentiality and food-safety/RSA competency cannot be inferred from a manager title. [7][8][21]

## 7. KPIs and required source facts

These are **candidate conventions**, not benchmark targets. All require a versioned definition, eligible population, period, tax/cost basis and completeness indicator. Undefined denominators produce an unavailable result, not zero. Sales averages and stock comparison have documented precedent; formulas below are proposed exact definitions. [11][14][15]

- **Net sales / sales mix:** eligible line quantities, historic amounts/tax, discounts and refunds; category mappings effective at sale time. Reconcile to financial totals.
- **Average spend per cover:** eligible net sales ÷ actual covers; observed covers and food/bar inclusion rules. **Average check** instead divides by eligible check count; it cannot substitute for spend per person.
- **Food/beverage cost % and gross margin %:** category COGS ÷ matching category net sales; opening/closing valuations, purchases/credits and classified losses. Estimated recipe cost must be labelled theoretical.
- **Labour % / sales per labour hour:** agreed labour cost ÷ matching net sales; net sales ÷ agreed worked or paid hours. Need actual intervals/breaks, pay results/on-costs and department allocation. Preserve forecast versus actual versus final payroll status.
- **Usage/count variance and waste:** physical minus theoretical usage, or observed minus book stock, with sign stated; count cutoffs, all movements, recipes/yields/units and valuation basis. Recorded waste requires quantity, reason and cost; unexplained variance stays separate.
- **Budget variance:** actual − budget, with favourable/unfavourable meaning by measure; approved budget versions, commitments, comparable periods and matching definitions.
- **Service duration / reservation no-show rate:** fulfilment time − order time; no-show reservations ÷ eligible expected reservations. Need actual timestamps/statuses and cancellation/eligibility rules; sales closure time is not proof of serving time.
- **Settlement/purchase exceptions:** expected versus received settlement and fees; accepted versus invoiced quantities/prices; allocations, credits and timing. Show unresolved items rather than folding them into revenue.

No reliable universal target for food cost, labour cost, pour loss, table turns or stock variance was established. Targets belong to venue budgets and service strategy.

## 8. Australian compliance and recordkeeping

- **Financial records:** most business records have a five-year retention rule with exceptions; company financial records have at least seven years. Business structure and record class determine retention, not one global deletion period. **High for company minimum; general tax rule medium in this run.** [1][22]
- **Employment:** Fair Work guidance requires time/wage records for seven years, confidentiality, readable English and access; legally required hour details vary by circumstance. General all-staff actual-hours capture is useful practice, not represented here as an identical statutory requirement for every worker. Applicable award agreements and corrections need retained evidence. **Medium; one regulator publisher.** [6][7][13]
- **Super:** ATO and FWO describe Payday Super commencing 1 July 2026, generally requiring fund receipt within seven business days of payday, with exceptions. Preserve payday, applicable earnings/rules, due date, payment and receipt/failure evidence; avoid an obsolete quarterly default. **High, corroborated.** [23][24]
- **Food safety:** Standard 3.2.2A tools depend on business category/activity. Category-one substantiation can use records or other demonstration; relevant records have at least three months' retention. Supplier/food identity supports traceability; a formal written recall system is not automatically required of every retail pub. Confirm local registration, classification, supervisor/training and program duties. **High for conditional evidence rule; traceability medium.** [9][25][26]
- **Liquor:** NSW guidance supports jurisdiction-specific RSA training and competency evidence; it is an example, not a national register policy. **Medium.** [21] **Decision:** establish the actual licence, permitted trading conditions and any incident/refusal-record obligations from the relevant regulator and licence. These obligations were not verified by the RSA source. Propose capturing jurisdiction/licence and task competency validity.
- **Privacy:** single-site operation does not prove a small-business exemption; turnover and exceptions matter. Employee-record exemptions are limited and do not automatically cover applicants, customers or third-party payroll handling. Confirm coverage and purpose-based access/retention. **Medium; OAIC guidance.** [8][27]

Specific wage rates, state payroll-tax thresholds, liquor-register retention, food-control limits, allergen-information obligations, voucher tax rules, TFN handling and licence conditions were not sufficiently established for testable legal requirements here. Resolve only those applicable to this venue before the SRS fixes their contracts.

## Sources and verification

All accessed **2026-10-14**. “Not stated” means no publication/update date was established from the retrieved material. Generated training-document dates are not claims of a new industry practice. H = cross-publisher corroborated; M = single publisher/convention or limited retrieval. This report and ledger use that scale; acquisition digests retain their original researcher confidence labels and retrieval limitations. A ledger status of unverified means no independent corroboration in this run, not a finding that the authority is wrong. Sources [10], [11] and [19] illustrate conventions only; no competitor feature catalogue was produced.

| Ref | Evidence / publisher and source | Publication/update | Confidence |
| --- | --- | --- | --- |
| [1] | Australian Government — [Record keeping](https://business.gov.au/finance/payments-and-invoicing/record-keeping) | Not stated | M; company rule H with [22] |
| [2] | National Training Register — [SITXINV006 Receive, store and maintain stock](https://training.gov.au/assets/SIT/SITXINV006_R1.pdf) | Generated 2024-11-22 | M |
| [3] | National Training Register — [SITXHRM008 Roster staff](https://training.gov.au/TrainingComponentFiles/SIT/SITXHRM008_R1.pdf) | Generated 2023-03-02 | M |
| [4] | National Training Register — [SITHKOP010 Plan and cost recipes](https://training.gov.au/assets/SIT/SITHKOP010_R1.pdf) | Generated 2023-03-02 | M |
| [5] | AASB — [AASB 102 Inventories](https://standards.aasb.gov.au/aasb-102-dec-2021), paras 9–16, 25, 34 | 2021-12-20; operative version selected for pre-2027 periods | M; applicability conditional |
| [6] | FWO — [Hospitality Award summary](https://www.fairwork.gov.au/employment-conditions/awards/awards-summary/ma000009-summary) | Not stated | M |
| [7] | FWO — [Record-keeping](https://www.fairwork.gov.au/pay-and-wages/paying-wages/record-keeping) | 2026-08-28 | M |
| [8] | OAIC — [Employee records exemption](https://www.oaic.gov.au/privacy/privacy-guidance-for-organisations-and-government-agencies/organisations/employee-records-exemption) | 2015-12; regulator page retrieved | M |
| [9] | FSANZ — [Evidence tool](https://www.foodstandards.gov.au/business/food-safety/evidence-tool) | 2025-10-01 | H with [25] |
| [10] | Square AU — [Sales and payment reports](https://squareup.com/help/au/en/article/5381-in-app-summaries-and-reports) | Not stated | M; vendor convention |
| [11] | Lightspeed — [POS report structure](https://k-series-support.lightspeedhq.com/hc/en-us/articles/4412206559771-POS-report-structure) | Not stated | M; international convention |
| [12] | ATO — [GSTR 2013/1 tax invoices](https://www.ato.gov.au/law/view/document?LocID=%22GST%2FGSTR20131%2FNAT%2FATO%2Ffp39%22&PiT=20230412000001) | 2013; retrieved 2023-04-12 point-in-time | M; current threshold detail excluded |
| [13] | FWC/FWO — [Hospitality Industry (General) Award 2020](https://awards.fairwork.gov.au/MA000009.html) | Amendments through 2026-07-01 | M; coverage conditional |
| [14] | Australian Government — [Manage your inventory](https://business.gov.au/products-and-services/inventory-management/manage-your-inventory) | Not stated | M |
| [15] | Business Victoria — [Calculate profit and loss](https://business.vic.gov.au/business-information/finance/develop-good-financial-procedures/calculate-profit-and-loss) | Not stated | M; general guidance |
| [16] | ASIC Moneysmart — [GST calculator](https://moneysmart.gov.au/work-and-tax/gst-calculator) | 2026-06-18 | H; ATO s9-75 extract corroborated in digest |
| [17] | ATO — [Food and beverage GST classifications](https://www.ato.gov.au/law/view/document?PiT=99991231235958&locid=%27GII%2FGSTIIFL1%2FNAT%2FATO%27) | Not stated | M; search extract, full page blocked |
| [18] | National Training Register — [SITHFAB034 Table service](https://training.gov.au/Training/Details/SITHFAB034?tableQualifications-orderBy=Title-asc) | Not stated | M |
| [19] | Square AU — [Customise close of day](https://squareup.com/help/au/en/article/5439-customize-your-close-of-day) | Not stated | M; optional convention |
| [20] | National Training Register — [SITXFIN010 Prepare and monitor budgets](https://training.gov.au/TrainingComponentFiles/SIT/SITXFIN010_R1.pdf) | Generated 2023-03-02 | M |
| [21] | Liquor & Gaming NSW — [RSA training](https://www.nsw.gov.au/business-and-economy/liquor-and-gaming/training/rsa) | Not stated | M; NSW only |
| [22] | ASIC — [Company record keeping](https://www.asic.gov.au/for-business-and-companies/companies/company-building-blocks/company-record-keeping) | Not stated | H with [1] |
| [23] | ATO — [Payday Super](https://softwaredevelopers.ato.gov.au/PaydaySuper) | 2026-03-18 | H with [24] |
| [24] | FWO — [Payday Super rules](https://www.fairwork.gov.au/newsroom/news/payday-super-new-rules-starting-1-july-2026) | Published 2025-12-10; updated 2026-02-03 | H with [23] |
| [25] | NSW Food Authority — [Showing food is safe](https://www.foodauthority.nsw.gov.au/retail/standard-322a-food-safety-management-tools/showing-food-safe) | Not stated | H with [9]; local application conditional |
| [26] | FSANZ — [Food traceability](https://www.foodstandards.gov.au/business/food-safety/food-traceability) | 2025-09-30 | M |
| [27] | OAIC — [Small business](https://www.oaic.gov.au/privacy/privacy-guidance-for-organisations-and-government-agencies/organisations/small-business) | Not stated | M |

## Freshness and limits

The adjacent staleness.json records the calculated re-check schedule for dated load-bearing claims. With a zero-month regulatory verification window, the earliest calculated date is December 2015: dated regulatory claims are already due for re-check, not granted a future grace period. Undated claims are omitted from the calculation and require verification before adoption. Regulations and employment instruments require re-verification whenever adopted into an SRS or used operationally, regardless of publication age. Unknown publication dates cannot establish freshness. Check current accounting compilations before periods beginning in 2027; avoid assuming that old tax, privacy or licence guidance remains complete. The oldest privacy guidance and single-publisher compliance findings are explicit verification limits, not resolved venue obligations.
