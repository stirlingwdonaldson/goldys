package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalReservationIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReservationService service;
  @Autowired CanonicalReservationRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_reservation");
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    ReservationInput input = input("OPENTABLE", "1000000001", "2026-09-23T09:30:00Z");

    CanonicalReservation first = service.record(input);
    CanonicalReservation retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(repository.findCurrentByRef("1000000001")).hasSize(1);
  }

  @Test
  void changedFactSupersedesThePriorVersion() {
    ReservationInput before = input("OPENTABLE", "1000000002", "2026-09-23T09:30:00Z");
    CanonicalReservation first = service.record(before);
    ReservationInput after =
        new ReservationInput(
            "OPENTABLE",
            "1000000002",
            Instant.parse("2026-09-23T09:30:00Z"),
            5,
            "CANCELLED",
            null,
            "OpenTable",
            "Jones",
            before.rawRecordId());

    CanonicalReservation corrected = service.record(after);

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findCurrentByRef("1000000002")).hasSize(1);
    assertThat(repository.findCurrentByRef("1000000002").get(0).status()).isEqualTo("CANCELLED");
  }

  @Test
  void differentSourcesKeepSeparateCurrentRows() {
    service.record(input("OPENTABLE", "1000000003", "2026-09-23T09:30:00Z"));
    service.record(input("MANUAL", "1000000003", "2026-09-23T09:30:00Z"));

    assertThat(repository.findCurrentByRef("1000000003")).hasSize(2);
  }

  @Test
  void persistsRichReservationFields() {
    UUID rawId = rawRecord("OPENTABLE");
    ReservationInput input =
        ReservationInput.builder()
            .sourceSystem("OPENTABLE")
            .reservationId("1000000099")
            .reservationAt(Instant.parse("2026-09-23T09:30:00Z"))
            .partySize(6)
            .status("BOOKED")
            .tableName("12")
            .sourceChannel("OpenTable")
            .partyName("Rich Guest")
            .rawRecordId(rawId)
            .createdAt(Instant.parse("2026-09-23T09:01:00Z"))
            .cancelledAt(Instant.parse("2026-09-23T09:02:00Z"))
            .noShowAt(Instant.parse("2026-09-23T09:03:00Z"))
            .seatedAt(Instant.parse("2026-09-23T09:04:00Z"))
            .finishedAt(Instant.parse("2026-09-23T09:05:00Z"))
            .lastUpdatedAt(Instant.parse("2026-09-23T09:06:00Z"))
            .totalActualDurationMinutes(1007)
            .tableType("v8")
            .diningAreaAssigned("v9")
            .diningAreaBooked("v10")
            .diningAreaIdAssigned("v11")
            .diningAreaIdBooked("v12")
            .waitStaff("v13")
            .createdBy("v14")
            .turnTimeSetting("v15")
            .reservationType("v16")
            .occasion("v17")
            .reservationStatus("v18")
            .onlineWaitlist(false)
            .isFlexReso(true)
            .quotedWaitTime(1021)
            .totalWaitTime(1022)
            .onlineChannel("v23")
            .partner("v24")
            .campaignType("v25")
            .campaignDetails("v26")
            .referrerName("v27")
            .referrerBusiness("v28")
            .restrefSource("v29")
            .restrefCampaignName("v30")
            .restrefId("v31")
            .guestFirstName("v32")
            .guestLastName("v33")
            .guestNickname("v34")
            .guestEmail("v35")
            .guestPhone("v36")
            .company("v37")
            .marketingOptIn(true)
            .birthday(LocalDate.of(2026, 1, 1).plusDays(39))
            .anniversary(LocalDate.of(2026, 1, 1).plusDays(40))
            .firstVisitDate(LocalDate.of(2026, 1, 1).plusDays(41))
            .recentVisitDate(LocalDate.of(2026, 1, 1).plusDays(42))
            .upcomingVisits(1043)
            .completedVisits(1044)
            .cancelledCount(1045)
            .noShowCount(1046)
            .lifetimeSpend(new BigDecimal("47.25"))
            .lifetimePerCoverSpend(new BigDecimal("48.25"))
            .guestId(UUID.fromString("00000000-0000-0000-0000-000000000049"))
            .experienceTitle("v50")
            .experienceType("v51")
            .experienceTypeEnum("v52")
            .experiencePriceType("v53")
            .prepaymentMethod("v54")
            .prepaymentStatus("v55")
            .vendorPayoutStatus("v56")
            .currency("v57")
            .experiencePrice(new BigDecimal("58.25"))
            .experienceSubtotal(new BigDecimal("59.25"))
            .addonsSubtotal(new BigDecimal("60.25"))
            .serviceCharges(new BigDecimal("61.25"))
            .prepaidExperienceTip(new BigDecimal("62.25"))
            .prepaidExperienceVat(new BigDecimal("63.25"))
            .prepaidExperienceTotalPaid(new BigDecimal("64.25"))
            .experienceTotalSales(new BigDecimal("65.25"))
            .experienceTotalSalesWithTip(new BigDecimal("66.25"))
            .prepaymentRequiredTotal(new BigDecimal("67.25"))
            .prepaymentPaidTotal(new BigDecimal("68.25"))
            .posCheckIds("v69")
            .posSubtotal(new BigDecimal("70.25"))
            .posTax(new BigDecimal("71.25"))
            .posServiceCharges(new BigDecimal("72.25"))
            .posTip(new BigDecimal("73.25"))
            .posPaid(new BigDecimal("74.25"))
            .posDue(new BigDecimal("75.25"))
            .totalTips(new BigDecimal("76.25"))
            .totalVat(new BigDecimal("77.25"))
            .totalIncome(new BigDecimal("78.25"))
            .totalIncomeWithTip(new BigDecimal("79.25"))
            .totalRevenue(new BigDecimal("80.25"))
            .totalRevenueWithTip(new BigDecimal("81.25"))
            .totalRevenueWithoutTip(new BigDecimal("82.25"))
            .posSpend(new BigDecimal("83.25"))
            .posRefund(new BigDecimal("84.25"))
            .posRevenue(new BigDecimal("85.25"))
            .refundAmount(new BigDecimal("86.25"))
            .vendorPayoutAmount(new BigDecimal("87.25"))
            .guestRequests("v88")
            .visitNotes("v89")
            .reservationTags("v90")
            .guestTags("v91")
            .build();

    service.record(input);

    CanonicalReservation current = repository.findCurrentByRef("1000000099").get(0);
    assertThat(current.createdAt()).isEqualTo(Instant.parse("2026-09-23T09:01:00Z"));
    assertThat(current.cancelledAt()).isEqualTo(Instant.parse("2026-09-23T09:02:00Z"));
    assertThat(current.noShowAt()).isEqualTo(Instant.parse("2026-09-23T09:03:00Z"));
    assertThat(current.seatedAt()).isEqualTo(Instant.parse("2026-09-23T09:04:00Z"));
    assertThat(current.finishedAt()).isEqualTo(Instant.parse("2026-09-23T09:05:00Z"));
    assertThat(current.lastUpdatedAt()).isEqualTo(Instant.parse("2026-09-23T09:06:00Z"));
    assertThat(current.totalActualDurationMinutes()).isEqualTo(1007);
    assertThat(current.tableType()).isEqualTo("v8");
    assertThat(current.diningAreaAssigned()).isEqualTo("v9");
    assertThat(current.diningAreaBooked()).isEqualTo("v10");
    assertThat(current.diningAreaIdAssigned()).isEqualTo("v11");
    assertThat(current.diningAreaIdBooked()).isEqualTo("v12");
    assertThat(current.waitStaff()).isEqualTo("v13");
    assertThat(current.createdBy()).isEqualTo("v14");
    assertThat(current.turnTimeSetting()).isEqualTo("v15");
    assertThat(current.reservationType()).isEqualTo("v16");
    assertThat(current.occasion()).isEqualTo("v17");
    assertThat(current.reservationStatus()).isEqualTo("v18");
    assertThat(current.onlineWaitlist()).isEqualTo(false);
    assertThat(current.isFlexReso()).isEqualTo(true);
    assertThat(current.quotedWaitTime()).isEqualTo(1021);
    assertThat(current.totalWaitTime()).isEqualTo(1022);
    assertThat(current.onlineChannel()).isEqualTo("v23");
    assertThat(current.partner()).isEqualTo("v24");
    assertThat(current.campaignType()).isEqualTo("v25");
    assertThat(current.campaignDetails()).isEqualTo("v26");
    assertThat(current.referrerName()).isEqualTo("v27");
    assertThat(current.referrerBusiness()).isEqualTo("v28");
    assertThat(current.restrefSource()).isEqualTo("v29");
    assertThat(current.restrefCampaignName()).isEqualTo("v30");
    assertThat(current.restrefId()).isEqualTo("v31");
    assertThat(current.guestFirstName()).isEqualTo("v32");
    assertThat(current.guestLastName()).isEqualTo("v33");
    assertThat(current.guestNickname()).isEqualTo("v34");
    assertThat(current.guestEmail()).isEqualTo("v35");
    assertThat(current.guestPhone()).isEqualTo("v36");
    assertThat(current.company()).isEqualTo("v37");
    assertThat(current.marketingOptIn()).isEqualTo(true);
    assertThat(current.birthday()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(39));
    assertThat(current.anniversary()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(40));
    assertThat(current.firstVisitDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(41));
    assertThat(current.recentVisitDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(42));
    assertThat(current.upcomingVisits()).isEqualTo(1043);
    assertThat(current.completedVisits()).isEqualTo(1044);
    assertThat(current.cancelledCount()).isEqualTo(1045);
    assertThat(current.noShowCount()).isEqualTo(1046);
    assertThat(current.lifetimeSpend()).isEqualByComparingTo(new BigDecimal("47.25"));
    assertThat(current.lifetimePerCoverSpend()).isEqualByComparingTo(new BigDecimal("48.25"));
    assertThat(current.guestId())
        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000049"));
    assertThat(current.experienceTitle()).isEqualTo("v50");
    assertThat(current.experienceType()).isEqualTo("v51");
    assertThat(current.experienceTypeEnum()).isEqualTo("v52");
    assertThat(current.experiencePriceType()).isEqualTo("v53");
    assertThat(current.prepaymentMethod()).isEqualTo("v54");
    assertThat(current.prepaymentStatus()).isEqualTo("v55");
    assertThat(current.vendorPayoutStatus()).isEqualTo("v56");
    assertThat(current.currency()).isEqualTo("v57");
    assertThat(current.experiencePrice()).isEqualByComparingTo(new BigDecimal("58.25"));
    assertThat(current.experienceSubtotal()).isEqualByComparingTo(new BigDecimal("59.25"));
    assertThat(current.addonsSubtotal()).isEqualByComparingTo(new BigDecimal("60.25"));
    assertThat(current.serviceCharges()).isEqualByComparingTo(new BigDecimal("61.25"));
    assertThat(current.prepaidExperienceTip()).isEqualByComparingTo(new BigDecimal("62.25"));
    assertThat(current.prepaidExperienceVat()).isEqualByComparingTo(new BigDecimal("63.25"));
    assertThat(current.prepaidExperienceTotalPaid()).isEqualByComparingTo(new BigDecimal("64.25"));
    assertThat(current.experienceTotalSales()).isEqualByComparingTo(new BigDecimal("65.25"));
    assertThat(current.experienceTotalSalesWithTip()).isEqualByComparingTo(new BigDecimal("66.25"));
    assertThat(current.prepaymentRequiredTotal()).isEqualByComparingTo(new BigDecimal("67.25"));
    assertThat(current.prepaymentPaidTotal()).isEqualByComparingTo(new BigDecimal("68.25"));
    assertThat(current.posCheckIds()).isEqualTo("v69");
    assertThat(current.posSubtotal()).isEqualByComparingTo(new BigDecimal("70.25"));
    assertThat(current.posTax()).isEqualByComparingTo(new BigDecimal("71.25"));
    assertThat(current.posServiceCharges()).isEqualByComparingTo(new BigDecimal("72.25"));
    assertThat(current.posTip()).isEqualByComparingTo(new BigDecimal("73.25"));
    assertThat(current.posPaid()).isEqualByComparingTo(new BigDecimal("74.25"));
    assertThat(current.posDue()).isEqualByComparingTo(new BigDecimal("75.25"));
    assertThat(current.totalTips()).isEqualByComparingTo(new BigDecimal("76.25"));
    assertThat(current.totalVat()).isEqualByComparingTo(new BigDecimal("77.25"));
    assertThat(current.totalIncome()).isEqualByComparingTo(new BigDecimal("78.25"));
    assertThat(current.totalIncomeWithTip()).isEqualByComparingTo(new BigDecimal("79.25"));
    assertThat(current.totalRevenue()).isEqualByComparingTo(new BigDecimal("80.25"));
    assertThat(current.totalRevenueWithTip()).isEqualByComparingTo(new BigDecimal("81.25"));
    assertThat(current.totalRevenueWithoutTip()).isEqualByComparingTo(new BigDecimal("82.25"));
    assertThat(current.posSpend()).isEqualByComparingTo(new BigDecimal("83.25"));
    assertThat(current.posRefund()).isEqualByComparingTo(new BigDecimal("84.25"));
    assertThat(current.posRevenue()).isEqualByComparingTo(new BigDecimal("85.25"));
    assertThat(current.refundAmount()).isEqualByComparingTo(new BigDecimal("86.25"));
    assertThat(current.vendorPayoutAmount()).isEqualByComparingTo(new BigDecimal("87.25"));
    assertThat(current.guestRequests()).isEqualTo("v88");
    assertThat(current.visitNotes()).isEqualTo("v89");
    assertThat(current.reservationTags()).isEqualTo("v90");
    assertThat(current.guestTags()).isEqualTo("v91");
  }

  private ReservationInput input(String source, String id, String at) {
    return new ReservationInput(
        source, id, Instant.parse(at), 4, "BOOKED", "12", "OpenTable", "Smith", rawRecord(source));
  }

  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
