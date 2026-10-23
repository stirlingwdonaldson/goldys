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
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(LightspeedSaleItemIngestService.class);

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
      items = parseItems(body);
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        items.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);

    try {
      recordItems(items, rawId);
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        items.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
  }

  /**
   * Re-parse one already-persisted raw payload into canonical rows, without re-persisting it. Used
   * to recover sale-items reports that were stored before the {@code maxStringLength} fix but never
   * successfully canonicalized. Idempotent: a re-parsed row whose facts are unchanged is a no-op at
   * the canonical layer. An unparseable payload (e.g. a source-side truncated report) is logged and
   * skipped rather than aborting the pass.
   */
  public void backfill(RawLedgerQuery.RawPayload payload) {
    try {
      recordItems(parseItems(payload.bytes()), payload.id());
    } catch (RuntimeException e) {
      log.warn("Lightspeed sale-items replay failed for raw record {}", payload.id(), e);
    }
  }

  /** Parse and validate a retained report without changing ledger/canonical state. */
  public List<LightspeedSaleItem> preview(RawLedgerQuery.RawPayload payload) {
    List<LightspeedSaleItem> items = parseItems(payload.bytes());
    for (LightspeedSaleItem item : items) {
      if (item.quantity() == null || item.amount() == null)
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Sale-item quantity or amount is missing");
    }
    return items;
  }

  /** Targeted recovery never replaces an existing source fact and propagates every failure. */
  public int recoverMissing(RawLedgerQuery.RawPayload payload, List<LightspeedSaleItem> items) {
    int inserted = 0;
    try {
      for (LightspeedSaleItem item : items) {
        if (canonical.recordMissing(input(item, payload.id()))) inserted++;
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(
          payload.id(), IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        payload.id(),
        IngestionStageKind.CANONICALIZED,
        items.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
    return inserted;
  }

  private List<LightspeedSaleItem> parseItems(byte[] body) {
    return parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8));
  }

  private void recordItems(List<LightspeedSaleItem> items, UUID rawId) {
    for (LightspeedSaleItem it : items) {
      if (it.quantity() == null || it.amount() == null) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Sale-item quantity or amount is missing");
      }
      canonical.record(input(it, rawId));
    }
  }

  private static SaleItemInput input(LightspeedSaleItem it, UUID rawId) {
    return new SaleItemInput(
        "LIGHTSPEED",
        it.receiptLineId(),
        it.saleDate(),
        it.saleNumber(),
        it.itemName(),
        it.productNumber(),
        it.sku(),
        it.categoryName(),
        it.quantity(),
        it.amount(),
        it.soldPriceIncTax(),
        it.totalTax(),
        it.costIncTax(),
        it.orderType(),
        it.saleType(),
        it.staffName(),
        it.registerName(),
        it.tableNumber(),
        rawId);
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
}
