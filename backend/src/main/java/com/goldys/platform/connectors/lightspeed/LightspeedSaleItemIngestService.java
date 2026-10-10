package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalSaleItemIngest;
import com.goldys.platform.canonical.SaleItemInput;
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
 * Lightspeed "sales-details" webhook pipeline: persist the Looker envelope byte-faithfully, then
 * parse the embedded CSV and record one canonical sale line item per receipt line.
 *
 * <p>The report must be scheduled without the {@code Product Salelines → Product ID} field; that
 * join silently drops ~92% of rows (see the design spec).
 */
@Service
public class LightspeedSaleItemIngestService {
  private static final ObjectMapper MAPPER =
      new ObjectMapper(
          JsonFactory.builder()
              .streamReadConstraints(
                  StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
              .build());

  private final IngestionService ingestion;
  private final CanonicalSaleItemIngest canonical;
  private final LightspeedSaleItemCsvParser parser;

  public LightspeedSaleItemIngestService(
      IngestionService ingestion,
      CanonicalSaleItemIngest canonical,
      LightspeedSaleItemCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-sale-items",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-sale-items");

    List<LightspeedSaleItem> items;
    try {
      items = parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        items.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);

    try {
      for (LightspeedSaleItem it : items) {
        canonical.record(
            new SaleItemInput(
                "LIGHTSPEED",
                it.receiptLineId(),
                it.saleDate(),
                it.saleNumber(),
                it.itemName(),
                it.productNumber(),
                it.sku(),
                it.categoryName(),
                nz(it.quantity()),
                nz(it.amount()),
                it.soldPriceIncTax(),
                it.totalTax(),
                it.costIncTax(),
                it.orderType(),
                it.saleType(),
                it.staffName(),
                it.registerName(),
                it.tableNumber(),
                rawId));
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        items.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
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
