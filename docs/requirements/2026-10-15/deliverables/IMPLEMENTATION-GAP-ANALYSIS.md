# Goldy's — Implementation gap analysis

Date: 14 October 2026 (Australia/Melbourne). Inspection target: local `reference/goldys`, commit `9d625c7f23c88112302acc0a89d7826a3443f462`; its working tree was clean when inspected.

## Conclusion

The application has a substantial evidence-ingestion, reconciliation and analytical foundation. It does **not yet satisfy the independently established reporting, operational decision and UX contracts as a complete product**. The most consequential gaps are recurring versioned weekly reports and CSV/Excel exports; trustworthy COGS, actual-cover and labour meanings; usable stock and replenishment; atomic people/work and money relationships; specific-product forecasts; responsibility-based navigation/access; and accountable local follow-up.

Several gaps are software limitations despite available source fields or raw ingestion paths. In particular, supplier/stock/order/conversion master data is fetched without a canonical business model, stock/waste canonical data is not consumed by the inventory projection, and the forecasting request cannot select a product. Missing service fulfilment, bank settlement, worker/roster details and validated venue policies require additional evidence as well as software. An unavailable source never makes the business requirement unnecessary.

This is an assessment of repository capability, not a production acceptance certificate. No source services, production database, live application or external research were accessed. No dependencies were installed, tests executed, migrations run, applications started, code changed or deployment performed. Existing tests and audit documents are evidence of intended/previously observed behaviour, not proof that the current checkout passes all acceptance cases. Only this report was written.

## Independent authority and assessment method

The governing baseline is [PROJECT_CHARTER.md](../PROJECT_CHARTER.md), the completed [PRD](prds/prd-goldys-requirements-2026-10-14/prd.md), [EXPERIENCE.md](ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md) and [DESIGN.md](ux-designs/ux-goldys-requirements-2026-10-14/DESIGN.md). The independent [brief](briefs/brief-goldys-requirements-2026-10-14/brief.md), [domain model](../research/business-domain-model.md) and [capability map](../research/business-capability-map.md) explain the business rationale. Application PRDs, architecture and historical audit recommendations are implementation evidence only; they do not redefine these requirements.

All **154 GOL requirements** and **18 GUX contracts** are assessed individually below. Each entry preserves its independent title/status, gives a classification, severity, specific remaining boundary and gap-register reference. The referenced gap supplies business impact, file evidence, source-versus-software diagnosis and proposed direction. Each requirement's `.AC1` is included in its assessment; presence of a similarly named table, screen or metric does not pass the acceptance contract. UX-AC01–10 and UJ-01–08 are cross-checked separately.

B = baseline; C = candidate awaiting validation; CC = conditional candidate. Severity describes the consequence **if the relevant candidate is adopted or relied upon**, not stakeholder-approved priority, legal applicability, delivery order or a funded backlog. Open decisions OD-01–14 remain open. Proposed directions identify business capabilities to restore, not an implementation plan or architecture selection.

Classification legend:

1. **Fully implemented:** complete bounded repository contract supported; no claim of live deployment or stakeholder acceptance.
2. **Partially implemented:** meaningful subset exists; named acceptance conditions remain unmet.
3. **Implemented but semantically incorrect:** an existing representation/calculation conflicts with the independent contract. This takes precedence over a merely partial label when misleading meaning is central.
4. **Present in raw data but not canonicalised:** retained historical evidence or explicit raw-only ingestion supports the concept, but the required canonical meaning/relationships are missing. This does not imply that today's production ledger contains the dataset or that every required field exists.
5. **Not implemented:** no end-to-end capability or required business model found in the inspected migrations, models, services, API/tool contracts and screens. Absence of software does not establish absence of business activity.
6. **Cannot verify from repository evidence:** acceptance depends on runtime, stakeholder, source, deployment or human-use evidence unavailable here. This is not a pass.

High = likely materially misleading decision, confidentiality failure or blocked central business task. Medium = incomplete operational explanation, scope, workflow or quality safeguard. Low = documentation/governance boundary with limited immediate operational consequence. None = no gap within the inspected bounded contract.

## Evidence index

Each citation below names an actual inspected file and the starting line of the relevant implementation. Read the surrounding method/schema; migrations must be read cumulatively through V72, not as current population statements. An old migration comment such as “no ingestion source” is superseded where later code supplies that path.

### E01

**Raw ledger and revision foundation.**

- [backend/src/main/resources/db/migration/V1__baseline_schema.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V1__baseline_schema.sql:1)
- [backend/src/main/resources/db/migration/V2__ingestion_ledger.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V2__ingestion_ledger.sql:1)
- [backend/src/main/java/com/goldys/platform/canonical/BitemporalEntity.java:24](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/BitemporalEntity.java:24)

### E02

**Source paths and raw-only datasets.**

- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java:90](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java:90)
- [backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java:19](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java:19)
- [backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableMenuItemsCsvIngestService.java:27](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableMenuItemsCsvIngestService.java:27)

### E03

**Business identity boundaries.**

- [backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceService.java:29](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceService.java:29)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:83](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:83)
- [backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:34](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:34)

### E04

**Detailed sales and tender contracts.**

- [backend/src/main/resources/db/migration/V33__payments.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V33__payments.sql:1)
- [backend/src/main/resources/db/migration/V35__sale_item_line_details.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V35__sale_item_line_details.sql:1)
- [backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSaleItemIngestService.java:109](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSaleItemIngestService.java:109)
- [backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedAllSalesIngestService.java:68](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedAllSalesIngestService.java:68)

### E05

**Reservation versus attendance.**

- [backend/src/main/resources/db/migration/V54__reservation_detail.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V54__reservation_detail.sql:1)
- [backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationJsonParser.java:55](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReservationJsonParser.java:55)
- [backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java:139](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java:139)

### E06

**Purchasing and derived metric meanings.**

- [backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java:199](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java:199)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:74](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:74)
- [backend/src/main/java/com/goldys/platform/reconciliation/InventoryProjector.java:58](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/InventoryProjector.java:58)

### E07

**Stock and waste canonicalisation.**

- [backend/src/main/resources/db/migration/V68__stock_wastage_canonical.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V68__stock_wastage_canonical.sql:1)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunner.java:27](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/StockWastageCanonicalizationRunner.java:27)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtbStocktakeParser.java:59](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtbStocktakeParser.java:59)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtbWastageParser.java:28](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtbWastageParser.java:28)

### E08

**Recipes and ingredient resolution.**

- [backend/src/main/resources/db/migration/V47__recipe_composition.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V47__recipe_composition.sql:1)
- [backend/src/main/resources/db/migration/V49__recipe_ingredients.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V49__recipe_ingredients.sql:1)
- [backend/src/main/java/com/goldys/platform/reconciliation/RecipeIngredientProjector.java:39](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/RecipeIngredientProjector.java:39)
- [backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java:69](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java:69)

### E09

**Labour contracts and surface.**

- [backend/src/main/resources/db/migration/V21__labour.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V21__labour.sql:1)
- [backend/src/main/java/com/goldys/platform/reconciliation/LabourProjector.java:115](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/LabourProjector.java:115)
- [backend/src/main/java/com/goldys/platform/application/LabourReportingService.java:51](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/LabourReportingService.java:51)
- [frontend/components/staff/staff-page-view.tsx:40](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/components/staff/staff-page-view.tsx:40)

### E10

**Summary and unit-cost calculations.**

- [backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java:53](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java:53)
- [backend/src/main/java/com/goldys/platform/reconciliation/InvoiceLineMetricsServiceImpl.java:39](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/InvoiceLineMetricsServiceImpl.java:39)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:211](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:211)

### E11

**Forecast inputs and persistence.**

- [backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java:91](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java:91)
- [backend/src/main/java/com/goldys/platform/forecasting/ForecastSeriesAssembler.java:44](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/forecasting/ForecastSeriesAssembler.java:44)
- [backend/src/main/resources/db/migration/V44__forecast_run.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V44__forecast_run.sql:1)
- [forecast-service/predictor.py:82](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/forecast-service/predictor.py:82)

### E12

**Forecast UX, comparison and access.**

- [backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:108](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:108)
- [frontend/app/(app)/forecasting/page.tsx:33](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/forecasting/page.tsx:33)

### E13

**Trust and provenance.**

- [backend/src/main/java/com/goldys/platform/application/TrustService.java:66](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/TrustService.java:66)
- [backend/src/main/java/com/goldys/platform/application/TrustService.java:107](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/TrustService.java:107)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:291](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:291)

### E14

**Matched ratios versus separate summary calculations.**

- [backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:123](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:123)
- [backend/src/main/java/com/goldys/platform/application/LabourReportingService.java:51](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/LabourReportingService.java:51)
- [backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java:53](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java:53)

### E15

**Role/resource grants.**

- [backend/src/main/java/com/goldys/platform/auth/PermissionService.java:20](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/auth/PermissionService.java:20)
- [backend/src/main/java/com/goldys/platform/auth/Permission.java:22](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/auth/Permission.java:22)
- [backend/src/main/java/com/goldys/platform/auth/CurrentUserService.java:7](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/auth/CurrentUserService.java:7)
- [backend/src/main/resources/db/migration/V23__seed_domain_permissions.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V23__seed_domain_permissions.sql:1)
- [backend/src/main/resources/db/migration/V45__seed_forecasting_permissions.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V45__seed_forecasting_permissions.sql:1)

### E16

**Cross-domain and forecast read gates.**

- [backend/src/main/java/com/goldys/platform/application/DataExplorerService.java:18](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/DataExplorerService.java:18)
- [backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:116](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:116)
- [backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java:32](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java:32)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java:30](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java:30)

### E17

**Navigation and home.**

- [frontend/lib/nav.ts:51](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/nav.ts:51)
- [frontend/lib/work-queue.ts:17](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/work-queue.ts:17)
- [frontend/components/app-shell/app-sidebar.tsx:142](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/components/app-shell/app-sidebar.tsx:142)
- [frontend/app/(app)/dashboard/page.tsx:48](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/dashboard/page.tsx:48)

### E18

**Exception ordering and operative overrides.**

- [frontend/lib/work-queue.ts:117](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/work-queue.ts:117)
- [frontend/lib/work-queue.ts:246](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/work-queue.ts:246)
- [backend/src/main/java/com/goldys/platform/reconciliation/InventoryOverrideService.java:36](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/InventoryOverrideService.java:36)
- [backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java:93](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/ReservationProjector.java:93)

### E19

**Notes and local audit fragments.**

- [backend/src/main/java/com/goldys/platform/canonical/InvoiceNoteService.java:7](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/InvoiceNoteService.java:7)
- [backend/src/main/resources/db/migration/V65__invoice_note.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V65__invoice_note.sql:1)
- [backend/src/main/java/com/goldys/platform/reconciliation/OverrideAuditService.java:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/OverrideAuditService.java:1)
- [backend/src/main/java/com/goldys/platform/reconciliation/RuleAuditService.java:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/RuleAuditService.java:1)

### E20

**Business query limits.**

- [backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQuery.java:5](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQuery.java:5)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java:30](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQueryServiceImpl.java:30)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java:90](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java:90)
- [backend/src/main/java/com/goldys/platform/application/DataExplorerService.java:50](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/DataExplorerService.java:50)

### E21

**Domain/service boundaries.**

- [docs/architecture/current-state.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/architecture/current-state.md:1)
- [docs/system-context.md:24](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/system-context.md:24)
- [docs/architecture/adding-a-domain.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/architecture/adding-a-domain.md:1)
- [backend/src/main/java/com/goldys/platform/reporting/ToolRegistry.java:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reporting/ToolRegistry.java:1)

### E22

**Historical audits.**

- [docs/audits/production-data/00-executive-summary.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/audits/production-data/00-executive-summary.md:1)
- [docs/audits/production-data/07-canonical-and-projections.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/audits/production-data/07-canonical-and-projections.md:1)
- [docs/audits/production-data/08-analytical-capabilities.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/audits/production-data/08-analytical-capabilities.md:1)
- [docs/frontend/audit.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/frontend/audit.md:1)

### E23

**Verified changes since the audit.**

- [frontend/app/(app)/sales/page.tsx:29](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/sales/page.tsx:29)
- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java:93](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java:93)
- [backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:232](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java:232)

### E24

**Time meaning.**

- [backend/src/main/java/com/goldys/platform/config/VenueClock.java:9](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/config/VenueClock.java:9)
- [backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java:110](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/forecasting/ForecastService.java:110)
- [backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:128](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/ForecastingApplicationService.java:128)
- [backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:47](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:47)

### E25

**Core workflow screens.**

- [frontend/app/(app)/kitchen/page.tsx:92](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/kitchen/page.tsx:92)
- [frontend/app/(app)/staff/page.tsx:7](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/staff/page.tsx:7)
- [frontend/app/(app)/sales-detail/page.tsx:35](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/sales-detail/page.tsx:35)
- [frontend/app/(app)/reservations/page.tsx:68](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/reservations/page.tsx:68)
- [frontend/app/(app)/recipes/page.tsx:185](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/app/(app)/recipes/page.tsx:185)

### E26

**Saved dashboard revisions.**

- [backend/src/main/resources/db/migration/V18__saved_dashboards.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V18__saved_dashboards.sql:1)
- [backend/src/main/resources/db/migration/V25__dashboard_documents_v2.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V25__dashboard_documents_v2.sql:1)
- [backend/src/main/java/com/goldys/platform/application/SavedDashboardApplicationService.java:245](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/application/SavedDashboardApplicationService.java:245)
- [docs/architecture/current-state.md:143](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/architecture/current-state.md:143)

### E27

**Source discovery and limits.**

- [docs/connectors/source-access.md:9](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/connectors/source-access.md:9)
- [docs/connectors/source-access.md:174](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/connectors/source-access.md:174)
- [docs/connectors/ctb-invoice-export-gaps.md:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/docs/connectors/ctb-invoice-export-gaps.md:1)

### E28

**Presentation, states and demo boundary.**

- [frontend/components/app-shell/app-sidebar.tsx:56](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/components/app-shell/app-sidebar.tsx:56)
- [frontend/components/states/loading-state.tsx:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/components/states/loading-state.tsx:1)
- [frontend/lib/format.ts:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/format.ts:1)
- [frontend/lib/api/index.ts:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/api/index.ts:1)
- [frontend/lib/demo-mode.tsx:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/frontend/lib/demo-mode.tsx:1)

### E29

**Transformation stage improvements.**

- [backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:70](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java:70)
- [backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSaleItemIngestService.java:66](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSaleItemIngestService.java:66)
- [backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedPaymentIngestService.java:63](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedPaymentIngestService.java:63)
- [backend/src/main/resources/db/migration/V32__ingestion_stage.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V32__ingestion_stage.sql:1)

### E30

**Products, menus and prices.**

- [backend/src/main/resources/db/migration/V59__lightspeed_product_catalog.sql:1](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/resources/db/migration/V59__lightspeed_product_catalog.sql:1)
- [backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java:100](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/reconciliation/RecipeProjector.java:100)
- [backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:35](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/reference/goldys/backend/src/main/java/com/goldys/platform/canonical/CanonicalProductSalesService.java:35)

## Gap register

<a id="g01"></a>

### G01 — Weekly reporting is not a retained recurring business report

**Severity: High. Business impact:** The owner/GM still lacks the requested repeatable weekly reporting handoff and a defensible original/reissued report.

**Implementation evidence and source/software distinction:** Saved dashboards retain query configuration and document revisions; rendering reruns current data. There is no report specification/instance, preparation schedule, readiness deadline, sign-off/coverage lifecycle or retained result revision in the migration/API inventory. Connector scheduling is collection scheduling, not report preparation. This is missing software; agreed week, contents, audience and deadline are also missing stakeholder contracts (OD-03/09/13). Evidence: [E20](#e20), [E21](#e21), [E26](#e26).

**Proposed direction:** Define the agreed report contract and retain identifiable prepared instances, inputs/definitions, coverage and linked restatements. Distinguish preparation success from validated finality; preserve dashboard reuse without treating a live dashboard as a frozen report.

<a id="g02"></a>

### G02 — Business CSV and formatted Excel exports are absent

**Severity: High. Business impact:** The explicit spreadsheet automation outcome cannot be completed inside Goldy’s; users still need manual assembly.

**Implementation evidence and source/software distinction:** CSV code handles incoming files. PDF download exposes original invoices. Neither is an outbound selected-facts/report CSV or formatted numeric Excel workbook. No outbound CSV/XLSX route, workbook generator or export workflow was found in main backend/frontend sources. Missing software is established; recipient tools and permitted report content remain OD-10/12/13. Evidence: [E20](#e20), [E25](#e25), [E26](#e26).

**Proposed direction:** Provide scope-aware report/fact exports with usable types, totals, readable headings, definition/revision/as-of, caveats, row counts and omission states. Recheck export grants and protect spreadsheet text from execution. Do not automatically distribute files without separate authority.

<a id="g03"></a>

### G03 — Purchases are presented as COGS and food cost

**Severity: High. Business impact:** Managers can mistake invoiced purchasing for consumed cost, misjudge margin and choose wrong stocking/pricing responses.

**Implementation evidence and source/software distinction:** InventoryProjector sums invoice line totals by invoice date and writes null wastage/stock. MetricCatalog calls that “Purchases (COGS)”; food_cost_percent divides purchases by gross sales. InventoryReportingService repeats the ratio, while supplier COGS is also invoice spend. Independent MET-007 requires carrying cost attributable to goods sold and matched net revenue; purchases, cash paid and COGS are different facts. The error is software meaning; movement/valuation and accounting-policy evidence are additional gaps. Evidence: [E06](#e06), [E10](#e10), [E25](#e25).

**Proposed direction:** Keep invoice purchases accurately named and qualified. Establish validated valuation/consumption/cost coverage before COGS, gross margin or prime-cost assertions; match revenue/category/tax/period scopes and disclose incomplete costs.

<a id="g04"></a>

### G04 — Actual covers and no-show denominators are conflated

**Severity: High. Business impact:** Spend per cover, hours per cover, demand and staffing feedback can be materially wrong.

**Implementation evidence and source/software distinction:** ReservationProjector:139–152 increments bookings for every row, including walk-ins/cancellations, and adds partySize to covers for SEATED/COMPLETED/WALK_IN. The parser supplies one partySize, not independent booked and observed cover counts. DerivedMetricExecutor uses gross venue sales / these covers without visit-to-check eligible net-sales allocation. No-show rate uses all bookings rather than a validated expected population. Known source NO_SHOW status is preserved; missing arrival is not automatically classified NO_SHOW. Source actual-cover basis remains unverified and the separate visit/allocation capability is absent. Evidence: [E05](#e05), [E06](#e06).

**Proposed direction:** Preserve intended party size separately from observed visit/covers, including partial arrival and walk-ins. Define expected no-show eligibility/cutoff and sales attribution coverage; leave actual covers or spend per cover unavailable when evidence is insufficient.

<a id="g05"></a>

### G05 — Raw-only masters and purchasing commitments prevent useful replenishment

**Severity: High. Business impact:** Ordering managers cannot explain what to buy through the next delivery despite available source concepts.

**Implementation evidence and source/software distinction:** CtbConnector:108–124 fetches recipe masters, stock, suppliers, stock orders, statements and reference data as raw-only datasets. No canonical supplier, stock-item, supplier-pack/terms, order/receipt/credit type or purchase-match allocation model appears in the cumulative schema. Invoice headers/lines cover only part of purchasing. Captured historical/source-path evidence exists; actual accepted/rejected deliveries, detailed commitments and promise times are not proved complete. Evidence: [E02](#e02), [E27](#e27), [E03](#e03).

**Proposed direction:** Canonicalise independently meaningful identities, dated pack/conversion/terms, typed documents and remaining commitments. Add receiving/match evidence where sources genuinely lack it. Preserve unknown delivery/conversion/usable-stock rules; do not derive accepted stock from invoice money.

<a id="g06"></a>

### G06 — Canonical stock/count/waste stops before the business read model

**Severity: High. Business impact:** Available counts and waste cannot support current stock, recount investigation, waste totals or usable supply in business screens.

**Implementation evidence and source/software distinction:** V68 and StockWastageCanonicalizationRunner provide header/count/waste models and flag-gated replay. InventoryProjector still reads only invoice lines, derives dates only from invoices and writes null stock/wastage. The resolved-only reporting boundary excludes newly canonical facts until a projection exists. Replay execution, population and current freshness are unverified. This is a software integration gap, not simply “no stock source”. Evidence: [E07](#e07), [E06](#e06).

**Proposed direction:** Expose qualified count/waste observations through business read contracts; retain count scope/cutoff/recount lineage and input completeness. Establish item/location movements and conversions before claiming current book/usable stock. Keep counts separate from movements and incidents separate from residual usage.

<a id="g07"></a>

### G07 — Unknown values are converted into false numeric facts

**Severity: High. Business impact:** An uncounted item can become zero stock, an unknown pack can become one unit and an unavailable price can become zero cost, corrupting decisions and ratios.

**Implementation evidence and source/software distinction:** CtbStocktakeParser:68 applies nz to qtyCount; malformed/missing decimal values become zero. CtInvoiceCsvIngestService:211–222 guesses the first numeric token or defaults to one, and :115 defaults missing unit cost to zero. Lightspeed sale-item :121–122 and payment :118–123 mapping also applies numeric zero fallbacks. Positive evidence of a real zero is not distinguished from absence. Some waste fields reject missing quantity instead of retaining an incomplete incident. Evidence: [E07](#e07), [E10](#e10), [E04](#e04).

**Proposed direction:** Retain the original quantity text and explicit known/unknown/not-captured/disputed/not-applicable states. Separate known physical facts from unknown costs; qualify unresolved conversions and allow incomplete incidents without fabricated quantities.

<a id="g08"></a>

### G08 — Invoice and product identity is narrower than business identity

**Severity: High. Business impact:** Different suppliers’ invoice numbers or distinct same-named products can merge, supersede each other or receive wrong costs.

**Implementation evidence and source/software distinction:** CanonicalInvoiceService locks by source+invoiceNumber and builds logical identity from invoiceNumber alone. CSV line refs use number+sequence; supplier lookup and joins remain number-based. Raw CTB supplier/invoice IDs are available in historical audit evidence. ProductSalesService and CTB aggregation key product/day by normalised name, despite product/stock IDs elsewhere. The 12 October invoice-identity design does not establish an implemented fix: current service still uses the old key. Evidence: [E03](#e03), [E10](#e10), [E30](#e30).

**Proposed direction:** Use issuer/type-qualified enduring document identity and stable product/variant identity, with source numbers/names as aliases. Retain ambiguous mappings and legitimate repeated occurrences; do not infer universal identity from a display number or name.

<a id="g09"></a>

### G09 — Sales, modifiers and payment relationships are incomplete

**Severity: High. Business impact:** Users cannot reliably explain unpaid balances, deposits, discounts, reversals, preparation consumption or settlement against a customer check.

**Implementation evidence and source/software distinction:** V33/V35 plus newer parsers support tenders and receipt-line occurrences. All-sales ingestion still collapses source checks into daily totals; no standalone check with split/merge lifecycle, modifier selection, signed adjustment allocation, generic payment allocation, liability ledger, cash-count or provider/bank settlement model exists. Rich OpenTable prepayment/POS amounts are canonical reservation attributes, not the required liability/payment/allocation entities. Source report totals and identifiers are partly present; complete money/status and fulfilment evidence is not established. Evidence: [E04](#e04), [E05](#e05), [E02](#e02).

**Proposed direction:** Retain checks, independent line/modifier/adjustment occurrences, typed transfers/liabilities and evidenced allocations. Add source evidence for cash/settlement/fulfilment where needed. Treat charged, paid, settled and fulfilled as separate states; never infer restock from refund money.

<a id="g10"></a>

### G10 — Recipes do not yet support effective modifier-aware consumption

**Severity: High. Business impact:** Kitchen and FOH cannot explain ingredient replenishment or theoretical versus residual usage for the historical product mix.

**Implementation evidence and source/software distinction:** Recipe cost, links and ingredients are implemented, including sub-recipe flags and source quantities. Current resolved recipe/ingredient rows are rebuilt from current canonical snapshots; the resolved ingredient projection omits stock_usable, stock_wastage, stock_uom_shortname and is_broken_link carried by V49. No effective sale/modifier-to-component usage or stock-movement consumption assessment exists. Source documentation records missing parent-to-sub-recipe usage quantity; that is a real unverified source-detail limitation, distinct from dropped canonical fields and current-only software. Evidence: [E08](#e08), [E30](#e30), [E27](#e27).

**Proposed direction:** Preserve yield/conversion/broken-link qualifications to the decision surface, retain effective specification attribution, and calculate labelled theoretical usage only for evidenced components/modifier effects. Keep source portion-cost estimates distinct from verified actual COGS. Obtain missing sub-recipe usage evidence before roll-up.

<a id="g11"></a>

### G11 — Menu availability is inferred from positive price

**Severity: High. Business impact:** A legitimate zero-price offering can be treated as absent and pricing/cost context mistaken for observed availability.

**Implementation evidence and source/software distinction:** RecipeProjector:69–76 defines sellable as non-modifier with price > 0; :100–113 derives ON_MENU/OFF_MENU from matched product presence. Canonical product is not a versioned menu-context or availability assertion; OpenTable menu data remains raw-only. Historical charged line prices are retained, but effective intended terms and eligibility are not a complete business model. Evidence: [E30](#e30), [E08](#e08).

**Proposed direction:** Keep product catalogue, effective offering/price terms and observed availability independent. Zero price is valid. Qualify source-derived menu heuristics; do not equate product presence or recent price with availability at a service.

<a id="g12"></a>

### G12 — Labour is a provisional daily aggregate, not independent work/pay evidence

**Severity: High. Business impact:** Roster coverage, missing clock-out, skill gaps, payable hours and loaded labour cost cannot be reviewed reliably.

**Implementation evidence and source/software distinction:** DeputyIngestController stores unparsed raw webhooks. V21 defines daily staffRef/department scheduled and actual hours as NOT NULL and nullable cost totals; the old canonical_shift scaffold does not provide an active roster/timesheet ingestion path. There are no employee/engagement/role, availability/competency, roster version, timesheet/work-break or entitlement/payment component models. The Staff screen shows summary tiles, not coverage decisions. A confirmed Deputy payload and operational rules are missing evidence; lack of those models/mappers/workflows is missing software. Evidence: [E09](#e09), [E02](#e02).

**Proposed direction:** Verify source work/plan/pay grains, retain enduring person/assignment identity and independent published plan, observed interval, payable determination and cost components. Unknown end/break/rate stays unknown. Validate actual FOH/head-chef duties and minimise private information before scoped review.

<a id="g13"></a>

### G13 — Catalogue correctness does not propagate consistently to summaries

**Severity: High. Business impact:** A screen can give a plausible ratio that differs from the governed series or mixes unequal populations, without the necessary qualification.

**Implementation evidence and source/software distinction:** DerivedMetricExecutor now aligns numerator and denominator by shared resolved days and emits excluded-day notices (an improvement since the audit). LabourReportingService independently totals hours/covers/sales and calculates ratios; InventoryReportingService independently totals purchases/sales. Their summary DTOs discard series notices/coverage and can combine different day sets. Labour percentages use gross sales, unlike the independent net-sales contract; cost component completeness is not represented. Evidence: [E14](#e14), [E10](#e10), [E06](#e06).

**Proposed direction:** Use one validated measure contract across series, summaries, AI and exports. Carry eligible population, excluded days and completeness to all outputs; rederive period ratios from matched components and explicitly name the validated revenue/cost/hour bases.

<a id="g14"></a>

### G14 — Temporal revision columns do not establish business-effective history

**Severity: High. Business impact:** Late imports/corrections cannot reliably reproduce what was effective or reported for a past service or week.

**Implementation evidence and source/software distinction:** CanonicalProductSalesService:53–54 and CanonicalInvoiceService:50–51 pass recordedAt for both validFrom and recordedAt. BitemporalEntity.supersede closes system time only. Separate business dates help current reporting but do not satisfy effective-period/version attribution; saved dashboard revisions retain configuration, not results. ForecastService and comparison use UTC clock dates despite the VenueClock convention. Trading boundaries/policies remain unresolved. Evidence: [E01](#e01), [E03](#e03), [E24](#e24), [E26](#e26).

**Proposed direction:** Represent evidenced occurrence/effective precision separately from receipt, publication and calculation time. Bind reporting/service/pay calendars to validated policy versions; use consistent venue-local boundaries. Retain original report/forecast input and result revisions without fabricating effective times.

<a id="g15"></a>

### G15 — Provenance and decision eligibility are incomplete

**Severity: Medium. Business impact:** Users cannot reproduce every result or judge whether it is complete and fresh enough for their question.

**Implementation evidence and source/software distinction:** TrustService drill-down accepts only daily gross/net/GST and reconstructs current contributors; derived provenance contains Instant.EPOCH and empty contributor IDs. Single-raw-record canonical foreign keys cannot express every contributing page/row. Rich PDF links and stage tracking improve evidence, but do not establish metric input snapshots, expected business population or field-level transformation lineage. Trust uses configuration thresholds, not a validated decision-specific eligibility rule. Evidence: [E13](#e13), [E20](#e20), [E29](#e29).

**Proposed direction:** Retain contributor/definition/revision scope, explicit aggregate-only boundaries, last evidenced coverage versus receipt/recalculation, and expected/covered/excluded populations. Expose eligibility as unvalidated until policy is set; do not equate successful import with complete activity.

<a id="g16"></a>

### G16 — Specific-product forecasting is not supported end to end

**Severity: High. Business impact:** The directly requested parma/Aperol or other product outlook cannot be selected with a sale-unit/variant basis.

**Implementation evidence and source/software distinction:** The UI fixes target sales.gross and three predictors. ForecastService creates MetricQuery with empty dimensions and no product predicate; V44 target_dimension is unused. ForecastableMetrics permits all but top-sellers, but nominal eligibility is not selected-product forecasting. Product daily data exists; this restriction is a software request/query/UX limitation, not proof of missing product history. Evidence: [E11](#e11), [E12](#e12), [E20](#e20).

**Proposed direction:** Support an explicitly selected stable product/variant, units versus money, history coverage, horizon and price/availability assumptions. Qualify stockout-constrained or insufficient history. Do not sum products or use first series as a substitute for the selected item.

<a id="g17"></a>

### G17 — Revenue forecasts retain outputs but lose qualification and scenario context

**Severity: Medium. Business impact:** Users see quantiles and accuracy without the complete evidence basis needed to assess reliance or alternatives.

**Implementation evidence and source/software distinction:** Runs persist issue/request metadata, model version and quantile outputs; minimum 14 non-null points and MAE/MAPE missing/zero handling exist. ForecastService extracts only MetricSeries, discarding MetricResult notices/provenance, and re-queries inputs asynchronously after submission rather than retaining the checked input snapshot. The Python model uses past_only_covariates; UI comments describe demand known ahead of time, but no future bookings/roster covariate horizon is passed. No authored assumption/scenario versions or new-site transfer model exists. Accuracy is not verified/calibrated by this inspection. Evidence: [E11](#e11), [E12](#e12), [E13](#e13).

**Proposed direction:** Retain original eligible inputs/coverage/definitions and declared driver/uncertainty meaning; present quantiles as model outputs, not guarantees. Distinguish past-only predictors from known future input. Keep local scenarios separate from original forecasts and expose comparable outcome population/exclusions.

<a id="g18"></a>

### G18 — Resource grants cannot express the independent access contract

**Severity: High. Business impact:** Legitimate departmental users may be blocked, while adding broad grants risks exposing unrelated or sensitive information.

**Implementation evidence and source/software distinction:** PermissionService fails closed and uses explicit department×seniority×resource READ/WRITE rows; seeded resources are ALL×OWNER. There is no person/function purpose, effective interval, data-row/worker scope, separate export/annotation/definition operation or approval/delegation evidence. Labour summary requires hours AND cost to return even hours-only information; Staff UI hardcodes owner cost visibility. Source diagnostics and raw/canonical exploration share connectors READ. These are software limitations; OD-02/10 must supply validated grants rather than guessed manager entitlements. Evidence: [E15](#e15), [E16](#e16), [E09](#e09).

**Proposed direction:** Retain server-side fail-closed checks while supporting independently validated purpose, information/row scope, operation and effective delegation. Separate cost/worker detail from operational hours; allow entitled users through their actual grant, not title-only frontend rules.

<a id="g19"></a>

### G19 — Cross-domain and forecast reads have incomplete operand gates

**Severity: High. Business impact:** Future grants can expose a denied metric through another resource or previously prepared forecast.

**Implementation evidence and source/software distinction:** ForecastingApplicationService.list:117–119 returns all runs including series without target-metric filtering, unlike get/comparison. Labour summary gates hours/cost but reads covers and sales; inventory summary gates inventory.cost but reads sales. ToolDispatcher checks each top-level query permission, not recursively every derived operand; MetricQueryServiceImpl intentionally does not authorize. Current OWNER-only seeds reduce exposure; no current non-owner production breach is asserted. This is a concrete permission-boundary risk, not missing source data. Evidence: [E16](#e16), [E12](#e12), [E10](#e10), [E09](#e09).

**Proposed direction:** Authorize every contributing operand/result and recheck persisted run access, including list routes. Apply scope consistently through derived summaries, evidence, exports and local records; validate denied-parent/allowed-derived and changed-grant cases before broader grants.

<a id="g20"></a>

### G20 — Four focus presets and entity navigation conflict with agreed UX

**Severity: High. Business impact:** Users navigate available technical/domain features instead of their responsibility-to-decision chain; staff and managers receive inadequate distinct work surfaces.

**Implementation evidence and source/software distinction:** work-queue defines Whole business, Venue, Kitchen and FOH with title/department defaults. nav.ts exposes global Workspace/Operations/Performance/Intelligence/Administration groups and dedicated Reviews/Flow lab/data surfaces; sidebar renders NAV_GROUPS without grant filtering. Home defaults to trends, KPIs, attention and ingestion activity and says “verified across every source”. There is no equal unranked Weekly review/Operational overview choice or three-context home structure. Administration collapse is helpful but incomplete; collapse can reopen on source failure. Evidence: [E17](#e17), [E25](#e25), [E28](#e28).

**Proposed direction:** Use the agreed FOH/BOH/Upper management contexts with duty/grant-specific content; preserve the unranked upper-management alternatives and explicit personal preference. Make business question, caveat, permitted response and feedback primary; retain diagnostics as contextual/secondary authorised work.

<a id="g21"></a>

### G21 — My work and operative overrides are not local follow-up/proposal workflows

**Severity: High. Business impact:** Issues lack accepted ownership, partial/reopened history and verified outcomes; a local value can become reporting truth before the proposed-correction review contract is met.

**Implementation evidence and source/software distinction:** My work is a derived signal queue, not persisted follow-up. It sorts hardcoded severities (source failure high; product conflict medium) and has no owner/acceptance/due/verification lifecycle. InvoiceNoteService stores extracted PDF notes, overwriting changed text; it is not authored local commentary. InventoryOverrideService immediately saves an override and recomputes the resolved purchases; ReservationProjector substitutes manual covers. Actor/reason/history fragments exist, but no separate proposed/approved/hypothetical result states or decision-to-KPI loop. Evidence: [E18](#e18), [E19](#e19).

**Proposed direction:** Retain source facts and proposed local assertions separately, with explicit review and hypothetical labelling. Establish permitted ownership, due/escalation, partial/cancelled/reopened and evidence-backed closure states under OD-14. Do not let presentation acknowledgment close an issue or equate local status with external execution.

<a id="g22"></a>

### G22 — Business questions are bounded by compiled daily projections

**Severity: Medium. Business impact:** Selected service/product/location/supplier/work-role questions require manual work or cannot be answered even where source detail exists.

**Implementation evidence and source/software distinction:** MetricQuery supports metric/range/grain/dimension/comparison but no value predicates. Catalogue allowlists and fixed semantic/tool methods expose daily/weekly/monthly aggregates; canonical/resolved explorer accepts entity/page/size rather than business filters. Daily resolved payment/sale-item models discard detail dimensions. Unsupported timestamp/fulfilment data is a source gap; existing raw/canonical fields hidden by query/projection/API shape are software gaps. The resolved-only invariant amplifies incomplete projection coverage; it does not inherently require lossy daily-only truth. Evidence: [E20](#e20), [E21](#e21), [E25](#e25).

**Proposed direction:** Extend governed business read contracts for validated dimensions/filters and required atomic detail, keeping fan-out, population and permission safeguards. The independent PRD does not require arbitrary SQL, unrestricted schema discovery or user-defined formulas; historical audit suggestions for those are not adopted here.

<a id="g23"></a>

### G23 — Context, availability and controls have uneven or absent coverage

**Severity: Medium. Business impact:** Useful context is disconnected from a named decision, while stockout/service/control outcomes cannot be distinguished from missing evidence.

**Implementation evidence and source/software distinction:** Reviews have canonical/classification paths and screens; rich reservation lifecycle/context exists. No Asana/article/weather ingestion or purpose/relevance linkage, availability incident/unmet-demand, fulfilment-stage observation, or applicable control/competency/incident register was found. Booking seated/finished times are not item fulfilment. No live source permission or legal applicability is established. Source-detail availability for each absent domain remains to be verified. Evidence: [E02](#e02), [E05](#e05), [E27](#e27), [E25](#e25).

**Proposed direction:** Retain relevant reviews as qualified context and connect only purpose-validated inputs. Obtain observed availability/service/control evidence without inferring stockout from zero sales, causality from sentiment or breach/pass from missing applicability. Keep conditional controls pending applicability.

<a id="g24"></a>

### G24 — Recovery, access revocation and operating quality remain unverified

**Severity: Medium. Business impact:** Users cannot yet rely on agreed time, availability, recovery, privacy, accessibility or retention outcomes merely from repository design.

**Implementation evidence and source/software distinction:** Responsive sidebar, loading/error/denial components, formatters, tests and operational docs provide groundwork. No authorised representative-user sessions, agreed task/volume/deadline/recovery bounds, accessibility conformance scope, record-class applicability/retention or current restore/load-test evidence is available. Login principals capture role/active state; effective duty changes and already-authenticated session/cache revocation need runtime tests. Demo fixtures can illustrate screens but cannot verify live source population. Evidence: [E15](#e15), [E22](#e22), [E28](#e28).

**Proposed direction:** Bind pending OD values and verify representative business tasks, interruption/recovery, fresh permission checks and denied inference under approved conditions. Preserve unknown acceptance status; do not invent SLAs, universal retention, legal duties or inaccessible-state success.

<a id="g25"></a>

### G25 — Budgets, cash exposure and opening planning have no business model

**Severity: Medium. Business impact:** The owner cannot compare approved budgets, financial commitments or qualified second-pub alternatives.

**Implementation evidence and source/software distinction:** Forecast runs and dashboard revisions are not approved budgets/targets, cost/commitment ledgers or new-site scenario versions. No expense/account scope, cash/settlement obligations, selected plan authority or new-site resource/transfer/sensitivity record exists. Accounting/bank/new-site input availability and policy remain unresolved; lack of input is not a reason to remove these business capabilities. Evidence: [E11](#e11), [E20](#e20), [E26](#e26), [E27](#e27).

**Proposed direction:** Retain separately named approved plans/targets, financial obligation/result bases and proposed-site scenarios with author/version/assumptions/gaps. Never substitute purchase totals for profit, absent budget for zero, or current-pub performance for guaranteed new-site outcomes.

## Individual independent requirement assessments

Read each entry with its linked gap(s). Those links provide the business impact, precise implementation evidence, missing-source/software distinction and proposed direction; they are part of the individual assessment. An absence label refers to the complete business contract, not every related field. A partial label does not imply its `.AC1` passed.

### CON requirements

- **[GOL-CON-001 — Read-only connected systems](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:59)** (B); **1. Fully implemented**; severity **None**. Inspected connectors collect evidence without a source-system operational write path; local overrides stay inside Goldy's. Purpose/access validation is separately assessed under ACC. Impact/evidence/direction: No gap within the bounded assessment. Implementation evidence: [E02](#e02), [E21](#e21).

- **[GOL-CON-002 — Full business coverage independent of source availability](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:65)** (B); **2. Partially implemented**; severity **High**. Implemented domains cover only a subset of the charter; absent sources and absent models still exclude relevant decisions from the application. Impact/evidence/direction: [G05](#g05), [G09](#g09), [G12](#g12), [G23](#g23), [G25](#g25).

- **[GOL-CON-003 — Human authority over decisions](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:71)** (B); **1. Fully implemented**; severity **None**. Forecast outputs and reporting tools do not publish rosters, order stock, pay workers or execute operating decisions; consequential action remains human/external within inspected paths. Impact/evidence/direction: No gap within the bounded assessment. Implementation evidence: [E11](#e11), [E21](#e21).

- **[GOL-CON-004 — Verified applicability before compliance claims](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:77)** (B); **5. Not implemented**; severity **Medium**. No applicability/criterion/validator model or explicit unresolved-applicability workflow exists; absence of a compliance feature is not verified compliance. Impact/evidence/direction: [G23](#g23).

- **[GOL-CON-005 — Current pub and opening planning scope](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:83)** (B); **2. Partially implemented**; severity **Medium**. Current-pub scope is supported without live second-pub totals; qualified opening decision evidence and scenarios are absent. Impact/evidence/direction: [G25](#g25).

- **[GOL-CON-006 — Business-first requirements and bounded work](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:89)** (B); **1. Fully implemented**; severity **None**. This is a documentation/governance constraint: the independent baseline is preserved and this inspection writes only the requested report. It is not an application feature or build-readiness claim. Impact/evidence/direction: No gap within the bounded assessment. Evidence: this report’s inspection boundary, the unchanged reference checkout and independent authority cited above.

### FR requirements

- **[GOL-FR-001 — Repeatable weekly business report](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:196)** (C); **2. Partially implemented**; severity **High**. Resolved consolidated measures and reusable dashboards exist; recurring weekly report acceptance, usable handoff and measured effort do not. Impact/evidence/direction: [G01](#g01), [G02](#g02).

- **[GOL-FR-002 — Weekly report contract and version](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:204)** (C); **2. Partially implemented**; severity **High**. Dashboard definitions/revisions are retained; report specification, prepared result revision and validated sign-off are missing. Impact/evidence/direction: [G01](#g01), [G14](#g14).

- **[GOL-FR-003 — Automated report timing and readiness](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:212)** (C); **5. Not implemented**; severity **High**. Scheduled ingestion does not prepare a due report or expose report-level deadline/readiness/failure. Impact/evidence/direction: [G01](#g01).

- **[GOL-FR-004 — Reconciliation of contributing numbers](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:220)** (C); **2. Partially implemented**; severity **High**. Sales resolution and invoice reconciliation exist; source-basis comparability, full contributor relationships and summary coverage remain incomplete. Impact/evidence/direction: [G08](#g08), [G09](#g09), [G13](#g13), [G15](#g15).

- **[GOL-FR-005 — Actionable business summaries](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:228)** (C); **2. Partially implemented**; severity **High**. Home, attention queue and Ask Goldy's surface findings; responsibility, significance, qualification and next-decision/outcome chain are incomplete. Impact/evidence/direction: [G20](#g20), [G21](#g21).

- **[GOL-FR-006 — Trace from summary to evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:236)** (C); **2. Partially implemented**; severity **High**. Daily sales drill-down and invoice PDF links exist; other measure components, retained historical inputs and reproducible aggregate-only boundaries are incomplete. Impact/evidence/direction: [G15](#g15), [G14](#g14).

- **[GOL-FR-007 — Historical product, menu and price interpretation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:246)** (C); **2. Partially implemented**; severity **High**. Products and charged prices exist; effective offering/price/category/recipe attribution and availability are incomplete. Impact/evidence/direction: [G11](#g11), [G10](#g10), [G14](#g14).

- **[GOL-FR-008 — Bookings versus attendance and covers](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:254)** (C); **3. Implemented but semantically incorrect**; severity **High**. Attended party counts exist, but covers use partySize rather than an independent actual-cover observation; visits/check allocations are absent. Impact/evidence/direction: [G04](#g04).

- **[GOL-FR-009 — Demand and capacity comparison](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:262)** (C); **2. Partially implemented**; severity **High**. Reservation service buckets and labour summaries exist; capacity, occupancy, workload/skills and nonduplicating attendance comparison do not. Impact/evidence/direction: [G04](#g04), [G12](#g12).

- **[GOL-FR-010 — Fulfilment and service outcomes](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:270)** (C); **5. Not implemented**; severity **High**. Sale lines and seated/finished reservation fields are not order/amendment/partial-fulfilment stage evidence or measured item service outcomes. Impact/evidence/direction: [G09](#g09), [G23](#g23).

- **[GOL-FR-011 — Availability and unmet-demand evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:278)** (C); **5. Not implemented**; severity **Medium**. No availability-incident or evidenced unmet-demand capability supports a stockout/lost-sales assertion. Impact/evidence/direction: [G23](#g23).

- **[GOL-FR-012 — Atomic sales and commercial exceptions](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:288)** (C); **2. Partially implemented**; severity **High**. Receipt lines, deleted orders and tenders exist; checks, modifier/adjustment allocations, fulfilment and missing-value semantics remain incomplete. Impact/evidence/direction: [G09](#g09), [G07](#g07).

- **[GOL-FR-013 — Tender allocation and unpaid balances](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:296)** (C); **2. Partially implemented**; severity **High**. Tender facts and sale numbers exist; independent applied/unapplied allocations and reconciled unpaid balances do not. Impact/evidence/direction: [G09](#g09).

- **[GOL-FR-014 — Deposits, vouchers and credits](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:304)** (CC); **2. Partially implemented**; severity **High**. OpenTable prepayment fields are canonicalised, but no liability-entry/redemption/refund balance model separates these from sales. Impact/evidence/direction: [G09](#g09).

- **[GOL-FR-015 — Cash holding reconciliation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:312)** (C); **5. Not implemented**; severity **High**. No holding/session cash observation and expected-movement reconciliation exists. Impact/evidence/direction: [G09](#g09).

- **[GOL-FR-016 — Processor-to-bank settlement explanation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:320)** (C); **5. Not implemented**; severity **High**. Tender clearing fields do not supply processor statement components, bank receipts or matched settlement explanations. Impact/evidence/direction: [G09](#g09), [G25](#g25).

- **[GOL-FR-017 — Replenishment horizon and usable stock](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:330)** (C); **5. Not implemented**; severity **High**. No usable item/location stock, outstanding accepted supply and next-delivery horizon workflow exists. Impact/evidence/direction: [G05](#g05), [G06](#g06).

- **[GOL-FR-018 — Candidate purchase quantity explanation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:338)** (C); **5. Not implemented**; severity **High**. Forecast and recipe fragments do not yield explained item/pack quantity alternatives under delivery, conversion and yield assumptions. Impact/evidence/direction: [G05](#g05), [G10](#g10), [G16](#g16).

- **[GOL-FR-019 — Supplier terms and outstanding commitments](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:346)** (C); **4. Present in raw data but not canonicalised**; severity **High**. Supplier, stock-order and reference datasets are fetched raw; dated terms and remaining/amended/cancelled commitments are not canonical business facts. Impact/evidence/direction: [G05](#g05).

- **[GOL-FR-020 — Accepted and rejected receipt evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:354)** (C); **5. Not implemented**; severity **High**. Invoices/PDF notes do not establish separately accepted/rejected physical receipt lines and stock additions. Impact/evidence/direction: [G05](#g05).

- **[GOL-FR-021 — Order, receipt, invoice and credit matching](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:362)** (C); **2. Partially implemented**; severity **High**. CSV/PDF invoice matching exists; typed order/receipt/invoice/credit many-to-many quantity/value allocations do not. Impact/evidence/direction: [G05](#g05), [G08](#g08).

- **[GOL-FR-022 — Payables and supplier payment status](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:370)** (C); **2. Partially implemented**; severity **High**. Invoice amounts/due dates exist; supplier payment/credit allocations, disputes and remaining payable status do not. Impact/evidence/direction: [G05](#g05), [G09](#g09), [G25](#g25).

- **[GOL-FR-023 — Stock by item, location and cutoff](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:380)** (C); **2. Partially implemented**; severity **High**. Canonical observations exist; no movement-explained item/location book balance or negative/incomplete-balance review exists. Impact/evidence/direction: [G06](#g06), [G05](#g05).

- **[GOL-FR-024 — Internal transfers and preparation production](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:388)** (C); **5. Not implemented**; severity **High**. No paired transfer or preparation-production legs support venue-net-zero/internal activity explanations. Impact/evidence/direction: [G06](#g06).

- **[GOL-FR-025 — Count scope, observations and recounts](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:396)** (C); **2. Partially implemented**; severity **High**. Count header/line replay exists; expected count population, true cutoff/recount semantics and movement reconciliation are incomplete. Impact/evidence/direction: [G06](#g06), [G07](#g07).

- **[GOL-FR-026 — Count variance and proposed adjustment](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:404)** (C); **5. Not implemented**; severity **High**. No matched physical-minus-book variance and independent local adjustment proposal exists; purchase overrides address a different fact. Impact/evidence/direction: [G06](#g06), [G21](#g21).

- **[GOL-FR-027 — Effective recipes, modifiers and yields](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:412)** (C); **2. Partially implemented**; severity **High**. Recipe costs, links and components exist; effective sale/modifier/yield/component application is not complete. Impact/evidence/direction: [G10](#g10), [G14](#g14).

- **[GOL-FR-028 — Theoretical usage and ingredient economics](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:420)** (C); **2. Partially implemented**; severity **High**. Source portion/component cost is retained; reproducible effective theoretical consumption and validated conversion/usable-yield economics are absent. Impact/evidence/direction: [G10](#g10), [G07](#g07).

- **[GOL-FR-029 — Known waste and disposal](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:428)** (C); **2. Partially implemented**; severity **High**. Canonical waste quantity/reason/cost exists; unknown-quantity incidents, disposal linkage, measured/estimated method and business projections are incomplete. Impact/evidence/direction: [G06](#g06), [G07](#g07).

- **[GOL-FR-030 — Residual depletion and usage variance](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:436)** (C); **5. Not implemented**; severity **High**. No compatible residual/theoretical assessment with classified movements and known-loss exclusions exists. Impact/evidence/direction: [G06](#g06), [G10](#g10).

- **[GOL-FR-031 — Availability, duties and competency evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:446)** (C); **5. Not implemented**; severity **High**. No enduring worker availability/duties/competency applicability/expiry evidence model supports rostering review. Impact/evidence/direction: [G12](#g12).

- **[GOL-FR-032 — Workload and skill coverage planning](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:454)** (C); **5. Not implemented**; severity **High**. Daily hours/covers/revenue are not product effort, competing-duty or required-skill coverage planning. Impact/evidence/direction: [G12](#g12).

- **[GOL-FR-033 — Roster versions and absence risks](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:462)** (C); **5. Not implemented**; severity **High**. No published/amended roster version, unfilled slot or absence-risk/scenario workflow exists. Impact/evidence/direction: [G12](#g12).

- **[GOL-FR-034 — Planned, actual and payable time](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:470)** (C); **2. Partially implemented**; severity **High**. Daily scheduled/actual hour fields exist; independent roster, observed work/break and payable-time contracts are absent. Impact/evidence/direction: [G12](#g12), [G07](#g07).

- **[GOL-FR-035 — Timesheet evidence and correction follow-up](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:478)** (C); **5. Not implemented**; severity **High**. No timesheet status, overlapping/missing interval and authored local correction lineage workflow exists. Impact/evidence/direction: [G12](#g12), [G21](#g21).

- **[GOL-FR-036 — Labour cost interpretation and productivity](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:486)** (C); **3. Implemented but semantically incorrect**; severity **High**. Labour totals/ratios are labelled cost/productivity without component/payable scope, and percentages use gross rather than matching net sales. Impact/evidence/direction: [G12](#g12), [G13](#g13).

- **[GOL-FR-037 — Pay obligations and settlement evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:494)** (CC); **5. Not implemented**; severity **High**. No earning/contribution obligation and allocated-payment evidence capability exists; applicability remains conditional. Impact/evidence/direction: [G12](#g12), [G25](#g25).

- **[GOL-FR-038 — Comparable financial results](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:504)** (C); **3. Implemented but semantically incorrect**; severity **High**. Purchases are labelled COGS/food cost; valuation, other expenses and matched financial recognition do not support complete profit. Impact/evidence/direction: [G03](#g03), [G13](#g13), [G25](#g25).

- **[GOL-FR-039 — Budgets, targets and commitments](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:512)** (C); **5. Not implemented**; severity **Medium**. No approved budget/target, commitment exposure and separate forecast-plan comparison model exists. Impact/evidence/direction: [G25](#g25).

- **[GOL-FR-040 — Comparable reporting populations](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:520)** (C); **2. Partially implemented**; severity **High**. Period comparisons and shared-day series exist; agreed trading duration, effective categories, financial bases and summary coverage are incomplete. Impact/evidence/direction: [G13](#g13), [G14](#g14), [G22](#g22).

- **[GOL-FR-041 — Revenue forecast](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:530)** (C); **2. Partially implemented**; severity **High**. Persisted gross-sales forecast, issue/horizon and quantiles exist; original input coverage, basis and decision eligibility remain incomplete. Impact/evidence/direction: [G17](#g17), [G14](#g14).

- **[GOL-FR-042 — Specific-product sales forecast](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:538)** (C); **5. Not implemented**; severity **High**. No selected-product/variant predicate or forecast UX exists despite available product metrics. Impact/evidence/direction: [G16](#g16).

- **[GOL-FR-043 — Forecast and scenario assumptions](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:546)** (C); **2. Partially implemented**; severity **Medium**. Forecast request metadata exists; authored assumptions, scenario versions/alternatives and explicit local changes do not. Impact/evidence/direction: [G17](#g17), [G25](#g25).

- **[GOL-FR-044 — Forecast outcome evaluation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:554)** (C); **2. Partially implemented**; severity **Medium**. Stored issued quantiles are compared with later actuals; error denominator guards exist, but retained definitions/input coverage and eligible evaluation disclosure are incomplete. Impact/evidence/direction: [G17](#g17).

- **[GOL-FR-045 — Second-pub decision evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:562)** (C); **5. Not implemented**; severity **Medium**. No proposed-site evidence/scenario/transfer-assumption/sensitivity workflow exists. Impact/evidence/direction: [G25](#g25).

- **[GOL-FR-046 — Purpose-bound external and internal context](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:572)** (C); **2. Partially implemented**; severity **Medium**. Canonical reviews and rich booking context exist; purpose-bound relevance, article/Asana/weather coverage and verified-outcome distinctions are incomplete. Impact/evidence/direction: [G23](#g23).

- **[GOL-FR-047 — Applicable operational control evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:580)** (CC); **5. Not implemented**; severity **Medium**. No applicable control/licence/competency/incident and corrective-evidence register exists; applicability itself is unverified. Impact/evidence/direction: [G23](#g23).

- **[GOL-FR-048 — Prioritised exception review](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:588)** (C); **2. Partially implemented**; severity **High**. A source/reconciliation/invoice queue exists; owners, decision impact, agreed prioritisation and unresolved scope/age are incomplete. Impact/evidence/direction: [G21](#g21), [G20](#g20).

- **[GOL-FR-049 — Local annotation and correction proposal](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:598)** (C); **3. Implemented but semantically incorrect**; severity **High**. Operative manual overrides can replace resolved business values immediately; source facts persist, but proposal/review/hypothetical states are not the required correction workflow. Impact/evidence/direction: [G21](#g21).

- **[GOL-FR-050 — Follow-up responsibility and lifecycle](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:606)** (C); **5. Not implemented**; severity **High**. No persisted local ownership, due/escalation, partial/reopened/cancelled and verified-closure lifecycle exists. Impact/evidence/direction: [G21](#g21).

- **[GOL-FR-051 — CSV export of permitted business information](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:616)** (C); **5. Not implemented**; severity **High**. CSV handling is inbound; authorised selected-fact/result CSV output is absent. Impact/evidence/direction: [G02](#g02).

- **[GOL-FR-052 — Readable formatted Excel reports](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:624)** (C); **5. Not implemented**; severity **High**. No formatted Excel reporting/export workflow exists. Impact/evidence/direction: [G02](#g02).

- **[GOL-FR-053 — Export completeness, provenance and safe use](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:632)** (C); **5. Not implemented**; severity **High**. No business export artefact preserves scope, missingness, provenance, omissions, grants and safe spreadsheet text. Impact/evidence/direction: [G02](#g02), [G18](#g18).

- **[GOL-FR-054 — Business questions, filters and comparisons](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:641)** (C); **2. Partially implemented**; severity **High**. Compiled metric queries/rankings and some source/evidence filters exist; validated arbitrary business scopes, value predicates and complete authorised comparisons do not. Impact/evidence/direction: [G22](#g22), [G18](#g18).

### SEM requirements

- **[GOL-SEM-001 — Identity and duplicate evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:653)** (B); **3. Implemented but semantically incorrect**; severity **High**. Some source-fact idempotency exists, but invoice-number and normalised-name identities can merge distinct business facts. Impact/evidence/direction: [G08](#g08).

- **[GOL-SEM-002 — Temporal meaning and attribution](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:661)** (B); **3. Implemented but semantically incorrect**; severity **High**. Revision timestamps exist; several valid-time values are receipt time, and trading/pay/forecast calendars are not consistently policy-bound. Impact/evidence/direction: [G14](#g14).

- **[GOL-SEM-003 — Quantity, currency, tax and cost bases](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:669)** (B); **3. Implemented but semantically incorrect**; severity **High**. Currency/tax/UOM fragments exist; guessed quantity/zero cost, cross-item blended units and purchases-as-COGS violate the required bases. Impact/evidence/direction: [G07](#g07), [G10](#g10), [G03](#g03).

- **[GOL-SEM-004 — Known zero and absence states](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:677)** (B); **3. Implemented but semantically incorrect**; severity **High**. Metric null/zero guards exist, but parser fallbacks fabricate zero/one and lose material absence states. Impact/evidence/direction: [G07](#g07).

- **[GOL-SEM-005 — History, correction and authority](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:685)** (B); **2. Partially implemented**; severity **High**. Canonical supersession and some override/rule audits retain history; original published results, proposal authority and reason/lineage are incomplete. Impact/evidence/direction: [G14](#g14), [G21](#g21).

- **[GOL-SEM-006 — Derived measures and uncertainty](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:693)** (B); **2. Partially implemented**; severity **High**. Catalogue definitions, notices and quantiles exist; snapshots, eligible populations, scenario/final states and uncertainty lineage are incomplete. Impact/evidence/direction: [G15](#g15), [G13](#g13), [G17](#g17).

- **[GOL-SEM-007 — Completeness by decision population](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:701)** (B); **2. Partially implemented**; severity **High**. Missing dates/shared-day notices exist; expected business population and downstream summary qualification remain incomplete. Impact/evidence/direction: [G15](#g15), [G13](#g13).

- **[GOL-SEM-008 — Freshness and decision eligibility](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:709)** (B); **2. Partially implemented**; severity **High**. Trust/ingestion timestamps and configured thresholds exist; evidenced coverage and validated decision-specific eligibility are incomplete. Impact/evidence/direction: [G15](#g15), [G06](#g06).

- **[GOL-SEM-009 — Canonical meaning before source mapping](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:717)** (B); **3. Implemented but semantically incorrect**; severity **High**. Source concepts are mapped, but purchases/COGS, party size/covers and positive-price/menu availability are incorrectly equated. Impact/evidence/direction: [G03](#g03), [G04](#g04), [G11](#g11).

- **[GOL-SEM-010 — Single recognition and reconciled relationships](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:725)** (B); **2. Partially implemented**; severity **High**. Resolved-source selection and idempotency prevent some duplication; allocations, transfers, losses and estimate/final replacement relationships are missing. Impact/evidence/direction: [G08](#g08), [G09](#g09), [G06](#g06).

### DR requirements

- **[GOL-DR-001 — Venue](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:739)** (C); **2. Partially implemented**; severity **High**. Single venue timezone configuration exists; enduring venue identity/operator/jurisdiction/history contracts are incomplete. Impact/evidence/direction: [G14](#g14), [G25](#g25).

- **[GOL-DR-002 — Operational location](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:749)** (C); **2. Partially implemented**; severity **High**. Area/table/department labels exist; canonical location hierarchy, capacities and stockholding identity do not. Impact/evidence/direction: [G05](#g05), [G04](#g04).

- **[GOL-DR-003 — Reporting period](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:759)** (C); **2. Partially implemented**; severity **High**. Date ranges and lunch/dinner classification exist; separate policy/versioned operational/pay/accounting periods do not. Impact/evidence/direction: [G14](#g14).

- **[GOL-DR-004 — Product](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:769)** (C); **2. Partially implemented**; severity **High**. Canonical product IDs exist; product-day totals still key names, and sale unit/variant/effective category links are incomplete. Impact/evidence/direction: [G08](#g08), [G11](#g11).

- **[GOL-DR-005 — Menu offering version](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:779)** (C); **4. Present in raw data but not canonicalised**; severity **High**. OpenTable menu intake is raw-only; product catalogue/derived menu state is not a canonical effective offering context. Impact/evidence/direction: [G11](#g11).

- **[GOL-DR-006 — Price term](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:789)** (C); **2. Partially implemented**; severity **High**. Product sell price and charged line price exist; independently eligible dated/contextual price terms are absent. Impact/evidence/direction: [G11](#g11), [G14](#g14).

- **[GOL-DR-007 — Customer check](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:799)** (C); **4. Present in raw data but not canonicalised**; severity **High**. All-sales raw records preserve check identifiers/totals but collapse to daily canonical sales; no check lifecycle/split/merge entity exists. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-008 — Sale line](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:809)** (C); **2. Partially implemented**; severity **High**. Canonical receipt-line identity and amounts/quantities exist; parent check, status partitions, fulfilment, units and unknown handling are incomplete. Impact/evidence/direction: [G09](#g09), [G07](#g07).

- **[GOL-DR-009 — Modifier selection](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:819)** (C); **5. Not implemented**; severity **High**. is_modifier on a catalogue product does not identify a selected modifier occurrence/target/effect on a sale line. Impact/evidence/direction: [G09](#g09), [G10](#g10).

- **[GOL-DR-010 — Commercial adjustment](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:829)** (C); **2. Partially implemented**; severity **High**. Deleted-order and source tax/discount fragments do not provide signed check/line adjustment allocations with independent reversal/approval basis. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-011 — Money movement](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:839)** (C); **2. Partially implemented**; severity **High**. Canonical tender facts exist; generic transfer/refund/supplier/pay/settlement direction/status and endpoint contracts do not. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-012 — Payment allocation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:849)** (C); **5. Not implemented**; severity **High**. saleNumber on payment rows is not an independent partial/many-target payment allocation with balance invariants. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-013 — Customer liability entry](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:859)** (CC); **2. Partially implemented**; severity **High**. Reservation prepayment amounts/status exist; deposit/voucher/credit entry and linked redemption balance contracts do not. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-014 — Cash reconciliation observation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:869)** (C); **5. Not implemented**; severity **High**. No independent cash holding count/session/cutoff with expected-movement basis exists. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-015 — Settlement component](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:879)** (C); **5. Not implemented**; severity **High**. No provider settlement-group/component and bank-allocation model exists. Impact/evidence/direction: [G09](#g09).

- **[GOL-DR-016 — Supplier](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:889)** (C); **4. Present in raw data but not canonicalised**; severity **High**. Supplier master is fetched raw; invoice supplierName does not supply enduring qualified counterparty identity/history. Impact/evidence/direction: [G05](#g05), [G08](#g08).

- **[GOL-DR-017 — Supplier item and pack term](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:899)** (C); **4. Present in raw data but not canonicalised**; severity **High**. Reference measurement/conversion and stock/supplier datasets are raw-only; PDF pack fragments are not effective supplier-item conversion terms. Impact/evidence/direction: [G05](#g05), [G07](#g07).

- **[GOL-DR-018 — Purchase document](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:909)** (C); **2. Partially implemented**; severity **High**. Invoice header exists; separately typed order/receipt/credit, issuer-qualified identity and status do not. Impact/evidence/direction: [G05](#g05), [G08](#g08).

- **[GOL-DR-019 — Purchase document line](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:919)** (C); **2. Partially implemented**; severity **High**. Invoice line and enrichment exist; typed order/receipt disposition and unknown price/quantity semantics are incomplete. Impact/evidence/direction: [G05](#g05), [G07](#g07).

- **[GOL-DR-020 — Purchase match allocation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:929)** (C); **5. Not implemented**; severity **High**. CSV/PDF enrichment match is not a many-to-many typed purchase-document/line allocation. Impact/evidence/direction: [G05](#g05).

- **[GOL-DR-021 — Stock item](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:939)** (C); **4. Present in raw data but not canonicalised**; severity **High**. Raw stock master exists; stock IDs on other entities do not provide an enduring base-unit/interchangeability stock-item contract. Impact/evidence/direction: [G05](#g05).

- **[GOL-DR-022 — Recipe version](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:949)** (C); **2. Partially implemented**; severity **High**. Recipe-cost row and portion fields exist; positive output/effective version, yield completeness and attribution are incomplete. Impact/evidence/direction: [G10](#g10), [G14](#g14).

- **[GOL-DR-023 — Recipe component](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:959)** (C); **2. Partially implemented**; severity **High**. Ingredients/sub-recipe structure exists; validated gross/net conversions, effective stock mapping and dependency/missing-link qualifications are incomplete. Impact/evidence/direction: [G10](#g10).

- **[GOL-DR-024 — Stock movement](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:969)** (C); **5. Not implemented**; severity **High**. No originating-fact item/location movement legs, counterpart groups or adjustment basis exists. Impact/evidence/direction: [G06](#g06).

- **[GOL-DR-025 — Wastage observation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:979)** (C); **2. Partially implemented**; severity **High**. Waste row exists; scope/time precision, measured/estimated method, optional unknown quantity and disposal linkage are incomplete. Impact/evidence/direction: [G06](#g06), [G07](#g07).

- **[GOL-DR-026 — Stock count session](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:989)** (C); **2. Partially implemented**; severity **High**. Stocktake header has partial/locked flags; intended population, authoritative cutoff and full completion contract are absent. Impact/evidence/direction: [G06](#g06).

- **[GOL-DR-027 — Stock count observation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:999)** (C); **3. Implemented but semantically incorrect**; severity **High**. Count lines retain source IDs/area/UOM, but missing/malformed qtyCount becomes zero and true cutoff/recount basis is incomplete. Impact/evidence/direction: [G07](#g07), [G06](#g06).

- **[GOL-DR-028 — Consumption assessment](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1009)** (C); **5. Not implemented**; severity **High**. No versioned interval/method consumption assessment with eligible inputs/exclusions exists. Impact/evidence/direction: [G06](#g06), [G10](#g10).

- **[GOL-DR-029 — Employee](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1019)** (C); **5. Not implemented**; severity **High**. staffRef/name and login accounts do not provide a canonical enduring employee with employment/work relationships. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-030 — Position and work role](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1029)** (C); **5. Not implemented**; severity **High**. Software department/seniority codes are not duties/work-role definitions or delegation/competency evidence. Impact/evidence/direction: [G12](#g12), [G18](#g18).

- **[GOL-DR-031 — Employment assignment](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1039)** (C); **5. Not implemented**; severity **High**. No employer engagement/terms/classification version distinguishes concurrent or re-engaged employment. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-032 — Roster version](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1049)** (C); **5. Not implemented**; severity **High**. Daily scheduled hours and shift scaffold do not identify published/amended roster versions. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-033 — Planned shift](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1059)** (C); **2. Partially implemented**; severity **High**. Scaffold shiftStart/end/staffRef exists; roster/role/slot/break/allocation contract and production ingestion do not. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-034 — Timesheet](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1069)** (C); **5. Not implemented**; severity **High**. No worker statement/version/status with observed intervals, approval and deduplication exists. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-035 — Actual work and break interval](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1079)** (C); **2. Partially implemented**; severity **High**. Labour rows allow shift endpoints; independent actual work/break intervals, unknown ends and nonduplicating allocations are incomplete. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-036 — Labour cost and entitlement component](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1089)** (C); **2. Partially implemented**; severity **High**. Daily scheduled/actual cost values exist; engagement/earning-period component revisions and entitlement/payment lineage do not. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-037 — Reservation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1099)** (C); **2. Partially implemented**; severity **High**. Rich canonical booking with source status/history exists; unknown party-size support and validated temporal/visit/eligibility semantics are incomplete. Impact/evidence/direction: [G04](#g04), [G14](#g14).

- **[GOL-DR-038 — Visit and actual covers](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1109)** (C); **3. Implemented but semantically incorrect**; severity **High**. Attended reservations are summarised as visits/covers without independent observed count or table/episode identity. Impact/evidence/direction: [G04](#g04).

- **[GOL-DR-039 — Service allocation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1119)** (C); **2. Partially implemented**; severity **High**. OpenTable pos_check_ids and POS amounts exist as attributes; validated many-to-many visit/check/line allocation/coverage does not. Impact/evidence/direction: [G04](#g04), [G09](#g09).

- **[GOL-DR-040 — Metric definition](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1129)** (C); **2. Partially implemented**; severity **High**. Compiled metric definitions/version 1 exist; effective validated population/time/cost/missing-rule history is incomplete. Impact/evidence/direction: [G15](#g15), [G13](#g13).

- **[GOL-DR-041 — Measurement](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1139)** (C); **2. Partially implemented**; severity **High**. Metric result/provenance exists; retained input/result revisions and complete unavailability/coverage/uncertainty contracts do not. Impact/evidence/direction: [G15](#g15), [G14](#g14).

- **[GOL-DR-042 — Plan and target value](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1149)** (C); **2. Partially implemented**; severity **Medium**. Persisted forecast runs exist; approved budget/target, scenario authority, scoped assumptions and comparable definition versions do not. Impact/evidence/direction: [G17](#g17), [G25](#g25).

- **[GOL-DR-043 — Operational control evidence](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1159)** (CC); **5. Not implemented**; severity **Medium**. No applicable control/incident/qualification subject and criterion/version evidence contract exists. Impact/evidence/direction: [G23](#g23).

- **[GOL-DR-044 — Workload observation or assumption](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1169)** (C); **5. Not implemented**; severity **High**. No observed/estimated product/task/preparation effort and competing-duty workload assertion exists. Impact/evidence/direction: [G12](#g12).

- **[GOL-DR-045 — Contextual evidence item](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1179)** (C); **2. Partially implemented**; severity **Medium**. Reviews/reservation context exist; relevance/purpose, broader contextual types and verified external-work linkage are incomplete. Impact/evidence/direction: [G23](#g23).

- **[GOL-DR-046 — Local annotation and correction proposal](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1189)** (C); **3. Implemented but semantically incorrect**; severity **High**. Extracted invoice notes and operative overrides do not meet authored proposal/review states and independent hypothetical calculation semantics. Impact/evidence/direction: [G21](#g21).

- **[GOL-DR-047 — Local follow-up and decision record](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1199)** (C); **5. Not implemented**; severity **High**. No persisted local decision/follow-up ownership and evidence-backed lifecycle exists. Impact/evidence/direction: [G21](#g21).

- **[GOL-DR-048 — Report specification and report instance](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1209)** (C); **5. Not implemented**; severity **High**. Dashboard query documents are not report specifications plus prepared instance/result revisions with readiness/coverage/sign-off. Impact/evidence/direction: [G01](#g01).

### MET requirements

- **[GOL-MET-001 — Net sales and sales mix](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1225)** (C); **2. Partially implemented**; severity **High**. Gross-minus-GST and product amount/quantity metrics exist; eligible adjustment/liability/tip/surcharge rules and mix/unit coverage are incomplete. Impact/evidence/direction: [G09](#g09), [G08](#g08), [G13](#g13).

- **[GOL-MET-002 — Spend per actual cover and average check](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1233)** (C); **3. Implemented but semantically incorrect**; severity **High**. Gross venue sales divided by party-size-derived covers is not attributed net spend per actual cover; independent average-check population is absent. Impact/evidence/direction: [G04](#g04).

- **[GOL-MET-003 — Service duration distribution](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1241)** (C); **5. Not implemented**; severity **High**. Reservation duration is not observed order-to-fulfilment duration; no eligible stage distribution exists. Impact/evidence/direction: [G23](#g23), [G09](#g09).

- **[GOL-MET-004 — Reservation no-show rate](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1249)** (C); **3. Implemented but semantically incorrect**; severity **High**. No-shows / all reservation rows includes unvalidated cancellation/walk-in eligibility; source status is preserved but denominator semantics are wrong. Impact/evidence/direction: [G04](#g04).

- **[GOL-MET-005 — Stock balance and count variance](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1257)** (C); **2. Partially implemented**; severity **High**. Count observations exist; movement-explained book stock and matched physical count variance do not. Impact/evidence/direction: [G06](#g06), [G07](#g07).

- **[GOL-MET-006 — Usage variance and recorded waste](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1265)** (C); **2. Partially implemented**; severity **High**. Waste observations exist, but business waste projection and compatible theoretical/residual usage assessment are absent. Impact/evidence/direction: [G06](#g06), [G10](#g10).

- **[GOL-MET-007 — COGS, gross profit and gross margin](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1273)** (C); **3. Implemented but semantically incorrect**; severity **High**. Invoice purchases/food-cost ratios are implemented under a COGS label without consumption valuation and matched net revenue. Impact/evidence/direction: [G03](#g03).

- **[GOL-MET-008 — Labour cost and productivity](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1281)** (C); **3. Implemented but semantically incorrect**; severity **High**. Daily cost/hour ratios exist; cost component/payable scope and matching net-sales productivity contract are not satisfied. Impact/evidence/direction: [G12](#g12), [G13](#g13).

- **[GOL-MET-009 — Prime cost](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1289)** (C); **5. Not implemented**; severity **High**. No independently defined COGS-plus-labour prime-cost measure with complete component basis exists. Impact/evidence/direction: [G03](#g03), [G12](#g12).

- **[GOL-MET-010 — Budget variance and forecast error](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1297)** (C); **2. Partially implemented**; severity **Medium**. MAE/MAPE on retained forecasts exists; approved budget variance and agreed weighted eligible error basis/disclosure are incomplete. Impact/evidence/direction: [G17](#g17), [G25](#g25).

- **[GOL-MET-011 — Reconciliation exception count, amount and age](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1305)** (C); **2. Partially implemented**; severity **High**. Open source/reconciliation/invoice issues exist; business-age, remaining compatible amount and partial lifecycle history are incomplete. Impact/evidence/direction: [G21](#g21), [G15](#g15).

- **[GOL-MET-012 — Applicable control and verified action outcomes](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1313)** (C); **5. Not implemented**; severity **High**. No applicable-control or evidence-verified action outcome calculation exists. Impact/evidence/direction: [G23](#g23), [G21](#g21).

- **[GOL-MET-013 — Forecast revenue and product-sales outputs](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1321)** (C); **2. Partially implemented**; severity **High**. Revenue quantiles exist; selected product quantities/money, retained coverage and price/availability assumptions are absent. Impact/evidence/direction: [G16](#g16), [G17](#g17).

- **[GOL-MET-014 — Availability, stockout and supplier outcomes](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1329)** (C); **5. Not implemented**; severity **High**. No observed stockout/availability or promised-versus-accepted supplier timeliness/in-full measure exists. Impact/evidence/direction: [G05](#g05), [G23](#g23).

- **[GOL-MET-015 — Report automation outcome](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1337)** (C); **5. Not implemented**; severity **High**. No observed report assembly/checking/rework effort and agreed deadline/readiness outcome measurement exists. Impact/evidence/direction: [G01](#g01).

- **[GOL-MET-016 — Operating profit and cash commitments](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1345)** (C); **5. Not implemented**; severity **High**. No validated operating expense/profit, cash position, due-payable/liability or future-commitment measurement contract exists. Impact/evidence/direction: [G25](#g25), [G09](#g09).

### ACC requirements

- **[GOL-ACC-001 — Validate explicit grants](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1359)** (B); **2. Partially implemented**; severity **High**. Explicit role/resource rows fail closed, but person/purpose/scope/operation/effective/approval grant evidence is absent. Impact/evidence/direction: [G18](#g18).

- **[GOL-ACC-002 — Enforce scope for every view and export](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1367)** (B); **2. Partially implemented**; severity **High**. Server resource checks and metric render checks exist; operand, row/worker, inference, local-record and export scope are incomplete. Impact/evidence/direction: [G18](#g18), [G19](#g19).

- **[GOL-ACC-003 — Sensitive worker/customer information](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1375)** (B); **2. Partially implemented**; severity **High**. Some owner cost gating exists, but validated minimum collection/purpose and sensitive field/row policies are not encoded. Impact/evidence/direction: [G18](#g18), [G23](#g23).

- **[GOL-ACC-004 — Operational responsibility and approval separation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1383)** (C); **5. Not implemented**; severity **High**. No policy-driven delegated local verifier/approver and independent-verification workflow exists. Impact/evidence/direction: [G21](#g21), [G18](#g18).

- **[GOL-ACC-005 — Changed duties and revoked access](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1391)** (B); **6. Cannot verify from repository evidence**; severity **High**. Login active-state and role/resource checks do not verify effective delegation change, existing session/cache or new export denial after revocation. Impact/evidence/direction: [G24](#g24), [G18](#g18).

- **[GOL-ACC-006 — External and supporting participants](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1399)** (CC); **5. Not implemented**; severity **High**. No purpose/duration-limited adviser/supporting-function grant and sharing model exists. Impact/evidence/direction: [G18](#g18).

- **[GOL-ACC-007 — Accountability for sensitive local actions](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1407)** (B); **2. Partially implemented**; severity **High**. Override/rule/dashboard actors/history exist; sensitive export, report-definition and purpose/scoped grant accountability is incomplete. Impact/evidence/direction: [G19](#g19), [G01](#g01), [G02](#g02).

- **[GOL-ACC-008 — Purpose-bound source access and sharing](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1415)** (B); **2. Partially implemented**; severity **High**. Token-gated feeds and source adapters exist; purpose-bounded collection and distribution evidence are not represented comprehensively. Impact/evidence/direction: [G18](#g18), [G23](#g23).

### NFR requirements

- **[GOL-NFR-001 — Decision-speed and report deadlines](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1444)** (C); **6. Cannot verify from repository evidence**; severity **High**. No stakeholder-approved request/report deadlines and business volumes or measured acceptance results are available. Impact/evidence/direction: [G24](#g24), [G01](#g01).

- **[GOL-NFR-002 — Business-time availability and degraded use](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1452)** (C); **6. Cannot verify from repository evidence**; severity **Medium**. Error/partial states exist, but approved operating window/disruption/support and qualified degraded-use acceptance cannot be verified. Impact/evidence/direction: [G24](#g24).

- **[GOL-NFR-003 — Recoverable local evidence and report history](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1460)** (C); **6. Cannot verify from repository evidence**; severity **High**. Recovery bounds/exercise evidence is unavailable; required report/local-record classes are also absent. Impact/evidence/direction: [G24](#g24), [G01](#g01), [G21](#g21).

- **[GOL-NFR-004 — Reproducibility and numerical reconciliation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1468)** (B); **2. Partially implemented**; severity **High**. Resolved recomputation and rounding exist; complete input/result versions, declared tolerances and cross-surface reproducibility are incomplete. Impact/evidence/direction: [G14](#g14), [G15](#g15), [G13](#g13).

- **[GOL-NFR-005 — Confidentiality and authorised use](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1476)** (B); **2. Partially implemented**; severity **High**. Explicit server checks protect some resources; full purpose/scope/inference/export/local-record confidentiality is not satisfied. Impact/evidence/direction: [G18](#g18), [G19](#g19).

- **[GOL-NFR-006 — Usability for operational work](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1484)** (C); **6. Cannot verify from repository evidence**; severity **High**. No representative user task success/time/assistance evidence or approved bounds exist; required workflows also have structural gaps. Impact/evidence/direction: [G24](#g24), [G20](#g20).

- **[GOL-NFR-007 — Accessible interpretation and use](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1492)** (C); **6. Cannot verify from repository evidence**; severity **Medium**. Accessible component groundwork exists; approved conditions and end-to-end interpretation/conformance evidence are unavailable. Impact/evidence/direction: [G24](#g24).

- **[GOL-NFR-008 — Business volume and growth envelope](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1500)** (C); **6. Cannot verify from repository evidence**; severity **Medium**. Pagination exists; complete output/reconciliation under an approved history/concurrency/export envelope is unverified. Impact/evidence/direction: [G24](#g24), [G22](#g22).

- **[GOL-NFR-009 — Record-class retention and lawful disposition](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1508)** (CC); **6. Cannot verify from repository evidence**; severity **Medium**. No verified record-class applicability, retention/hold/disposition policy and acceptance evidence is available. Impact/evidence/direction: [G24](#g24).

- **[GOL-NFR-010 — Business-readable dates, units and formats](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1516)** (B); **2. Partially implemented**; severity **High**. Shared formatting/venue timezone exists; unknown states, unit/tax bases and overnight reporting/forecast date consistency remain incomplete. Impact/evidence/direction: [G14](#g14), [G07](#g07), [G13](#g13).

- **[GOL-NFR-011 — Interpretability of forecast uncertainty](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1524)** (C); **2. Partially implemented**; severity **Medium**. Quantile chart and forecast/run labels exist; source coverage, assumption meaning and user reliance interpretation are incomplete and unvalidated. Impact/evidence/direction: [G17](#g17).

- **[GOL-NFR-012 — Complete and usable report/export artefacts](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/prds/prd-goldys-requirements-2026-10-14/prd.md:1532)** (C); **5. Not implemented**; severity **High**. Required CSV/Excel artefacts do not exist; recipient-tool opening, completeness and safe-text acceptance cannot be met. Impact/evidence/direction: [G02](#g02).

## Individual UX assessments

GUX status is inherited from the agreed/proposed/unresolved distinctions in EXPERIENCE.md. The three contexts, responsive web and unranked management choice are agreed; detailed compositions, duty/KPI assignments and numerical targets remain proposed. DESIGN.md intentionally leaves palette, fonts, tokens and breakpoints unselected: the existing application choosing its own theme is **not** a gap against an unspecified visual identity. Its presentation obligations are assessed through GUX-10/12/16/17 and NFR-007/010.

- **[GUX-01 — Operational value test](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:41)**; **2. Partially implemented**; severity **High**. Some summaries and queues exist; navigation prominence and a full decision-to-action-to-outcome chain are not justified by validated responsibility. Impact/evidence/direction: [G20](#g20), [G21](#g21).

- **[GUX-02 — Upper management](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:51)**; **2. Partially implemented**; severity **High**. Business/owner focus exists; owner/GM reporting and opening evidence are incomplete and duties/grants are title-based. Impact/evidence/direction: [G01](#g01), [G18](#g18), [G25](#g25).

- **[GUX-03 — Front of house](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:66)**; **2. Partially implemented**; severity **High**. FOH focus shortcuts exist; assigned service, roster/order evidence and manager-versus-staff privacy/work distinctions are incomplete. Impact/evidence/direction: [G04](#g04), [G05](#g05), [G12](#g12), [G18](#g18).

- **[GUX-04 — Back of house](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:79)**; **2. Partially implemented**; severity **High**. Kitchen focus and recipe/invoice screens exist; BOH head/sub-head/staff duties and usable stock/coverage decisions are not separately supported. Impact/evidence/direction: [G05](#g05), [G10](#g10), [G12](#g12), [G18](#g18).

- **[GUX-05 — Three contexts, shared decision surfaces](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:94)**; **3. Implemented but semantically incorrect**; severity **High**. The agreed three-context information architecture is replaced by four focus presets and global domain/technical groups. Impact/evidence/direction: [G20](#g20).

- **[GUX-06 — Upper management: two unranked alternatives](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:140)**; **5. Not implemented**; severity **High**. No equal unranked Weekly review versus Operational overview entry or explicit Use this as my home choice exists. Impact/evidence/direction: [G01](#g01), [G20](#g20).

- **[GUX-07 — Front of house home](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:150)**; **2. Partially implemented**; severity **High**. FOH preference/filtering exists; decision-specific service/coverage/supply/follow-up/outcome home modules and staff differences are absent. Impact/evidence/direction: [G20](#g20), [G21](#g21).

- **[GUX-08 — Back of house home](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:156)**; **2. Partially implemented**; severity **High**. Kitchen preference/filtering exists; preparation/usable supply/coverage/follow-up home and ordering-only sub-head role treatment are absent. Impact/evidence/direction: [G20](#g20), [G21](#g21).

- **[GUX-09 — Dispositions by operational value](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:164)**; **3. Implemented but semantically incorrect**; severity **High**. Dedicated entity/reviews/flow-lab/technical destinations reflect available features rather than the specified operational-value dispositions. Impact/evidence/direction: [G20](#g20).

- **[GUX-10 — Shared behavioural vocabulary](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:201)**; **2. Partially implemented**; severity **High**. Shared tables, charts, states and scope controls exist; definition/population/revision, proposal and export behavioural vocabulary is incomplete. Impact/evidence/direction: [G15](#g15), [G02](#g02), [G07](#g07).

- **[GUX-11 — Decision-to-feedback loop](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:225)**; **5. Not implemented**; severity **High**. No retained local decision, accepted ownership, verified closure and matched later-KPI feedback loop exists. Impact/evidence/direction: [G21](#g21).

- **[GUX-12 — Surface-wide state contract](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:235)**; **2. Partially implemented**; severity **High**. Loading/error/denial/null states exist; missing/partial/revision/concurrent-edit/last-known and complete business-specific meanings are incomplete. Impact/evidence/direction: [G07](#g07), [G13](#g13), [G24](#g24).

- **[GUX-13 — Warnings and interruptions](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:265)**; **2. Partially implemented**; severity **High**. Inline errors and attention warnings exist; impact/owner/reliance qualification and agreed urgency rules do not consistently govern warnings. Impact/evidence/direction: [G15](#g15), [G20](#g20), [G21](#g21).

- **[GUX-14 — Predictable exploration and local action](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:275)**; **2. Partially implemented**; severity **High**. Labelled navigation, tables and dialogs exist; retained business scope/revision, local Save/Cancel/recovery and exports are incomplete. Impact/evidence/direction: [G22](#g22), [G21](#g21), [G02](#g02).

- **[GUX-15 — Permissions versus personalisation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:283)**; **3. Implemented but semantically incorrect**; severity **High**. Personal focus does not itself widen backend grants, but title-derived defaults, hardcoded owner visibility and incomplete scope/effective grants violate the required separation. Impact/evidence/direction: [G18](#g18), [G20](#g20).

- **[GUX-16 — Usable interpretation and operation](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:293)**; **6. Cannot verify from repository evidence**; severity **Medium**. Responsive/semantic/labelled component groundwork exists; keyboard/assistive/zoom/phone journey completion and critical-caveat interpretation are not verified. Impact/evidence/direction: [G24](#g24).

- **[GUX-17 — Website reporting and phone quick checks](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:308)**; **2. Partially implemented**; severity **High**. Responsive web/sidebar layout is implemented; the specified phone note/follow-up and report/evidence tasks are not all available or tested. Impact/evidence/direction: [G24](#g24), [G20](#g20).

- **[GUX-18 — Acceptance of the experience](/Users/stirlingdonaldson/001-PROJECTS/goldys-requirements/deliverables/ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md:429)**; **6. Cannot verify from repository evidence**; severity **Medium**. Repository tests cannot establish representative stakeholder acceptance of UX-AC01–10; adopted bounds and role-holder validation are absent. Impact/evidence/direction: [G24](#g24).

## Coverage counts and acceptance limits

PRD: **3** fully implemented; **71** partially implemented; **16** implemented but semantically incorrect; **6** present in raw data but not canonicalised; **50** not implemented; **8** cannot verify from repository evidence. Total: **154**. UX: **18** individually assessed. These counts are contract-level classifications, not percentages of code completed; one missing source or shared semantic error can affect many requirements. Fully implemented CON entries are bounded constraints/process evidence, not a declaration that dependent business workflows pass.

## Journey and acceptance cross-check

- **UJ-01 / UJ-01a, AT-01/02, UX-AC04:** partial analytical preparation; no retained recurring weekly report/definition or CSV/Excel handoff. G01/G02/G14/G18.
- **UJ-02, AT-03, UX-AC06:** partial revenue forecast with persisted outputs and comparison; product selection, scenarios and full eligible coverage are absent. G16/G17.
- **UJ-03, AT-08:** labour summary/scaffold only; independent roster/work/pay/skill/availability coverage is absent. G12/G13.
- **UJ-04, AT-06/07:** invoice and recipe fragments plus canonical stock/waste; no usable-stock-through-delivery or quantity/receipt/transfer assessment. G05/G06/G10.
- **UJ-05, AT-04:** bookings/reviews screens exist; actual-cover meanings are wrong and fulfilment/availability observations absent. G04/G23.
- **UJ-06, AT-05/09, UX-AC05:** sales/tender/invoice evidence is partial; COGS, payments/liabilities/settlements and period populations do not satisfy independent financial meaning. G03/G09/G13/G25.
- **UJ-07, AT-11:** no qualified opening-planning scenario; applicable control evidence is also absent/unverified. G23/G25.
- **UJ-08 / UJ-08a, AT-10, UX-AC03/07:** source/reconciliation drill-down and overrides exist; accountable proposed correction, accepted ownership and verified closure do not. G15/G21.
- **AT-12 / UX-AC08:** some idempotency and resource denial exists; stable business identity, population completeness, effective revocation and scope/operand denial need correction or verification. G07/G08/G18/G19/G24.
- **UX-AC01/02/10:** agreed responsibility contexts, equal home alternatives and navigation-value test fail structurally. G20.
- **UX-AC09:** responsive/component foundations are partial; representative website/phone/keyboard/assistive acceptance cannot be verified. G24.

These are repository-based comparisons against the acceptance fixtures. The fixtures were not executed against a live database or application. Numerical examples in the independent PRD are not Goldy's production observations.

## Artificial architectural limitations and genuine evidence limits

The raw → canonical → resolved → semantic direction, append-only evidence, permission boundary and connector isolation are useful foundations. A modular monolith or PostgreSQL itself is not the demonstrated obstacle. Current **grain, identity, query and grant choices** impose the artificial limits:

- Business data is collapsed into day/product-name or day/department totals before some independent relationships are modelled. That makes check/modifier/worker/service questions impossible through those projections even when source IDs exist. G08/G09/G12/G22.
- The resolved-only consumer rule makes canonical stock/waste and dropped ingredient qualifications invisible until supported by appropriate resolved contracts. This is incomplete projection coverage, not inherent inability to preserve detailed read models. G06/G10.
- Every new business query is constrained by compiled metrics, dimensions and tools. Product forecast selection and service/supplier/work-role filtering are narrower than available canonical/raw fields. Bounded governance is justified; business-required dimensions need not be forbidden because today's methods omit them. G16/G22.
- Live dashboard re-rendering is useful for current analysis but cannot preserve originally issued reports or explain late restatement. Configuration revision is not result revision. G01/G14.
- Resource READ/WRITE and department×seniority cannot represent validated operation, purpose, person/row scope, effective delegation and verifier authority. Existing owner-only seeds are a safety fallback, not a business role model. G18/G19.
- Four role-derived focus presets, uniform entity navigation and hardcoded queue severity impose unvalidated responsibility and priority. They contradict the independently agreed three contexts and unranked management choices. G20/G21.
- Source concepts are embedded in canonical naming/meaning: purchase invoices become COGS, party size becomes covers, positive product price becomes menu availability. Canonicalisation must preserve independently defined meaning, not confer authority on source labels. G03/G04/G11.

Evidence limits that software alone cannot fix include validated actual cover counting, order fulfilment timestamps, accepted/rejected delivery detail, missing parent/sub-recipe usage quantities, true published rosters/work-break/pay payloads, bank/processor settlement, applicable legal/control rules, approved budget/new-site assumptions and stakeholder reporting/access/quality policy. G04/G05/G09/G10/G12/G23/G24/G25. Source paths documented in the repository are historical evidence of access, not verification that access or completeness holds today.

Importantly, new source data alone also will not fix missing report/export/follow-up models, incorrect COGS/cover meanings, product forecast predicates or purpose/row scope. Raw-only classification identifies recoverable modelling opportunities without declaring every required field captured. Canonical-but-unresolved classification is retained explicitly in partial findings rather than incorrectly calling the facts raw-only.

## Historical audit reconciliation

The production audit is dated **9 October 2026**, source checkout `f6140b8`, deployed metadata `c3377bb` (E22). It establishes historical populations and source-field observations; its row counts and deployments are not current facts for this 14 October checkout.

Current code supersedes several historical findings (E23/E29):

- The Sales trend now calls resolved `getSalesTrend()`; the older per-source double-counting finding is not asserted as current.
- CTB pulls attempt datasets independently and aggregate failures; a recipes failure no longer necessarily prevents subsequent dataset attempts.
- Receipt-line, payment, deleted-sale, recipe-cost/link/ingredient, rich reservation/guest, review and stock/waste canonicalisation paths now exist. Older zero-row or raw-only claims for those domains cannot be repeated as current capability verdicts.
- Derived metric series align both operands on shared days and emit partial-coverage notices. The historical unequal-day series ratio issue is addressed there; independent Labour/Inventory summary calculations still have the separate gap described in G13.
- Parsing/canonicalisation stages and CSV/PDF convergence exist. The historical push-success issue is improved; stage success still does not establish expected business population or report readiness.

Still supported by current code: invoice-number/name identity weaknesses; effective-time versus receipt-time conflation; incomplete contributor lineage; COGS/purchase semantics; limited business query/filter surface; blanket explorer permissions; and absent independent report/export/local decision workflows. Design records, a production audit's recommendation, a frontend fixture or a proposed architecture diagram are not implementation proof. No historical audit recommendation for arbitrary SQL, unrestricted discovery or a warehouse is promoted into an independent requirement.

## Remaining verification evidence

Operational acceptance would require authorised source population/coverage for the inspected commit; agreed report week/contents/deadline/sign-off and export recipient tools; effective purpose/scope/operation grants and denial/revocation scenarios; verified actual-cover, delivery, work/pay, recipe/conversion and settlement semantics; applicable control/retention evidence; original report/forecast restatement traces; and representative role-holder task/recovery/volume/accessibility evidence under OD-01–14. Those are verification dependencies, not approval requests or an implementation schedule.

Existing tests such as `ReservationProjectorIntegrationTest`, `DerivedMetricExecutorTest`, `StockWastageCanonicalizationRunnerTest`, `ForecastingApplicationServiceTest`, `PermissionBoundaryIntegrationTest` and `ArchitectureBoundariesTest` provide useful test locations. Their presence does not validate the independent business contract: tests can correctly enforce an implementation whose cover or COGS meaning is wrong. No tests were run because this was a read-only static comparison, and build/test tools can write generated files or start databases.

## Inspection boundary and completion

The reference checkout was inspected read-only using file/search and Git status/history-identification commands. Its HEAD and clean working-tree status were checked again after report generation. No source/database/infrastructure change was made. The independent documents were not rewritten. The only requested repository output is `deliverables/IMPLEMENTATION-GAP-ANALYSIS.md`. Work stops after this report; no implementation, migration, architecture redesign, planning workflow or additional artefact is initiated.
