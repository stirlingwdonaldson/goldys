package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalDeletedSaleIngest;
import com.goldys.platform.canonical.DeletedSaleInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lightspeed "all-deleted-orders" webhook pipeline: persist the Looker envelope byte-faithfully,
 * then parse the embedded CSV and record one canonical deleted sale per row.
 */
@Service
public class LightspeedDeletedSaleIngestService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final IngestionService ingestion;
  private final CanonicalDeletedSaleIngest canonical;
  private final LightspeedDeletedSaleCsvParser parser;

  public LightspeedDeletedSaleIngestService(
      IngestionService ingestion,
      CanonicalDeletedSaleIngest canonical,
      LightspeedDeletedSaleCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-deleted-sales",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-deleted-sales");

    List<LightspeedDeletedSale> deletedSales;
    try {
      deletedSales = parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        deletedSales.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);

    try {
      for (LightspeedDeletedSale d : deletedSales) {
        canonical.record(
            new DeletedSaleInput(
                "LIGHTSPEED",
                d.saleOpenedDate(),
                d.saleNumber(),
                d.orderType(),
                d.note(),
                nz(d.totalIncTax()),
                nz(d.totalExTax()),
                nz(d.totalTax()),
                d.totalCost(),
                d.openedRegisterCode(),
                d.openedRegisterName(),
                d.deletedRegisterCode(),
                d.deletedRegisterName(),
                d.staffName(),
                d.staffCode(),
                d.deletedByStaffName(),
                d.deletedByStaffCode(),
                d.tableNumber(),
                d.siteId(),
                d.customerName(),
                rawId));
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        deletedSales.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
  }

  private static String extractCsv(byte[] body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      String data = root.path("attachment").path("data").asText(null);
      if (data == null) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "No attachment.data in Lightspeed webhook payload");
      }
      return data;
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON webhook payload", e);
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
