package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pulls OpenTable reservations from GuestCenter, streaming the CSV to the sink and canonicalizing
 * each row.
 */
public class OpenTableConnector implements SourceConnector {
  private final OpenTableClient client;
  private final OpenTableCsvParser parser;
  private final CanonicalReservationIngest canonical;
  private final int windowBeforeDays;
  private final int windowAfterDays;

  public OpenTableConnector(
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      int windowBeforeDays,
      int windowAfterDays) {
    this.client = client;
    this.parser = parser;
    this.canonical = canonical;
    this.windowBeforeDays = windowBeforeDays;
    this.windowAfterDays = windowAfterDays;
  }

  @Override
  public String sourceSystem() {
    return "OPENTABLE";
  }

  @Override
  public String connectorName() {
    return "opentable-guestcenter";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    // `watermark` is intentionally unused: this connector pulls a rolling window anchored
    // on LocalDate.now() rather than resuming from a stored high-water mark.
    client.authenticate();

    LocalDate today = LocalDate.now();
    byte[] csv =
        client.exportReservationsCsv(
            today.minusDays(windowBeforeDays), today.plusDays(windowAfterDays));

    UUID rawId =
        sink.accept(
            new FetchedPayload(
                FetchMethod.SCRAPE,
                "text/csv",
                csv,
                StandardCharsets.UTF_8.name(),
                "opentable-guestcenter"));

    for (OpenTableReservation r : parser.parse(csv)) {
      canonical.record(
          new ReservationInput(
              "OPENTABLE",
              r.reservationId(),
              r.reservationAt(),
              r.partySize(),
              r.status(),
              r.table(),
              r.sourceChannel(),
              r.partyName(),
              rawId));
    }
  }
}
