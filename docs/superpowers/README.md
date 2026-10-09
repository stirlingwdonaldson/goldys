# Design records (specs and plans)

Dated, point-in-time design work produced with the superpowers workflow: a **spec**
(`specs/YYYY-MM-DD-<topic>-design.md`) agreed first, then an executable **plan**
(`plans/YYYY-MM-DD-<topic>.md`). They record what was intended when they were written.
Don't edit them to track later changes. Current behaviour is in the code and in the
living docs indexed in [`../README.md`](../README.md).

Many specs still carry their original `Status: Draft` or `In review` header. The
**Outcome** column below is the current state, checked against the code on
2026-10-09.

| Date | Topic | Spec | Plan | Outcome |
|---|---|---|---|---|
| 09-19 | Phase 1 MVP foundation (ledger, canonical, permissions) | [spec](specs/2026-09-19-phase-one-mvp-design.md) | [plan](plans/2026-09-19-phase-one-foundation.md) | Implemented. OIDC later replaced by email + password |
| 09-20 | Deployment environments | [spec](specs/2026-09-20-deployment-environments-design.md) | [plan](plans/2026-09-20-deployment-environments.md) | Implemented; runbook in `operations/deployment.md` |
| 09-20 | Email + password auth | [spec](specs/2026-09-20-email-password-auth-design.md) | [plan](plans/2026-09-20-email-password-auth.md) | Implemented (V7, V8) |
| 09-20 | Frontend shell | [spec](specs/2026-09-20-frontend-shell-design.md) | [plan](plans/2026-09-20-frontend-shell.md) | Implemented; later restyled by `design/ui-direction.md` |
| 09-20 | Frontend data screens (demo/live `Api`) | [spec](specs/2026-09-20-frontend-data-screens-design.md) | [plan](plans/2026-09-20-frontend-data-screens.md) | Implemented |
| 09-20 | Daily sales reconciliation | [spec](specs/2026-09-20-sales-reconciliation-design.md) | [plan](plans/2026-09-20-sales-reconciliation.md) | Implemented (V5) |
| 09-21 | Line-item (product) reconciliation | [spec](specs/2026-09-21-line-item-reconciliation-design.md) | [plan](plans/2026-09-21-line-item-reconciliation.md) | Implemented (V9, V10) |
| 09-22 | Dashboard round-out | — | [plan](plans/2026-09-22-dashboard-round-out.md) | Implemented |
| 09-23 | OpenTable scripted connector | [spec](specs/2026-09-23-opentable-connector-design.md) | [plan](plans/2026-09-23-opentable-connector.md) | Superseded: OpenTable is a manual CSV drop today |
| 09-23 | OpenTable session redesign | [spec](specs/2026-09-23-opentable-session-redesign.md) | [plan](plans/2026-09-23-opentable-session-redesign.md) | Superseded, as above |
| 09-29 | Frontend management IA (nav groups) | [spec](specs/2026-09-29-frontend-management-ia-design.md) | [plan](plans/2026-09-29-frontend-management-ia.md) | Implemented |
| 09-30 | Phase 2 UI design | [spec](specs/2026-09-30-phase-two-ui-design.md) | — | Partly implemented (rules, Ask Goldy's); Smart Exporter and Automation Hub not built |
| 09-30 | Resolution rule engine | [spec](specs/2026-09-30-rule-engine-design.md) | [plan](plans/2026-09-30-rule-engine.md) | Implemented (V12) |
| 09-30 | Resolution rules UI | — | [plan](plans/2026-09-30-resolution-rules-ui.md) | Implemented |
| 10-02 | Conversational BI (Ask Goldy's) | [spec](specs/2026-10-02-conversational-bi-design.md) | [plan](plans/2026-10-02-conversational-bi.md) | Implemented (V13) |
| 10-02 | Reporting tool framework | [spec](specs/2026-10-02-reporting-tool-framework-design.md) | [plan](plans/2026-10-02-reporting-tool-framework.md) | Implemented |
| 10-02 | Ingestion failure feedback | [spec](specs/2026-10-02-ingestion-failure-feedback-design.md) | [plan](plans/2026-10-02-ingestion-failure-feedback.md) | Implemented (V14) |
| 10-02 | Ingestion log viewer redesign | [spec](specs/2026-10-02-ingestion-log-viewer-redesign-design.md) | [plan](plans/2026-10-02-ingestion-log-viewer-redesign.md) | Implemented (`/logs`) |
| 10-06 | Daily sales read model | [spec](specs/2026-10-06-daily-sales-read-model-design.md) | [plan](plans/2026-10-06-daily-sales-read-model.md) | Implemented (V15) |
| 10-06 | Product sales read model | [spec](specs/2026-10-06-product-sales-read-model-design.md) | [plan](plans/2026-10-06-product-sales-read-model.md) | Implemented (V16, V17) |
| 10-06 | Reservations, labour, inventory domains | [spec](specs/2026-10-06-next-domains-reservations-labour-inventory-design.md) | [plan](plans/2026-10-06-next-domains-reservations-labour-inventory.md) | Implemented (V20–V23); Deputy canonicalization still deferred |
| 10-06 | Semantic metric catalogue | [spec](specs/2026-10-06-semantic-metric-catalogue-design.md) | [plan](plans/2026-10-06-semantic-metric-catalogue.md) | Implemented; see `metrics/catalog.md` |
| 10-07 | Ask Goldy's analytical expansion | [spec](specs/2026-10-07-ask-goldys-analytical-expansion-design.md) | [plan](plans/2026-10-07-ask-goldys-analytical-expansion.md) | Implemented (V26, V27) |
| 10-07 | Saved dashboards | [spec](specs/2026-10-07-dashboards-design.md) | [plan](plans/2026-10-07-dashboards.md) | Implemented (V18, V25) |
| 10-08 | Data explorer | [spec](specs/2026-10-08-data-explorer-design.md) | [plan](plans/2026-10-08-data-explorer.md) | Implemented (`/data`) |
| 10-08 | Integrate data-source research | — | [plan](plans/2026-10-08-integrate-data-source-research.md) | Docs integrated; MarketMan pull deferred |
| 10-08 | Invoice ingestion (CTB CSV + SFTP) | [spec](specs/2026-10-08-invoice-ingestion-design.md) | [plan](plans/2026-10-08-invoice-ingestion.md) | Implemented (V28) |
| 10-08 | Invoice PDF enrichment | [spec](specs/2026-10-08-pdf-enrichment-design.md) | [plan](plans/2026-10-08-pdf-enrichment.md) | Implemented (V29–V31) |
| 10-08 | Provenance and trust | [spec](specs/2026-10-08-provenance-trust-design.md) | [plan](plans/2026-10-08-provenance-trust.md) | Implemented |
| 10-09 | Invoice node graph | [spec](specs/2026-10-09-invoice-node-graph-design.md) | [plan](plans/2026-10-09-invoice-node-graph.md) | Implemented |

Some older records reference paths that have since moved. File paths inside them
have been updated to the current layout (`docs/operations/`, `docs/design/`,
`docs/decisions/`, `docs/architecture/`); class and package names have not.
