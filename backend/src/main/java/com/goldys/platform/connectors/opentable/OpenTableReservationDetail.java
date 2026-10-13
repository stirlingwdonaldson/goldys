package com.goldys.platform.connectors.opentable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single reservation row parsed from the rich 156-column GuestCenter export.
 *
 * <p>One field per {@link com.goldys.platform.canonical.ReservationInput} field, except {@code
 * sourceSystem} and {@code rawRecordId} (both are supplied by the ingest service, not parsed from
 * the CSV). {@code reservationId} is the raw {@code Reservation ID} string, {@code status} is the
 * normalized status, and every money/count/date field is already coerced (blank → {@code null}).
 * Because the record is large it is constructed through {@link #builder()}, never a positional
 * constructor.
 */
public record OpenTableReservationDetail(
    String reservationId,
    Instant reservationAt,
    int partySize,
    String status,
    String tableName,
    String sourceChannel,
    String partyName,
    Instant createdAt,
    Instant cancelledAt,
    Instant noShowAt,
    Instant seatedAt,
    Instant finishedAt,
    Instant lastUpdatedAt,
    Integer totalActualDurationMinutes,
    String tableType,
    String diningAreaAssigned,
    String diningAreaBooked,
    String diningAreaIdAssigned,
    String diningAreaIdBooked,
    String waitStaff,
    String createdBy,
    String turnTimeSetting,
    String reservationType,
    String occasion,
    String reservationStatus,
    Boolean onlineWaitlist,
    Boolean isFlexReso,
    Integer quotedWaitTime,
    Integer totalWaitTime,
    String onlineChannel,
    String partner,
    String campaignType,
    String campaignDetails,
    String referrerName,
    String referrerBusiness,
    String restrefSource,
    String restrefCampaignName,
    String restrefId,
    String guestFirstName,
    String guestLastName,
    String guestNickname,
    String guestEmail,
    String guestPhone,
    String company,
    Boolean marketingOptIn,
    LocalDate birthday,
    LocalDate anniversary,
    LocalDate firstVisitDate,
    LocalDate recentVisitDate,
    Integer upcomingVisits,
    Integer completedVisits,
    Integer cancelledCount,
    Integer noShowCount,
    BigDecimal lifetimeSpend,
    BigDecimal lifetimePerCoverSpend,
    UUID guestId,
    String experienceTitle,
    String experienceType,
    String experienceTypeEnum,
    String experiencePriceType,
    String prepaymentMethod,
    String prepaymentStatus,
    String vendorPayoutStatus,
    String currency,
    BigDecimal experiencePrice,
    BigDecimal experienceSubtotal,
    BigDecimal addonsSubtotal,
    BigDecimal serviceCharges,
    BigDecimal prepaidExperienceTip,
    BigDecimal prepaidExperienceVat,
    BigDecimal prepaidExperienceTotalPaid,
    BigDecimal experienceTotalSales,
    BigDecimal experienceTotalSalesWithTip,
    BigDecimal prepaymentRequiredTotal,
    BigDecimal prepaymentPaidTotal,
    String posCheckIds,
    BigDecimal posSubtotal,
    BigDecimal posTax,
    BigDecimal posServiceCharges,
    BigDecimal posTip,
    BigDecimal posPaid,
    BigDecimal posDue,
    BigDecimal totalTips,
    BigDecimal totalVat,
    BigDecimal totalIncome,
    BigDecimal totalIncomeWithTip,
    BigDecimal totalRevenue,
    BigDecimal totalRevenueWithTip,
    BigDecimal totalRevenueWithoutTip,
    BigDecimal posSpend,
    BigDecimal posRefund,
    BigDecimal posRevenue,
    BigDecimal refundAmount,
    BigDecimal vendorPayoutAmount,
    String guestRequests,
    String visitNotes,
    String reservationTags,
    String guestTags) {

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for a parsed reservation detail row. */
  public static final class Builder {
    private String reservationId;
    private Instant reservationAt;
    private int partySize;
    private String status;
    private String tableName;
    private String sourceChannel;
    private String partyName;
    private Instant createdAt;
    private Instant cancelledAt;
    private Instant noShowAt;
    private Instant seatedAt;
    private Instant finishedAt;
    private Instant lastUpdatedAt;
    private Integer totalActualDurationMinutes;
    private String tableType;
    private String diningAreaAssigned;
    private String diningAreaBooked;
    private String diningAreaIdAssigned;
    private String diningAreaIdBooked;
    private String waitStaff;
    private String createdBy;
    private String turnTimeSetting;
    private String reservationType;
    private String occasion;
    private String reservationStatus;
    private Boolean onlineWaitlist;
    private Boolean isFlexReso;
    private Integer quotedWaitTime;
    private Integer totalWaitTime;
    private String onlineChannel;
    private String partner;
    private String campaignType;
    private String campaignDetails;
    private String referrerName;
    private String referrerBusiness;
    private String restrefSource;
    private String restrefCampaignName;
    private String restrefId;
    private String guestFirstName;
    private String guestLastName;
    private String guestNickname;
    private String guestEmail;
    private String guestPhone;
    private String company;
    private Boolean marketingOptIn;
    private LocalDate birthday;
    private LocalDate anniversary;
    private LocalDate firstVisitDate;
    private LocalDate recentVisitDate;
    private Integer upcomingVisits;
    private Integer completedVisits;
    private Integer cancelledCount;
    private Integer noShowCount;
    private BigDecimal lifetimeSpend;
    private BigDecimal lifetimePerCoverSpend;
    private UUID guestId;
    private String experienceTitle;
    private String experienceType;
    private String experienceTypeEnum;
    private String experiencePriceType;
    private String prepaymentMethod;
    private String prepaymentStatus;
    private String vendorPayoutStatus;
    private String currency;
    private BigDecimal experiencePrice;
    private BigDecimal experienceSubtotal;
    private BigDecimal addonsSubtotal;
    private BigDecimal serviceCharges;
    private BigDecimal prepaidExperienceTip;
    private BigDecimal prepaidExperienceVat;
    private BigDecimal prepaidExperienceTotalPaid;
    private BigDecimal experienceTotalSales;
    private BigDecimal experienceTotalSalesWithTip;
    private BigDecimal prepaymentRequiredTotal;
    private BigDecimal prepaymentPaidTotal;
    private String posCheckIds;
    private BigDecimal posSubtotal;
    private BigDecimal posTax;
    private BigDecimal posServiceCharges;
    private BigDecimal posTip;
    private BigDecimal posPaid;
    private BigDecimal posDue;
    private BigDecimal totalTips;
    private BigDecimal totalVat;
    private BigDecimal totalIncome;
    private BigDecimal totalIncomeWithTip;
    private BigDecimal totalRevenue;
    private BigDecimal totalRevenueWithTip;
    private BigDecimal totalRevenueWithoutTip;
    private BigDecimal posSpend;
    private BigDecimal posRefund;
    private BigDecimal posRevenue;
    private BigDecimal refundAmount;
    private BigDecimal vendorPayoutAmount;
    private String guestRequests;
    private String visitNotes;
    private String reservationTags;
    private String guestTags;

    private Builder() {}

    public Builder reservationId(String reservationId) {
      this.reservationId = reservationId;
      return this;
    }

    public Builder reservationAt(Instant reservationAt) {
      this.reservationAt = reservationAt;
      return this;
    }

    public Builder partySize(int partySize) {
      this.partySize = partySize;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public Builder sourceChannel(String sourceChannel) {
      this.sourceChannel = sourceChannel;
      return this;
    }

    public Builder partyName(String partyName) {
      this.partyName = partyName;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder cancelledAt(Instant cancelledAt) {
      this.cancelledAt = cancelledAt;
      return this;
    }

    public Builder noShowAt(Instant noShowAt) {
      this.noShowAt = noShowAt;
      return this;
    }

    public Builder seatedAt(Instant seatedAt) {
      this.seatedAt = seatedAt;
      return this;
    }

    public Builder finishedAt(Instant finishedAt) {
      this.finishedAt = finishedAt;
      return this;
    }

    public Builder lastUpdatedAt(Instant lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder totalActualDurationMinutes(Integer totalActualDurationMinutes) {
      this.totalActualDurationMinutes = totalActualDurationMinutes;
      return this;
    }

    public Builder tableType(String tableType) {
      this.tableType = tableType;
      return this;
    }

    public Builder diningAreaAssigned(String diningAreaAssigned) {
      this.diningAreaAssigned = diningAreaAssigned;
      return this;
    }

    public Builder diningAreaBooked(String diningAreaBooked) {
      this.diningAreaBooked = diningAreaBooked;
      return this;
    }

    public Builder diningAreaIdAssigned(String diningAreaIdAssigned) {
      this.diningAreaIdAssigned = diningAreaIdAssigned;
      return this;
    }

    public Builder diningAreaIdBooked(String diningAreaIdBooked) {
      this.diningAreaIdBooked = diningAreaIdBooked;
      return this;
    }

    public Builder waitStaff(String waitStaff) {
      this.waitStaff = waitStaff;
      return this;
    }

    public Builder createdBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder turnTimeSetting(String turnTimeSetting) {
      this.turnTimeSetting = turnTimeSetting;
      return this;
    }

    public Builder reservationType(String reservationType) {
      this.reservationType = reservationType;
      return this;
    }

    public Builder occasion(String occasion) {
      this.occasion = occasion;
      return this;
    }

    public Builder reservationStatus(String reservationStatus) {
      this.reservationStatus = reservationStatus;
      return this;
    }

    public Builder onlineWaitlist(Boolean onlineWaitlist) {
      this.onlineWaitlist = onlineWaitlist;
      return this;
    }

    public Builder isFlexReso(Boolean isFlexReso) {
      this.isFlexReso = isFlexReso;
      return this;
    }

    public Builder quotedWaitTime(Integer quotedWaitTime) {
      this.quotedWaitTime = quotedWaitTime;
      return this;
    }

    public Builder totalWaitTime(Integer totalWaitTime) {
      this.totalWaitTime = totalWaitTime;
      return this;
    }

    public Builder onlineChannel(String onlineChannel) {
      this.onlineChannel = onlineChannel;
      return this;
    }

    public Builder partner(String partner) {
      this.partner = partner;
      return this;
    }

    public Builder campaignType(String campaignType) {
      this.campaignType = campaignType;
      return this;
    }

    public Builder campaignDetails(String campaignDetails) {
      this.campaignDetails = campaignDetails;
      return this;
    }

    public Builder referrerName(String referrerName) {
      this.referrerName = referrerName;
      return this;
    }

    public Builder referrerBusiness(String referrerBusiness) {
      this.referrerBusiness = referrerBusiness;
      return this;
    }

    public Builder restrefSource(String restrefSource) {
      this.restrefSource = restrefSource;
      return this;
    }

    public Builder restrefCampaignName(String restrefCampaignName) {
      this.restrefCampaignName = restrefCampaignName;
      return this;
    }

    public Builder restrefId(String restrefId) {
      this.restrefId = restrefId;
      return this;
    }

    public Builder guestFirstName(String guestFirstName) {
      this.guestFirstName = guestFirstName;
      return this;
    }

    public Builder guestLastName(String guestLastName) {
      this.guestLastName = guestLastName;
      return this;
    }

    public Builder guestNickname(String guestNickname) {
      this.guestNickname = guestNickname;
      return this;
    }

    public Builder guestEmail(String guestEmail) {
      this.guestEmail = guestEmail;
      return this;
    }

    public Builder guestPhone(String guestPhone) {
      this.guestPhone = guestPhone;
      return this;
    }

    public Builder company(String company) {
      this.company = company;
      return this;
    }

    public Builder marketingOptIn(Boolean marketingOptIn) {
      this.marketingOptIn = marketingOptIn;
      return this;
    }

    public Builder birthday(LocalDate birthday) {
      this.birthday = birthday;
      return this;
    }

    public Builder anniversary(LocalDate anniversary) {
      this.anniversary = anniversary;
      return this;
    }

    public Builder firstVisitDate(LocalDate firstVisitDate) {
      this.firstVisitDate = firstVisitDate;
      return this;
    }

    public Builder recentVisitDate(LocalDate recentVisitDate) {
      this.recentVisitDate = recentVisitDate;
      return this;
    }

    public Builder upcomingVisits(Integer upcomingVisits) {
      this.upcomingVisits = upcomingVisits;
      return this;
    }

    public Builder completedVisits(Integer completedVisits) {
      this.completedVisits = completedVisits;
      return this;
    }

    public Builder cancelledCount(Integer cancelledCount) {
      this.cancelledCount = cancelledCount;
      return this;
    }

    public Builder noShowCount(Integer noShowCount) {
      this.noShowCount = noShowCount;
      return this;
    }

    public Builder lifetimeSpend(BigDecimal lifetimeSpend) {
      this.lifetimeSpend = lifetimeSpend;
      return this;
    }

    public Builder lifetimePerCoverSpend(BigDecimal lifetimePerCoverSpend) {
      this.lifetimePerCoverSpend = lifetimePerCoverSpend;
      return this;
    }

    public Builder guestId(UUID guestId) {
      this.guestId = guestId;
      return this;
    }

    public Builder experienceTitle(String experienceTitle) {
      this.experienceTitle = experienceTitle;
      return this;
    }

    public Builder experienceType(String experienceType) {
      this.experienceType = experienceType;
      return this;
    }

    public Builder experienceTypeEnum(String experienceTypeEnum) {
      this.experienceTypeEnum = experienceTypeEnum;
      return this;
    }

    public Builder experiencePriceType(String experiencePriceType) {
      this.experiencePriceType = experiencePriceType;
      return this;
    }

    public Builder prepaymentMethod(String prepaymentMethod) {
      this.prepaymentMethod = prepaymentMethod;
      return this;
    }

    public Builder prepaymentStatus(String prepaymentStatus) {
      this.prepaymentStatus = prepaymentStatus;
      return this;
    }

    public Builder vendorPayoutStatus(String vendorPayoutStatus) {
      this.vendorPayoutStatus = vendorPayoutStatus;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder experiencePrice(BigDecimal experiencePrice) {
      this.experiencePrice = experiencePrice;
      return this;
    }

    public Builder experienceSubtotal(BigDecimal experienceSubtotal) {
      this.experienceSubtotal = experienceSubtotal;
      return this;
    }

    public Builder addonsSubtotal(BigDecimal addonsSubtotal) {
      this.addonsSubtotal = addonsSubtotal;
      return this;
    }

    public Builder serviceCharges(BigDecimal serviceCharges) {
      this.serviceCharges = serviceCharges;
      return this;
    }

    public Builder prepaidExperienceTip(BigDecimal prepaidExperienceTip) {
      this.prepaidExperienceTip = prepaidExperienceTip;
      return this;
    }

    public Builder prepaidExperienceVat(BigDecimal prepaidExperienceVat) {
      this.prepaidExperienceVat = prepaidExperienceVat;
      return this;
    }

    public Builder prepaidExperienceTotalPaid(BigDecimal prepaidExperienceTotalPaid) {
      this.prepaidExperienceTotalPaid = prepaidExperienceTotalPaid;
      return this;
    }

    public Builder experienceTotalSales(BigDecimal experienceTotalSales) {
      this.experienceTotalSales = experienceTotalSales;
      return this;
    }

    public Builder experienceTotalSalesWithTip(BigDecimal experienceTotalSalesWithTip) {
      this.experienceTotalSalesWithTip = experienceTotalSalesWithTip;
      return this;
    }

    public Builder prepaymentRequiredTotal(BigDecimal prepaymentRequiredTotal) {
      this.prepaymentRequiredTotal = prepaymentRequiredTotal;
      return this;
    }

    public Builder prepaymentPaidTotal(BigDecimal prepaymentPaidTotal) {
      this.prepaymentPaidTotal = prepaymentPaidTotal;
      return this;
    }

    public Builder posCheckIds(String posCheckIds) {
      this.posCheckIds = posCheckIds;
      return this;
    }

    public Builder posSubtotal(BigDecimal posSubtotal) {
      this.posSubtotal = posSubtotal;
      return this;
    }

    public Builder posTax(BigDecimal posTax) {
      this.posTax = posTax;
      return this;
    }

    public Builder posServiceCharges(BigDecimal posServiceCharges) {
      this.posServiceCharges = posServiceCharges;
      return this;
    }

    public Builder posTip(BigDecimal posTip) {
      this.posTip = posTip;
      return this;
    }

    public Builder posPaid(BigDecimal posPaid) {
      this.posPaid = posPaid;
      return this;
    }

    public Builder posDue(BigDecimal posDue) {
      this.posDue = posDue;
      return this;
    }

    public Builder totalTips(BigDecimal totalTips) {
      this.totalTips = totalTips;
      return this;
    }

    public Builder totalVat(BigDecimal totalVat) {
      this.totalVat = totalVat;
      return this;
    }

    public Builder totalIncome(BigDecimal totalIncome) {
      this.totalIncome = totalIncome;
      return this;
    }

    public Builder totalIncomeWithTip(BigDecimal totalIncomeWithTip) {
      this.totalIncomeWithTip = totalIncomeWithTip;
      return this;
    }

    public Builder totalRevenue(BigDecimal totalRevenue) {
      this.totalRevenue = totalRevenue;
      return this;
    }

    public Builder totalRevenueWithTip(BigDecimal totalRevenueWithTip) {
      this.totalRevenueWithTip = totalRevenueWithTip;
      return this;
    }

    public Builder totalRevenueWithoutTip(BigDecimal totalRevenueWithoutTip) {
      this.totalRevenueWithoutTip = totalRevenueWithoutTip;
      return this;
    }

    public Builder posSpend(BigDecimal posSpend) {
      this.posSpend = posSpend;
      return this;
    }

    public Builder posRefund(BigDecimal posRefund) {
      this.posRefund = posRefund;
      return this;
    }

    public Builder posRevenue(BigDecimal posRevenue) {
      this.posRevenue = posRevenue;
      return this;
    }

    public Builder refundAmount(BigDecimal refundAmount) {
      this.refundAmount = refundAmount;
      return this;
    }

    public Builder vendorPayoutAmount(BigDecimal vendorPayoutAmount) {
      this.vendorPayoutAmount = vendorPayoutAmount;
      return this;
    }

    public Builder guestRequests(String guestRequests) {
      this.guestRequests = guestRequests;
      return this;
    }

    public Builder visitNotes(String visitNotes) {
      this.visitNotes = visitNotes;
      return this;
    }

    public Builder reservationTags(String reservationTags) {
      this.reservationTags = reservationTags;
      return this;
    }

    public Builder guestTags(String guestTags) {
      this.guestTags = guestTags;
      return this;
    }

    public OpenTableReservationDetail build() {
      return new OpenTableReservationDetail(
          reservationId,
          reservationAt,
          partySize,
          status,
          tableName,
          sourceChannel,
          partyName,
          createdAt,
          cancelledAt,
          noShowAt,
          seatedAt,
          finishedAt,
          lastUpdatedAt,
          totalActualDurationMinutes,
          tableType,
          diningAreaAssigned,
          diningAreaBooked,
          diningAreaIdAssigned,
          diningAreaIdBooked,
          waitStaff,
          createdBy,
          turnTimeSetting,
          reservationType,
          occasion,
          reservationStatus,
          onlineWaitlist,
          isFlexReso,
          quotedWaitTime,
          totalWaitTime,
          onlineChannel,
          partner,
          campaignType,
          campaignDetails,
          referrerName,
          referrerBusiness,
          restrefSource,
          restrefCampaignName,
          restrefId,
          guestFirstName,
          guestLastName,
          guestNickname,
          guestEmail,
          guestPhone,
          company,
          marketingOptIn,
          birthday,
          anniversary,
          firstVisitDate,
          recentVisitDate,
          upcomingVisits,
          completedVisits,
          cancelledCount,
          noShowCount,
          lifetimeSpend,
          lifetimePerCoverSpend,
          guestId,
          experienceTitle,
          experienceType,
          experienceTypeEnum,
          experiencePriceType,
          prepaymentMethod,
          prepaymentStatus,
          vendorPayoutStatus,
          currency,
          experiencePrice,
          experienceSubtotal,
          addonsSubtotal,
          serviceCharges,
          prepaidExperienceTip,
          prepaidExperienceVat,
          prepaidExperienceTotalPaid,
          experienceTotalSales,
          experienceTotalSalesWithTip,
          prepaymentRequiredTotal,
          prepaymentPaidTotal,
          posCheckIds,
          posSubtotal,
          posTax,
          posServiceCharges,
          posTip,
          posPaid,
          posDue,
          totalTips,
          totalVat,
          totalIncome,
          totalIncomeWithTip,
          totalRevenue,
          totalRevenueWithTip,
          totalRevenueWithoutTip,
          posSpend,
          posRefund,
          posRevenue,
          refundAmount,
          vendorPayoutAmount,
          guestRequests,
          visitNotes,
          reservationTags,
          guestTags);
    }
  }
}
