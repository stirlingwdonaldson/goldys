package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for OpenTable reservations: persist the dropped CSV byte-faithfully,
 * then parse and canonicalize each reservation row. The operator exports GuestCenter's Reservations
 * CSV and POSTs it to {@code /api/ingest/opentable}; there is no automated browser pull.
 */
@Service
public class OpenTableCsvIngestService {
  private final IngestionService ingestion;
  private final OpenTableCsvParser parser;
  private final CanonicalReservationIngest canonical;

  public OpenTableCsvIngestService(
      IngestionService ingestion, OpenTableCsvParser parser, CanonicalReservationIngest canonical) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
  }

  public void ingest(byte[] csv) {
    UUID rawId =
        ingestion.ingestPush(
            "OPENTABLE",
            "opentable-csv-drop",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            csv,
            StandardCharsets.UTF_8.name(),
            "opentable-csv-drop");

    for (OpenTableReservation reservation : parser.parse(csv)) {
      canonical.record(
          new ReservationInput(
              "OPENTABLE",
              reservation.reservationId(),
              reservation.reservationAt(),
              reservation.partySize(),
              reservation.status(),
              reservation.table(),
              reservation.sourceChannel(),
              reservation.partyName(),
              rawId));
    }
  }
}
