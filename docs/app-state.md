# Goldy's application state

Implementation baseline: `24ed4a70`, 4 November 2026. Repository capability only; live
configuration, source availability and production acceptance were not verified.
Update this summary when capabilities change, not for every implementation task.

| Area | Exists in the repository | Remaining boundary |
|---|---|---|
| Data foundation | Immutable byte-faithful raw ledger, bitemporal canonical entities, reconciliation and resolved semantic projections; table-driven permissions. | Operational completeness and semantic correctness need dated evidence; infrastructure alone does not certify measures. |
| Weekly report | Live report, targets, matching Excel snapshot, food/beverage/waste/count details and qualified CTB source-cost estimates. | Representative current-week validation; actual stock variance/COGS; broader custom reports; retained issued report instances and automated delivery. |
| Financial meaning | Invoice purchases and qualified source/theoretical costs remain distinguishable. | Actual valuation and complete expense evidence gate actual COGS/operating profit; physical cash and bank/processor evidence need discovery. |
| Prime cost | Target scope, not established by existing source-cost estimates. | Actual COGS and labour cost must both be validated on comparable bases; weekly inclusion waits on labour readiness. |
| Sources | Lightspeed scheduled webhooks; CTB AJAX/SFTP; OpenTable assisted-session JSON with CSV fallback; Deputy raw webhook. | Deputy canonicalization, source fitness and any new cash/bank/expense integration; menu canonicalization remains a documented gap. MarketMan history remains a possible one-time input. |
| Reservations/reviews | Canonical reservations and reviews; review detail UI and classification code. | Validate metrics, source coverage and summary quality. Reservation covers are a party-size proxy, not verified actual arrivals. |
| Products/stock | Product classification and reviewed mappings; stock-count/waste/recipe evidence and qualified reporting paths. | Complete supported units/movements/valuation, unexplained variance and replenishment recommendations. |
| Forecasting | Async persisted TimesFM runs, qualified targets/covariates and comparison to actuals. | Validate revenue and individual-product contracts against actual API capability and source fitness before calling them accepted. |
| Interaction | Ask Goldy's bounded tools; saved dashboards rendered from live queries; source diagnostics and evidence-query registry. | Reusable custom report lifecycle, clear owner/manager workflows and approved role mapping. |
| Users/follow-up | Owner account administration; domain review/curation queues and local data corrections. | Manager grants; general assigned operational follow-up lifecycle. Existing review queues do not establish a complete task workflow. |
| Automation | Source ingestion schedules. | Business report delivery and condition-based notification/export automation are target gaps, distinct from ingestion scheduling. |

## Read with care

Use the [issue index](roadmap.md) for proposed work and the [PRD](prd.md) for target
scope. An issue closed as implemented still needs the appropriate acceptance
evidence before being described as operationally verified.
