package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

  @Test
  void mapsRichFieldsFromTheRealReservationShape() {
    // A scrubbed reservation shaped like the live reservations/gold response (money in cents,
    // guest-lifetime rollups under the diner… prefix, gratuity/tax instead of tip/vat).
    String rich =
        """
        {"reservationId":"r1","visitDate":"2026-09-01T17:00:00","partySize":10,
         "stateCategory":"finished","primarySource":"website","tableId":"BG10,BG9","guestName":"Test Guest",
         "firstName":"Test","lastName":"Guest","nickname":null,
         "dinerCompletedVisits":3,"dinerNoShowCount":1,"dinerCancelledCount":0,
         "dinerLifetimeSpend":250000,"dinerLifetimePerCoverSpend":1250,
         "dinerRecentVisitDate":"2026-09-01T17:00:00","dinerFirstVisitDate":"2024-01-05T12:00:00",
         "dinerUpcomingVisits":2,"anniversary":null,"birthday":null,"jobTitle":null,"company":null,
         "createdDate":"2026-08-30T17:32:36","cancelledDate":null,"noShowDate":null,
         "seatedDate":"2026-09-01T17:24:53","finishedDate":"2026-09-01T19:00:00",
         "lastUpdatedDate":"2026-09-04T05:45:58","totalActualDuration":95,
         "tableType":"Outdoor","diningAreaAssigned":"Garden","diningAreaBooked":null,
         "diningAreaIdAssigned":7,"diningAreaIdBooked":0,
         "server":"tim","createdBy":null,"turnTimeSetting":126,"reservationType":"standard",
         "occasion":"birthday","reservationStatus":"AssumedDone",
         "onlineWaitList":false,"isFlexReso":false,"quotedWaitTime":5,"totalWaitTime":10,
         "onlineChannel":"website","partner":null,"otBoostType":null,"campaignDetails":null,
         "referrerName":null,"referrerBusiness":null,"restrefSource":"restaurant website",
         "restrefCampaignName":null,"restrefId":null,
         "email":"guest@example.com","phone":"0412345678","marketingOptIn":false,
         "experienceTitle":null,"experienceType":null,"experienceTypeEnum":null,
         "experiencePriceType":null,"experiencePricePerCover":0,"experienceSubtotal":0,
         "experienceAddonsSubtotal":null,"experienceServiceCharges":null,"experienceGratuity":null,
         "experienceTax":null,"experienceTotalRevenue":0,"experienceTotalRevenueWithTip":0,
         "prepaymentMethod":null,"prepaymentStatus":"notPaid","prepaymentRequiredTotal":null,
         "prepaymentPaidTotal":null,"vendorPayoutStatus":null,
         "posCheckIds":null,"posSubtotal":84000,"posTax":8000,"posServiceCharges":0,
         "posGratuity":5000,"posPaid":97000,"posDue":null,"totalGratuity":5000,"totalTax":8000,
         "totalIncomeWithoutTip":92000,"totalIncomeWithTip":97000,"totalRevenue":92000,
         "totalRevenueWithTip":97000,"totalRevenueWithoutTip":92000,"posSpend":97000,
         "posRefund":null,"posRevenue":97000,"refundAmount":0,"vendorPayoutAmount":null,
         "currencyMultiplier":100,"currency":"aud",
         "guestRequests":"","visitNotes":"Window seat","reservationTags":"gc:service/birthday","guestTags":""}""";

    ReservationInput r = parser.parse(response(rich), rawId).getFirst();

    assertThat(r.reservationId()).isEqualTo("r1");
    assertThat(r.status()).isEqualTo("COMPLETED");
    assertThat(r.tableName()).isEqualTo("BG10,BG9");
    assertThat(r.tableType()).isEqualTo("Outdoor");
    // guest contact is masked/left null by privacy choice
    assertThat(r.guestEmail()).isNull();
    assertThat(r.guestPhone()).isNull();
    assertThat(r.guestFirstName()).isEqualTo("Test");
    // renaming: Wait staff ← server
    assertThat(r.waitStaff()).isEqualTo("tim");
    // diner… prefix guest-lifetime rollups
    assertThat(r.completedVisits()).isEqualTo(3);
    assertThat(r.noShowCount()).isEqualTo(1);
    assertThat(r.upcomingVisits()).isEqualTo(2);
    assertThat(r.firstVisitDate()).isEqualTo(LocalDate.parse("2024-01-05"));
    // money is in cents: dinerLifetimeSpend 250000 → 2500.00
    assertThat(r.lifetimeSpend()).isEqualByComparingTo("2500.00");
    assertThat(r.lifetimePerCoverSpend()).isEqualByComparingTo("12.50");
    // gratuity/tax renames
    assertThat(r.posTip()).isEqualByComparingTo("50.00");
    assertThat(r.totalTips()).isEqualByComparingTo("50.00");
    assertThat(r.totalVat()).isEqualByComparingTo("80.00");
    assertThat(r.totalIncome()).isEqualByComparingTo("920.00");
    assertThat(r.posSubtotal()).isEqualByComparingTo("840.00");
    assertThat(r.posPaid()).isEqualByComparingTo("970.00");
    // currency uppercased
    assertThat(r.currency()).isEqualTo("AUD");
    // visit lifecycle
    assertThat(r.seatedAt()).isNotNull();
    assertThat(r.finishedAt()).isNotNull();
    assertThat(r.totalActualDurationMinutes()).isEqualTo(95);
    // operational / source / notes
    assertThat(r.diningAreaAssigned()).isEqualTo("Garden");
    assertThat(r.onlineChannel()).isEqualTo("website");
    assertThat(r.restrefSource()).isEqualTo("restaurant website");
    assertThat(r.visitNotes()).isEqualTo("Window seat");
    assertThat(r.reservationStatus()).isEqualTo("AssumedDone");
  }
}
