package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for the thin 8-column OpenTable reservations export. This path is
 * <strong>deprecated</strong> in favour of {@link OpenTableReservationsCsvIngestService} (the rich
 * GuestCenter export): it stores the dropped CSV byte-faithfully and does <em>not</em>
 * canonicalize, so it can never downgrade a rich reservation row. The operator exports the
 * Reservations CSV and POSTs it to {@code /api/ingest/opentable}; there is no automated browser
 * pull.
 */
@Service
public class OpenTableCsvIngestService {
  private final IngestionService ingestion;

  public OpenTableCsvIngestService(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  public void ingest(byte[] csv) {
    ingestion.ingestPush(
        "OPENTABLE",
        "opentable-csv-drop",
        FetchMethod.FILE_EXPORT,
        "text/csv",
        csv,
        StandardCharsets.UTF_8.name(),
        "opentable-csv-drop");
  }
}
