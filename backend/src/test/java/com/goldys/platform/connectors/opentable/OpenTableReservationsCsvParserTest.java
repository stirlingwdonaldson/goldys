package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenTableReservationsCsvParserTest {

  private final OpenTableReservationsCsvParser parser =
      new OpenTableReservationsCsvParser("Australia/Melbourne");

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(
        Path.of("src/test/resources/fixtures/opentable/reservations_rich_sample.csv"));
  }

  @Test
  void stripsBomAndFindsColumns() throws Exception {
    List<OpenTableReservationDetail> rows = parser.parse(fixture());

    assertThat(rows).hasSize(4);
    OpenTableReservationDetail first = rows.get(0);
    assertThat(first.reservationId()).isEqualTo("CSV_140126_RES0000095");
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2020-12-05T07:00:00Z"));
    assertThat(first.partySize()).isEqualTo(3);
    assertThat(first.status()).isEqualTo("COMPLETED");
  }

  @Test
  void mapsRichFields() throws Exception {
    OpenTableReservationDetail first = parser.parse(fixture()).get(0);

    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2020-12-05T07:00:00Z"));
    assertThat(first.partySize()).isEqualTo(3);
    assertThat(first.status()).isEqualTo("COMPLETED");
    assertThat(first.waitStaff()).isNull();
    assertThat(first.posPaid()).isEqualByComparingTo("0.00");
    assertThat(first.guestFirstName()).isEqualTo("Kate");
    assertThat(first.guestLastName()).isEqualTo("Loughney");
    assertThat(first.reservationStatus()).isEqualTo("Done");
    assertThat(first.marketingOptIn()).isTrue();
  }

  @Test
  void masksPiiToNull() throws Exception {
    OpenTableReservationDetail first = parser.parse(fixture()).get(0);

    assertThat(first.guestPhone()).isNull();
    assertThat(first.guestEmail()).isNull();
  }

  @Test
  void blankOptionalToNull() throws Exception {
    OpenTableReservationDetail first = parser.parse(fixture()).get(0);

    assertThat(first.waitStaff()).isNull();
    assertThat(first.totalWaitTime()).isNull();
  }

  @Test
  void missingColumnThrows() {
    String csv = "Reservation ID,Size,Status\nCSV_140126_RES0000095,3,Finished\n";

    assertThatThrownBy(() -> parser.parse(csv.getBytes()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  @Test
  void handlesBothIdFormats() throws Exception {
    List<OpenTableReservationDetail> rows = parser.parse(fixture());

    assertThat(rows.get(0).reservationId()).isEqualTo("CSV_140126_RES0000095");
    assertThat(rows.get(1).reservationId()).isEqualTo("99BD5AF1-9DC3-486E-BB5E-7A6223B53AFA");
    assertThat(rows.get(2).reservationId()).isEqualTo("605980517a5e3700016f8399");
    assertThat(rows.get(3).reservationId()).isEqualTo("6abf1354bb32911e572c9555");
  }

  @Test
  void coercesMoneyStrippingCurrencyPrefix() {
    String csv =
        "Reservation ID,Visit Date,Size,Status,POS Paid\n"
            + "R1,2026-01-01 19:00:00,2,Finished,A$12.50\n";

    OpenTableReservationDetail row = parser.parse(csv.getBytes()).get(0);
    assertThat(row.posPaid()).isEqualByComparingTo(new BigDecimal("12.50"));
  }
}
