package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Manual scoped evidence imports. Financial facts are never written through this adapter. */
@Service
public class LightspeedReceiptEvidenceIngestService {
  private final IngestionService ingestion;
  private final CanonicalReceiptEvidenceIngest canonical;
  private final LightspeedReceiptCsvParser parser;
  private final ObjectMapper mapper;

  public LightspeedReceiptEvidenceIngestService(
      IngestionService ingestion,
      CanonicalReceiptEvidenceIngest canonical,
      LightspeedReceiptCsvParser parser,
      ObjectMapper mapper) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
    this.mapper = mapper;
  }

  public ImportResult ingest(byte[] bytes, ReceiptImportScope scope, boolean mapping) {
    String dataset = mapping ? "lightspeed-receipt-mapping" : "lightspeed-sales-feed";
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED", dataset, FetchMethod.MANUAL, "text/csv", bytes, "UTF-8", dataset);
    return process(rawId, bytes, scope, mapping);
  }

  public ImportResult ingestMappingWebhook(byte[] body, long siteNumber, String siteName) {
    String dataset = "lightspeed-receipt-mapping";
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            dataset,
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            "UTF-8",
            dataset);
    byte[] csv;
    ReceiptImportScope scope;
    try {
      csv = LightspeedSaleItemIngestService.extractCsv(body).getBytes(StandardCharsets.UTF_8);
      scope = parser.observedMappingScope(csv, siteNumber, siteName);
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    return process(rawId, csv, scope, true);
  }

  private ImportResult process(
      UUID rawId, byte[] bytes, ReceiptImportScope scope, boolean mapping) {
    String dataset = mapping ? "lightspeed-receipt-mapping" : "lightspeed-sales-feed";
    List<ReceiptMappingInput> mappings;
    List<SaleTransactionInput> transactions;
    UUID scopeRawId = null;
    try {
      byte[] scopeBytes =
          mapper.writeValueAsBytes(
              new ScopeEvidence(
                  rawId, scope, "SOURCE_COVERAGE_UNVERIFIED", "SOURCE_TIMEZONE_UNVERIFIED"));
      scopeRawId =
          ingestion.ingestPush(
              "LIGHTSPEED",
              dataset + "-scope",
              FetchMethod.MANUAL,
              "application/json",
              scopeBytes,
              StandardCharsets.UTF_8.name(),
              dataset + "-scope");
      mappings = mapping ? parser.mappings(bytes, scope) : List.of();
      transactions = mapping ? List.of() : parser.transactions(bytes, scope);
    } catch (Exception e) {
      if (scopeRawId != null)
        ingestion.recordQualification(
            scopeRawId,
            "PARENT_IMPORT_FAILED",
            "Linked receipt payload failed validation; inspect its retained evidence.");
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      if (e instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Could not retain receipt import scope");
    }
    int rows = mappings.size() + transactions.size();
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        rows == 0 ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
    try {
      canonical.record(
          mapping ? "MAPPING" : "TRANSACTIONS", rawId, scopeRawId, scope, mappings, transactions);
    } catch (RuntimeException e) {
      ingestion.recordQualification(
          scopeRawId,
          "PARENT_IMPORT_FAILED",
          "Linked receipt payload failed canonical processing; inspect its retained evidence.");
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        rows == 0 ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
    ingestion.recordQualification(
        rawId,
        "RECEIPT_EVIDENCE_UNVERIFIED",
        "Export population, source timezone and line-to-transaction identity remain unverified; omissions never delete prior observations.");
    ingestion.recordQualification(
        scopeRawId,
        "RECEIPT_EVIDENCE_UNVERIFIED",
        "Scope accompanies a qualified receipt import; it does not certify export completeness.");
    return new ImportResult(rawId, rows, "SOURCE_COVERAGE_UNVERIFIED");
  }

  private record ScopeEvidence(
      UUID payloadRawId, ReceiptImportScope scope, String coverage, String sourceTimezone) {}

  public record ImportResult(UUID rawId, int distinctObservations, String qualification) {}
}
