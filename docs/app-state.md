# Goldy's application state

Implementation baseline: `24ed4a70`, 4 November 2026. Repository capability only; live
configuration, source availability and production acceptance were not verified.
Pi update, 16 November 2026: the core workspace is deployed at `96c9af4d`; normal
login/reporting are user-confirmed. Other operational acceptance is limited to the
[evidence record](operations/pi-production-followup-2026-11-16.md). This does not
re-certify the older source/data capability baseline below.

| Area | Exists in the repository | Remaining boundary |
|---|---|---|
| Data foundation | Immutable byte-faithful raw ledger, bitemporal canonical entities, reconciliation and resolved semantic projections; table-driven permissions. | Operational completeness and semantic correctness need dated evidence; infrastructure alone does not certify measures. |
| Weekly report | Live report, targets, matching Excel snapshot, food/beverage/waste/count details and qualified CTB source-cost estimates. | Representative current-week validation; actual stock variance/COGS; broader custom reports; retained issued report instances and automated delivery. |
| Sales detail | Merchandise versus POS payments at cent precision; missing amounts, different observed date populations and conflicts stay visible. Payment report key collisions fail before publication. | Historical multiplied/missing merchandise needs clean source exports and reviewed corrections; POS tender comparison does not establish bank settlement. See [comparison safeguards](operations/sales-payment-comparison.md). |
| Financial meaning | Invoice purchases and qualified source/theoretical costs remain distinguishable. | Actual valuation and complete expense evidence gate actual COGS/operating profit; physical cash and bank/processor evidence need discovery. |
| Prime cost | Target scope, not established by existing source-cost estimates. | Actual COGS and labour cost must both be validated on comparable bases; weekly inclusion waits on labour readiness. |
| Sources | Lightspeed scheduled webhooks; CTB AJAX/SFTP; OpenTable assisted-session JSON with CSV fallback; Tanda manual raw API capture (see [setup](connectors/tanda.md)); Deputy webhook retired. | Tanda real-account validation, canonicalization, workforce UI and heuristics, source fitness and any new cash/bank/expense integration; menu canonicalization remains a documented gap. MarketMan history remains a possible one-time input. |
| Reservations/reviews | Canonical reservations and reviews; review detail UI and classification code. | Validate metrics, source coverage and summary quality. Reservation covers are a party-size proxy, not verified actual arrivals. |
| Products/stock | Product classification and reviewed mappings; stock-count/waste/recipe evidence and qualified reporting paths. | Complete supported units/movements/valuation, unexplained variance and replenishment recommendations. |
| Forecasting | Async persisted TimesFM runs, qualified targets/covariates and comparison to actuals. | Validate revenue and individual-product contracts against actual API capability and source fitness before calling them accepted. |
| Interaction | Pi-backed Ask Goldy's with existing Spring accounts/permissions, persisted investigations, queued follow-ups, Stop/retry, bounded reporting/public research and explicit query-only dashboard saves. Legacy frontend rollback retained. | Remaining production workflow acceptance; real provider invoice reconciliation; files/sandbox disabled; reusable custom report lifecycle and manager mapping remain separate dependencies. |
| Users/follow-up | Owner account administration; domain review/curation queues and local data corrections. | Manager grants; general assigned operational follow-up lifecycle. Existing review queues do not establish a complete task workflow. |
| Automation | Source ingestion schedules. | Business report delivery and condition-based notification/export automation are target gaps, distinct from ingestion scheduling. |

## Read with care

Use the [issue index](roadmap.md) for proposed work and the [PRD](prd.md) for target
scope. An issue closed as implemented still needs the appropriate acceptance
evidence before being described as operationally verified.

## Pi workspace migration (local implementation)

The released Pi 1.5.2 session adapter and disabled persisted-job/private reporting interfaces are under implementation. Existing Ask remains the user path. See [implementation evidence](design/pi-workspace-implementation-2026-11-15.md) for observed tests, incomplete slices and acceptance gates. Do not enable `app.workspace.enabled` for a pilot yet.

The local branch includes response-bound reporting evidence, a digest-pinned worker
image and conservative policy stamps for legacy conversation history. Pre-V120 legacy
threads remain stored but are restricted because their historical access cannot be
verified; new legacy conversations remain available. The history screen withholds
cached content until reauthorization and hides it on failed reads. These safeguards
do not complete the sandbox, files, research, accounting or operational transition.
