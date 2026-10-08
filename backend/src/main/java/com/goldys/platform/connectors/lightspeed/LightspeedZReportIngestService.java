package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Raw-only ingest for the Lightspeed Z-report (end-of-day reconciliation) webhook.
 *
 * <p>The Z-report arrives as the same Looker envelope as the sales and product reports ({@code
 * attachment.data} = CSV) and is stored byte-faithfully to the raw ledger with its own {@code
 * fetcher_identity=lightspeed-zreport}. The daily-total extraction is deliberately deferred:
 * neither repo records the Z-report's CSV columns, so a parser would be a guess. Add the parser
 * (and a {@code LightspeedRawBackfillRunner}-style re-parse) once the report's column shape is
 * confirmed and a Looker scheduled report for it exists.
 */
@Service
public class LightspeedZReportIngestService {
  private final IngestionService ingestion;

  public LightspeedZReportIngestService(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  public void ingest(byte[] body) {
    ingestion.ingestPush(
        "LIGHTSPEED",
        "lightspeed-zreport",
        FetchMethod.FILE_EXPORT,
        "application/json",
        body,
        StandardCharsets.UTF_8.name(),
        "lightspeed-zreport");
  }
}
