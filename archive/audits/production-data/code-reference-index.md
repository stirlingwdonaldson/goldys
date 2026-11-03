# Precise source reference index

Backend abbreviated class/file references in reports resolve to these full repository paths.
Line ranges refer to audit checkout `f6140b8`. Backend data code is unchanged from release metadata `c3377bb`.
Frontend page paths are stated explicitly in reports; ambiguous `page.tsx` basenames are not guessed here.

| Repository path | Referenced lines |
|---|---|
| `backend/src/main/java/com/goldys/platform/api/DataExplorerController.java` | 37–84 |
| `backend/src/main/java/com/goldys/platform/api/DeputyIngestController.java` | 18–25 |
| `backend/src/main/java/com/goldys/platform/api/LightspeedIngestController.java` | 46–84, 74–84 |
| `backend/src/main/java/com/goldys/platform/application/ConnectorHealthService.java` | 19–22 |
| `backend/src/main/java/com/goldys/platform/application/InventoryReportingService.java` | 53–63, 69–91 |
| `backend/src/main/java/com/goldys/platform/application/SalesReportingService.java` | 37–43 |
| `backend/src/main/java/com/goldys/platform/application/SavedDashboardApplicationService.java` | 250–257 |
| `backend/src/main/java/com/goldys/platform/application/TrustService.java` | 107–165, 127–132, 242–271 |
| `backend/src/main/java/com/goldys/platform/canonical/BitemporalEntity.java` | 13–16, 70–78 |
| `backend/src/main/java/com/goldys/platform/canonical/BitemporalRepository.java` | 7–14 |
| `backend/src/main/java/com/goldys/platform/canonical/CanonicalBrowseQuery.java` | 59–70, 73–128 |
| `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineEnrichment.java` | 23–25, 71–90 |
| `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceLineService.java` | 38–49, 51–70 |
| `backend/src/main/java/com/goldys/platform/canonical/CanonicalInvoiceService.java` | 31–33 |
| `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` | 38–71 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvIngestService.java` | 123–134, 62–67, 72–90, 84 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtInvoiceCsvParser.java` | 48–62, 48–65 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbClient.java` | 227–239, 90–220 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbConnector.java` | 120–125, 128–147, 149–161, 181–186, 188–194, 72–89, 75–88, 78–88, 81, 82, 83, 91–125 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbRevenueParser.java` | 43–50 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/CtbSaleItemParser.java` | 29–34 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoiceIngestFlagService.java` | 25–35, 25–36 |
| `backend/src/main/java/com/goldys/platform/connectors/ctb/InvoicePdfEnrichmentService.java` | 51–60, 51–68 |
| `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedIngestService.java` | 104–110, 77–86 |
| `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedInsightsCsvParser.java` | 35–52, 41–52 |
| `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductCsvParser.java` | 82–85 |
| `backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedProductIngestService.java` | 41–75, 52–71 |
| `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvIngestService.java` | 11–15 |
| `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableCsvParser.java` | 43–74 |
| `backend/src/main/java/com/goldys/platform/ingestion/ConnectorRunner.java` | 71–103 |
| `backend/src/main/java/com/goldys/platform/ingestion/CtbSftpPull.java` | 38–81, 66–78 |
| `backend/src/main/java/com/goldys/platform/ingestion/IngestionRunService.java` | 59–76, 85–98 |
| `backend/src/main/java/com/goldys/platform/ingestion/IngestionService.java` | 111–124, 167–212 |
| `backend/src/main/java/com/goldys/platform/ingestion/RawPayloadService.java` | 31–64 |
| `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesResolver.java` | 27–57 |
| `backend/src/main/java/com/goldys/platform/reconciliation/DataExplorerQueryImpl.java` | 78–84 |
| `backend/src/main/java/com/goldys/platform/reconciliation/InventoryProjector.java` | 43–90 |
| `backend/src/main/java/com/goldys/platform/reconciliation/InvoiceLineMetricsServiceImpl.java` | 40–74 |
| `backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java` | 35–39 |
| `backend/src/main/java/com/goldys/platform/reporting/ToolId.java` | 4–14 |
| `backend/src/main/java/com/goldys/platform/reporting/ToolRegistry.java` | 12–22 |
| `backend/src/main/java/com/goldys/platform/semantic/catalog/DerivedMetricExecutor.java` | 102–139, 220–231 |
| `backend/src/main/java/com/goldys/platform/semantic/catalog/GrainAggregator.java` | 24–57 |
| `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricCatalog.java` | 204–213, 90–335 |
| `backend/src/main/java/com/goldys/platform/semantic/catalog/MetricQuery.java` | 6–15 |
| `backend/src/main/resources/application.yml` | 100–125, 126–144 |
| `frontend/components/data-explorer/generic-table.tsx` | 9–25 |
| `frontend/lib/api/client.ts` | 28, 28–46, 39 |
| `frontend/lib/api/demo.ts` | 350–360, 401–426 |
| `frontend/lib/api/live.ts` | 35–102 |
| `frontend/lib/demo-mode.tsx` | 33–43 |
| `frontend/lib/use-api-data.ts` | 15–61 |
