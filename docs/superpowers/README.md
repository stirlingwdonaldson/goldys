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
| 10-24 | UX trust fixes and prioritised UX/UI remediation (rostering excluded) | [design](specs/2026-10-24-ux-trust-fixes-design.md) | [plan](plans/2026-10-24-ux-ui-remediation.md) | First trust batch verified locally; not pushed or deployed |
| 10-23 | Sale recipe costing, adjustments and payment indicators | [spec](specs/2026-10-23-sale-recipe-costing-design.md) | [plan](plans/2026-10-23-sale-recipe-costing.md) | Implemented locally; recipe costs assumed ex-GST via a reversible setting, disclosed on sale estimates |
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
| 10-11 | CTB invoice gap recovery | [spec](specs/2026-10-11-ctb-invoice-gap-recovery-design.md) | — | Design — not implemented |
| 10-11 | Network graph (platform map, menu affinity) | [spec](specs/2026-10-11-network-graph-design.md) | [plan](plans/2026-10-11-network-graph.md) | Phase A implemented (platform map); Phase B gated on a production basket-size check |
| 10-12 | CTB recipe non-JSON: empty POST body → HTTP 411 | [spec](specs/2026-10-12-ctb-recipe-non-json-isolation-design.md) | — | Implemented and verified live |
| 10-12 | CTB detail backfill (stocktake / wastage / recipe composition) | [spec](specs/2026-10-12-ctb-detail-backfill-design.md) | [plan](plans/2026-10-12-ctb-detail-backfill.md) | Slice A implemented (raw-ledger detail backfill, gated on `app.ctb-detail-backfill`) |
| 10-12 | TimesFM forecasting | [spec](specs/2026-10-12-timesfm-forecasting-design.md) | [plan](plans/2026-10-12-timesfm-forecasting.md) | In progress |
| 10-12 | Recipe ingredients (the "recipes.ingredients" slice) | [spec](specs/2026-10-12-recipe-ingredients-design.md) | [plan](plans/2026-10-12-recipe-ingredients.md) | Implemented (V49, V50) |
| 10-12 | Invoice identity (supplier-scoped headers, reorder-stable lines) | [spec](specs/2026-10-12-invoice-identity-design.md) | [plan](plans/2026-10-12-invoice-identity.md) | Planned — not implemented |
| 10-13 | OpenTable assisted session (human login via live view, JSON API pull) | [spec](specs/2026-10-13-opentable-assisted-session-design.md) | [plan](plans/2026-10-13-opentable-assisted-session.md) | Implemented on branch, not yet deployed (V52; off unless `OPENTABLE_API_ENABLED`). Supersedes the 09-23 session redesign's login/engine decisions |
| 10-13 | Supplier PDF templates (deterministic extraction for non-templated suppliers) | [spec](specs/2026-10-13-supplier-pdf-templates-design.md) | — | Design — awaiting approval |
| 10-13 | OpenTable reviews, menu items & rich reservations | [spec](specs/2026-10-13-opentable-reviews-menu-reservations-design.md) | [plan](plans/2026-10-13-opentable-reviews-menu-reservations.md) | Implemented on branch (rich CSV backfill, V54/V55, `canonical_guest`) |
| 10-13 | OpenTable JSON → rich reservation mapping (fix sparse supersession) | [spec](specs/2026-10-13-opentable-json-rich-mapping-design.md) | — | Implemented on branch |
| 10-13 | Recipe costing gap recovery (price join, off-menu label, last-sold) | [spec](specs/2026-10-13-recipe-costing-gap-design.md) | [plan](plans/2026-10-13-recipe-costing-gap.md) | Planned — not implemented |
| 10-16 | Invoice read correctness and next data-correctness increments | [spec](specs/2026-10-16-invoice-read-correctness-design.md) | [plan](plans/2026-10-16-data-correctness-next-increments.md) | Invoice increments implemented on task branch; coverage/product discovery recorded; production unchanged |
| 10-23 | Complete CTB recipe snapshots, detail UI, source evidence and navigation | [design](specs/2026-10-23-ctb-recipes-design.md) · [coverage](specs/2026-10-23-ctb-recipes-coverage.md) · [fields](specs/2026-10-23-ctb-recipe-field-coverage.md) | [plan](plans/2026-10-23-ctb-recipes-complete-ui.md) | Implemented locally; collection/replay remain off pending rollout |

| 10-16 | Product identity, coverage and dated inventory observations | [spec](specs/2026-10-16-product-coverage-observations-design.md) | [plan](plans/2026-10-16-product-coverage-observations.md) | Implemented on task branch; local verification recorded in audit report; production unchanged |

| 10-16 | Stock/count/waste replay and header date lineage | [spec](specs/2026-10-16-stock-waste-replay-design.md) | [plan](plans/2026-10-16-stock-waste-replay.md) | Implemented on task branch; backend verification in audit report; production unchanged |

Some older records reference paths that have since moved. File paths inside them
have been updated to the current layout (`docs/operations/`, `docs/design/`,
`docs/decisions/`, `docs/architecture/`); class and package names have not.
