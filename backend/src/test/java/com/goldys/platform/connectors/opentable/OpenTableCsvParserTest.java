package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableCsvParserTest {

  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(
        Path.of("src/test/resources/fixtures/opentable/reservations_sample.csv"));
  }

  @Test
  void parsesRowsNormalizingStatusAndCombiningDateTime() throws Exception {
    List<OpenTableReservation> rows = parser.parse(fixture());

    assertThat(rows).hasSize(5);
    OpenTableReservation first = rows.get(0);
    assertThat(first.reservationId()).isEqualTo("1000000001");
    assertThat(first.partySize()).isEqualTo(4);
    assertThat(first.status()).isEqualTo("BOOKED");
    assertThat(first.table()).isEqualTo("12");
    assertThat(first.sourceChannel()).isEqualTo("OpenTable");
    assertThat(first.partyName()).isEqualTo("Smith");
    // 2026-09-23T19:30 Australia/Sydney == 09:30 UTC
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2026-09-23T09:30:00Z"));
  }

  @Test
  void normalizesNoShowStatus() throws Exception {
    OpenTableReservation row = parser.parse(fixture()).get(4);
    assertThat(row.status()).isEqualTo("NO_SHOW");
  }

  @Test
  void blanksBecomeNull() throws Exception {
    OpenTableReservation row = parser.parse(fixture()).get(2);
    assertThat(row.table()).isNull();
    assertThat(row.sourceChannel()).isEqualTo("Phone");
  }

  @Test
  void emptyCsvIsSchemaMismatch() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    "Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
                        .getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void missingColumnIsSchemaMismatch() {
    String csv =
        "Reservation ID,Date,Time,Party Size,Status,Table,Source\n1,2026-09-23,19:30,4,Booked,12,OpenTable\n";
    assertThatThrownBy(() -> parser.parse(csv.getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Guest Name");
  }

  @Test
  void blankReservationIdIsSchemaMismatch() {
    String csv =
        "Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + ",2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n";
    assertThatThrownBy(() -> parser.parse(csv.getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Reservation ID");
  }

  @Test
  void wrongColumnCountIsSchemaMismatch() {
    String csv =
        "Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable\n";
    assertThatThrownBy(() -> parser.parse(csv.getBytes()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("wrong column count");
  }
}
