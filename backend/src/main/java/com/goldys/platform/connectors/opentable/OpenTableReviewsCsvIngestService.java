package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for the OpenTable reviews export: persist the dropped CSV
 * byte-faithfully, then parse it to validate the shape.
 *
 * <p>Reviews canonicalization is a later slice, so the parsed rows are parked — nothing is written
 * to the canonical layer yet. The operator exports GuestCenter's Reviews CSV and POSTs it to {@code
 * /api/ingest/opentable-reviews}; there is no automated browser pull.
 */
@Service
public class OpenTableReviewsCsvIngestService {
  private final IngestionService ingestion;
  private final OpenTableReviewsCsvParser parser;

  public OpenTableReviewsCsvIngestService(
      IngestionService ingestion, OpenTableReviewsCsvParser parser) {
    this.ingestion = ingestion;
    this.parser = parser;
  }

  public void ingest(byte[] csv) {
    ingestion.ingestPush(
        "OPENTABLE",
        "opentable-reviews",
        FetchMethod.FILE_EXPORT,
        "text/csv",
        csv,
        StandardCharsets.UTF_8.name(),
        "opentable-reviews");

    // Validate the shape (fails loudly on a schema change); the parsed rows are not canonicalized.
    parser.parse(csv);
  }
}
