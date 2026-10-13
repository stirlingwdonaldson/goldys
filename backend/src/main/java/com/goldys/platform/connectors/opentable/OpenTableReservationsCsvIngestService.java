package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manual CSV-drop pipeline for the rich GuestCenter reservations export: persist the dropped CSV
 * byte-faithfully, then parse and canonicalize each reservation row. The operator exports
 * GuestCenter's full 156-column Reservations CSV and POSTs it to {@code
 * /api/ingest/opentable-reservations}; there is no automated browser pull.
 *
 * <p>Supersession is not handled here: {@link CanonicalReservationIngest} closes and re-inserts a
 * fact when a re-uploaded row's fields have changed, so this service simply records every row it
 * parses.
 */
@Service
public class OpenTableReservationsCsvIngestService {
  private final IngestionService ingestion;
  private final OpenTableReservationsCsvParser parser;
  private final CanonicalReservationIngest canonical;

  public OpenTableReservationsCsvIngestService(
      IngestionService ingestion,
      OpenTableReservationsCsvParser parser,
      CanonicalReservationIngest canonical) {
    this.ingestion = ingestion;
    this.parser = parser;
    this.canonical = canonical;
  }

  public void ingest(byte[] csv) {
    UUID rawId =
        ingestion.ingestPush(
            "OPENTABLE",
            "opentable-reservations",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            csv,
            StandardCharsets.UTF_8.name(),
            "opentable-reservations");

    for (OpenTableReservationDetail detail : parser.parse(csv)) {
      canonical.record(toInput(detail, rawId));
    }
  }

  private static ReservationInput toInput(OpenTableReservationDetail detail, UUID rawId) {
    return ReservationInput.builder()
        .sourceSystem("OPENTABLE")
        .reservationId(detail.reservationId())
        .reservationAt(detail.reservationAt())
        .partySize(detail.partySize())
        .status(detail.status())
        .tableName(detail.tableName())
        .sourceChannel(detail.sourceChannel())
        .partyName(detail.partyName())
        .rawRecordId(rawId)
        .createdAt(detail.createdAt())
        .cancelledAt(detail.cancelledAt())
        .noShowAt(detail.noShowAt())
        .seatedAt(detail.seatedAt())
        .finishedAt(detail.finishedAt())
        .lastUpdatedAt(detail.lastUpdatedAt())
        .totalActualDurationMinutes(detail.totalActualDurationMinutes())
        .tableType(detail.tableType())
        .diningAreaAssigned(detail.diningAreaAssigned())
        .diningAreaBooked(detail.diningAreaBooked())
        .diningAreaIdAssigned(detail.diningAreaIdAssigned())
        .diningAreaIdBooked(detail.diningAreaIdBooked())
        .waitStaff(detail.waitStaff())
        .createdBy(detail.createdBy())
        .turnTimeSetting(detail.turnTimeSetting())
        .reservationType(detail.reservationType())
        .occasion(detail.occasion())
        .reservationStatus(detail.reservationStatus())
        .onlineWaitlist(detail.onlineWaitlist())
        .isFlexReso(detail.isFlexReso())
        .quotedWaitTime(detail.quotedWaitTime())
        .totalWaitTime(detail.totalWaitTime())
        .onlineChannel(detail.onlineChannel())
        .partner(detail.partner())
        .campaignType(detail.campaignType())
        .campaignDetails(detail.campaignDetails())
        .referrerName(detail.referrerName())
        .referrerBusiness(detail.referrerBusiness())
        .restrefSource(detail.restrefSource())
        .restrefCampaignName(detail.restrefCampaignName())
        .restrefId(detail.restrefId())
        .guestFirstName(detail.guestFirstName())
        .guestLastName(detail.guestLastName())
        .guestNickname(detail.guestNickname())
        .guestEmail(detail.guestEmail())
        .guestPhone(detail.guestPhone())
        .company(detail.company())
        .marketingOptIn(detail.marketingOptIn())
        .birthday(detail.birthday())
        .anniversary(detail.anniversary())
        .firstVisitDate(detail.firstVisitDate())
        .recentVisitDate(detail.recentVisitDate())
        .upcomingVisits(detail.upcomingVisits())
        .completedVisits(detail.completedVisits())
        .cancelledCount(detail.cancelledCount())
        .noShowCount(detail.noShowCount())
        .lifetimeSpend(detail.lifetimeSpend())
        .lifetimePerCoverSpend(detail.lifetimePerCoverSpend())
        .guestId(detail.guestId())
        .experienceTitle(detail.experienceTitle())
        .experienceType(detail.experienceType())
        .experienceTypeEnum(detail.experienceTypeEnum())
        .experiencePriceType(detail.experiencePriceType())
        .prepaymentMethod(detail.prepaymentMethod())
        .prepaymentStatus(detail.prepaymentStatus())
        .vendorPayoutStatus(detail.vendorPayoutStatus())
        .currency(detail.currency())
        .experiencePrice(detail.experiencePrice())
        .experienceSubtotal(detail.experienceSubtotal())
        .addonsSubtotal(detail.addonsSubtotal())
        .serviceCharges(detail.serviceCharges())
        .prepaidExperienceTip(detail.prepaidExperienceTip())
        .prepaidExperienceVat(detail.prepaidExperienceVat())
        .prepaidExperienceTotalPaid(detail.prepaidExperienceTotalPaid())
        .experienceTotalSales(detail.experienceTotalSales())
        .experienceTotalSalesWithTip(detail.experienceTotalSalesWithTip())
        .prepaymentRequiredTotal(detail.prepaymentRequiredTotal())
        .prepaymentPaidTotal(detail.prepaymentPaidTotal())
        .posCheckIds(detail.posCheckIds())
        .posSubtotal(detail.posSubtotal())
        .posTax(detail.posTax())
        .posServiceCharges(detail.posServiceCharges())
        .posTip(detail.posTip())
        .posPaid(detail.posPaid())
        .posDue(detail.posDue())
        .totalTips(detail.totalTips())
        .totalVat(detail.totalVat())
        .totalIncome(detail.totalIncome())
        .totalIncomeWithTip(detail.totalIncomeWithTip())
        .totalRevenue(detail.totalRevenue())
        .totalRevenueWithTip(detail.totalRevenueWithTip())
        .totalRevenueWithoutTip(detail.totalRevenueWithoutTip())
        .posSpend(detail.posSpend())
        .posRefund(detail.posRefund())
        .posRevenue(detail.posRevenue())
        .refundAmount(detail.refundAmount())
        .vendorPayoutAmount(detail.vendorPayoutAmount())
        .guestRequests(detail.guestRequests())
        .visitNotes(detail.visitNotes())
        .reservationTags(detail.reservationTags())
        .guestTags(detail.guestTags())
        .build();
  }
}
