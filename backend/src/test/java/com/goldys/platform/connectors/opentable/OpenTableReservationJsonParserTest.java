package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Synthetic fixtures shaped like GuestCenter's reservations/gold response. No real guest data. */
class OpenTableReservationJsonParserTest {
  private final OpenTableReservationJsonParser parser =
      new OpenTableReservationJsonParser(new ObjectMapper(), ZoneId.of("Australia/Melbourne"));
  private final UUID rawId = UUID.randomUUID();

  private static byte[] response(String rows) {
    return ("{\"meta-data\":{\"limit\":1000,\"offset\":0},\"data\":[" + rows + "]}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String row(String id, String state, String source) {
    return """
        {"reservationId":"%s","visitDate":"2026-10-11T18:30:00","partySize":4,
         "stateCategory":"%s","primarySource":"%s","tableId":"BG1,BG2",
         "guestName":"Test Guest","phone":"**********","totalSalesWithTip":null}"""
        .formatted(id, state, source);
  }

  @Test
  void mapsFieldsAndConvertsVenueLocalTime() {
    List<ReservationInput> inputs =
        parser.parse(response(row("abc123", "finished", "website")), rawId);

    assertThat(inputs).hasSize(1);
    ReservationInput r = inputs.getFirst();
    assertThat(r.sourceSystem()).isEqualTo("OPENTABLE");
    assertThat(r.reservationId()).isEqualTo("abc123");
    // 18:30 in Melbourne during AEDT (UTC+11).
    assertThat(r.reservationAt()).isEqualTo(Instant.parse("2026-10-11T07:30:00Z"));
    assertThat(r.partySize()).isEqualTo(4);
    assertThat(r.status()).isEqualTo("COMPLETED");
    assertThat(r.tableName()).isEqualTo("BG1,BG2");
    assertThat(r.sourceChannel()).isEqualTo("website");
    assertThat(r.partyName()).isEqualTo("Test Guest");
    assertThat(r.rawRecordId()).isEqualTo(rawId);
  }

  @Test
  void mapsEveryStateCategory() {
    assertThat(OpenTableReservationJsonParser.status("booked", "website")).isEqualTo("BOOKED");
    assertThat(OpenTableReservationJsonParser.status("seated", "phone")).isEqualTo("SEATED");
    assertThat(OpenTableReservationJsonParser.status("finished", "network")).isEqualTo("COMPLETED");
    assertThat(OpenTableReservationJsonParser.status("noShow", "website")).isEqualTo("NO_SHOW");
    assertThat(OpenTableReservationJsonParser.status("cancelled", "phone")).isEqualTo("CANCELLED");
  }

  @Test
  void walkInsThatWereSeatedBecomeWalkIn() {
    assertThat(OpenTableReservationJsonParser.status("seated", "walkin")).isEqualTo("WALK_IN");
    assertThat(OpenTableReservationJsonParser.status("finished", "walkin")).isEqualTo("WALK_IN");
    // A cancelled walk-in is still a cancellation.
    assertThat(OpenTableReservationJsonParser.status("cancelled", "walkin")).isEqualTo("CANCELLED");
  }

  @Test
  void unknownStatePassesThroughUppercased() {
    assertThat(OpenTableReservationJsonParser.status("waitlisted", "website"))
        .isEqualTo("WAITLISTED");
  }

  @Test
  void allowsMissingGuestNameAndTable() {
    String row =
        """
        {"reservationId":"x1","visitDate":"2026-01-02T12:00:00","partySize":2,
         "stateCategory":"booked","primarySource":"phone","tableId":"","guestName":null}""";

    ReservationInput r = parser.parse(response(row), rawId).getFirst();

    assertThat(r.partyName()).isNull();
    assertThat(r.tableName()).isNull();
  }

  @Test
  void missingRequiredFieldIsSchemaMismatch() {
    String row =
        "{\"visitDate\":\"2026-01-02T12:00:00\",\"partySize\":2,\"stateCategory\":\"booked\"}";

    assertThatThrownBy(() -> parser.parse(response(row), rawId))
        .isInstanceOf(ConnectorFetchException.class)
        .satisfies(
            e ->
                assertThat(((ConnectorFetchException) e).failureType())
                    .isEqualTo("CONNECTOR_SCHEMA_MISMATCH"))
        .hasMessageContaining("reservationId");
  }

  @Test
  void nonJsonOrMissingDataIsSchemaMismatch() {
    assertThatThrownBy(() -> parser.parse("<html>".getBytes(StandardCharsets.UTF_8), rawId))
        .isInstanceOf(ConnectorFetchException.class);
    assertThatThrownBy(() -> parser.rowCount("{\"items\":[]}".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class);
  }

  @Test
  void countsRows() {
    assertThat(
            parser.rowCount(
                response(row("a", "booked", "phone") + "," + row("b", "booked", "phone"))))
        .isEqualTo(2);
  }
}
