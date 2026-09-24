package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableCsvIngestServiceTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);
  private final OpenTableCsvIngestService service =
      new OpenTableCsvIngestService(ingestion, parser, canonical);

  @Test
  void persistsByteFaithfullyAndCanonicalizesEachRow() {
    byte[] csv = fixtureCsv();
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);

    service.ingest(csv);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("OPENTABLE"),
            eq("opentable-csv-drop"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("opentable-csv-drop"));
    assertThat(bytes.getValue()).isEqualTo(csv);

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, times(2)).record(input.capture());
    ReservationInput first = input.getAllValues().get(0);
    assertThat(first.sourceSystem()).isEqualTo("OPENTABLE");
    assertThat(first.reservationId()).isEqualTo("1000000001");
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2026-09-23T09:30:00Z"));
    assertThat(first.partySize()).isEqualTo(4);
    assertThat(first.status()).isEqualTo("BOOKED");
    assertThat(first.tableName()).isEqualTo("12");
    assertThat(first.sourceChannel()).isEqualTo("OpenTable");
    assertThat(first.partyName()).isEqualTo("Smith");
    assertThat(first.rawRecordId()).isEqualTo(rawId);
  }

  private static byte[] fixtureCsv() {
    return ("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n"
            + "1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
