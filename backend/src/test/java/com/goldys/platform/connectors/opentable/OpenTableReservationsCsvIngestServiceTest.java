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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableReservationsCsvIngestServiceTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableReservationsCsvParser parser =
      new OpenTableReservationsCsvParser("Australia/Melbourne");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);
  private final OpenTableReservationsCsvIngestService service =
      new OpenTableReservationsCsvIngestService(ingestion, parser, canonical);

  @Test
  void storesRawAndCanonicalizesEachRow() throws Exception {
    byte[] csv = richFixture();
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);

    service.ingest(csv);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("OPENTABLE"),
            eq("opentable-reservations"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("opentable-reservations"));
    assertThat(bytes.getValue()).isEqualTo(csv);

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, times(4)).record(input.capture());
    List<ReservationInput> values = input.getAllValues();

    ReservationInput first = values.get(0);
    assertThat(first.sourceSystem()).isEqualTo("OPENTABLE");
    assertThat(first.reservationId()).isEqualTo("CSV_140126_RES0000095");
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2020-12-05T07:00:00Z"));
    assertThat(first.partySize()).isEqualTo(3);
    assertThat(first.status()).isEqualTo("COMPLETED");
    assertThat(first.guestFirstName()).isEqualTo("Kate");
    assertThat(first.guestLastName()).isEqualTo("Loughney");
    assertThat(first.reservationStatus()).isEqualTo("Done");
    assertThat(first.marketingOptIn()).isTrue();
    assertThat(first.waitStaff()).isNull();
    assertThat(first.rawRecordId()).isEqualTo(rawId);

    assertThat(values).allSatisfy(v -> assertThat(v.sourceSystem()).isEqualTo("OPENTABLE"));
    assertThat(values).allSatisfy(v -> assertThat(v.rawRecordId()).isEqualTo(rawId));
  }

  @Test
  void supersessionUsesCanonicalService() {
    UUID rawIdA = UUID.randomUUID();
    UUID rawIdB = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(rawIdA, rawIdB);

    service.ingest(csvWithWaitStaff("Alice"));
    service.ingest(csvWithWaitStaff("Bob"));

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, times(2)).record(input.capture());
    List<ReservationInput> values = input.getAllValues();
    assertThat(values.get(0).waitStaff()).isEqualTo("Alice");
    assertThat(values.get(0).rawRecordId()).isEqualTo(rawIdA);
    assertThat(values.get(1).waitStaff()).isEqualTo("Bob");
    assertThat(values.get(1).rawRecordId()).isEqualTo(rawIdB);
  }

  private static byte[] richFixture() throws Exception {
    return Files.readAllBytes(
        Path.of("src/test/resources/fixtures/opentable/reservations_rich_sample.csv"));
  }

  private static byte[] csvWithWaitStaff(String waitStaff) {
    return ("Reservation ID,Visit Date,Size,Status,Wait staff\n"
            + "R1,2026-01-01 19:00:00,2,Finished,"
            + waitStaff
            + "\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
