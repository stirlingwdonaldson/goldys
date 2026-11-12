package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Retains the webhook before validating and publishing source-reported sale-date daily totals. */
@Service
public class LightspeedIngestService {
  private final IngestionService ingestion;
  private final CanonicalDailySalesIngest canonical;
  private final LightspeedInsightsCsvParser parser;

  public LightspeedIngestService(
      IngestionService ingestion,
      CanonicalDailySalesIngest canonical,
      LightspeedInsightsCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-insights",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-insights");
    parseAndRecord(body, rawId);
  }

  /** Legacy reconciliation reports fail validation; this is not a historical correction route. */
  public void backfill(List<RawLedgerQuery.RawPayload> payloads) {
    for (RawLedgerQuery.RawPayload payload : payloads) {
      parseAndRecord(payload.bytes(), payload.id());
    }
  }

  private void parseAndRecord(byte[] body, UUID rawId) {
    List<LightspeedInsightsSale> sales;
    try {
      sales = parser.parse(LightspeedEnvelope.csv(body));
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    IngestionStageOutcome outcome =
        sales.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS;
    ingestion.recordStage(rawId, IngestionStageKind.PARSED, outcome);
    try {
      for (LightspeedInsightsSale sale : sales) {
        // Net, tax and gross already include adjustments; never add adjustment measures again.
        canonical.record(
            new DailySalesInput(
                "LIGHTSPEED",
                sale.saleClosedDate(),
                sale.totalIncTax(),
                sale.totalTax(),
                sale.netTotal(),
                rawId));
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, outcome);
    if (sales.isEmpty())
      ingestion.recordQualification(
          rawId,
          "DAILY_SALES_EMPTY_UNVERIFIED",
          "Empty report supplies no dated zero totals; previous daily facts remain unchanged.");
  }
}
