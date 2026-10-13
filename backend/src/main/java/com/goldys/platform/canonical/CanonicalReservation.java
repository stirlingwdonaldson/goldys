package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source's reservation fact. Fact fields are immutable; a correction closes this
 * row and inserts a successor sharing the same logical identity (the OpenTable reservation id).
 */
@Entity
@Table(name = "canonical_reservation")
class CanonicalReservation extends BitemporalEntity {
  @Column(name = "reservation_at", nullable = false, updatable = false)
  private Instant reservationAt;

  @Column(name = "party_size", nullable = false, updatable = false)
  private int partySize;

  @Column(name = "status", nullable = false, updatable = false)
  private String status;

  @Column(name = "table_name", updatable = false)
  private String tableName;

  @Column(name = "source_channel", updatable = false)
  private String sourceChannel;

  @Column(name = "party_name", updatable = false)
  private String partyName;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "cancelled_at", updatable = false)
  private Instant cancelledAt;

  @Column(name = "no_show_at", updatable = false)
  private Instant noShowAt;

  @Column(name = "seated_at", updatable = false)
  private Instant seatedAt;

  @Column(name = "finished_at", updatable = false)
  private Instant finishedAt;

  @Column(name = "last_updated_at", updatable = false)
  private Instant lastUpdatedAt;

  @Column(name = "total_actual_duration_minutes", updatable = false)
  private Integer totalActualDurationMinutes;

  @Column(name = "table_type", updatable = false, length = 1024)
  private String tableType;

  @Column(name = "dining_area_assigned", updatable = false, length = 1024)
  private String diningAreaAssigned;

  @Column(name = "dining_area_booked", updatable = false, length = 1024)
  private String diningAreaBooked;

  @Column(name = "dining_area_id_assigned", updatable = false, length = 1024)
  private String diningAreaIdAssigned;

  @Column(name = "dining_area_id_booked", updatable = false, length = 1024)
  private String diningAreaIdBooked;

  @Column(name = "wait_staff", updatable = false, length = 1024)
  private String waitStaff;

  @Column(name = "created_by", updatable = false, length = 1024)
  private String createdBy;

  @Column(name = "turn_time_setting", updatable = false, length = 1024)
  private String turnTimeSetting;

  @Column(name = "reservation_type", updatable = false, length = 1024)
  private String reservationType;

  @Column(name = "occasion", updatable = false, length = 1024)
  private String occasion;

  @Column(name = "reservation_status", updatable = false, length = 1024)
  private String reservationStatus;

  @Column(name = "online_waitlist", updatable = false)
  private Boolean onlineWaitlist;

  @Column(name = "is_flex_reso", updatable = false)
  private Boolean isFlexReso;

  @Column(name = "quoted_wait_time", updatable = false)
  private Integer quotedWaitTime;

  @Column(name = "total_wait_time", updatable = false)
  private Integer totalWaitTime;

  @Column(name = "online_channel", updatable = false, length = 1024)
  private String onlineChannel;

  @Column(name = "partner", updatable = false, length = 1024)
  private String partner;

  @Column(name = "campaign_type", updatable = false, length = 1024)
  private String campaignType;

  @Column(name = "campaign_details", updatable = false, length = 1024)
  private String campaignDetails;

  @Column(name = "referrer_name", updatable = false, length = 1024)
  private String referrerName;

  @Column(name = "referrer_business", updatable = false, length = 1024)
  private String referrerBusiness;

  @Column(name = "restref_source", updatable = false, length = 1024)
  private String restrefSource;

  @Column(name = "restref_campaign_name", updatable = false, length = 1024)
  private String restrefCampaignName;

  @Column(name = "restref_id", updatable = false, length = 1024)
  private String restrefId;

  @Column(name = "guest_first_name", updatable = false, length = 1024)
  private String guestFirstName;

  @Column(name = "guest_last_name", updatable = false, length = 1024)
  private String guestLastName;

  @Column(name = "guest_nickname", updatable = false, length = 1024)
  private String guestNickname;

  @Column(name = "guest_email", updatable = false, length = 1024)
  private String guestEmail;

  @Column(name = "guest_phone", updatable = false, length = 1024)
  private String guestPhone;

  @Column(name = "company", updatable = false, length = 1024)
  private String company;

  @Column(name = "marketing_opt_in", updatable = false)
  private Boolean marketingOptIn;

  @Column(name = "birthday", updatable = false)
  private LocalDate birthday;

  @Column(name = "anniversary", updatable = false)
  private LocalDate anniversary;

  @Column(name = "first_visit_date", updatable = false)
  private LocalDate firstVisitDate;

  @Column(name = "recent_visit_date", updatable = false)
  private LocalDate recentVisitDate;

  @Column(name = "upcoming_visits", updatable = false)
  private Integer upcomingVisits;

  @Column(name = "completed_visits", updatable = false)
  private Integer completedVisits;

  @Column(name = "cancelled_count", updatable = false)
  private Integer cancelledCount;

  @Column(name = "no_show_count", updatable = false)
  private Integer noShowCount;

  @Column(name = "lifetime_spend", updatable = false, precision = 19, scale = 4)
  private BigDecimal lifetimeSpend;

  @Column(name = "lifetime_per_cover_spend", updatable = false, precision = 19, scale = 4)
  private BigDecimal lifetimePerCoverSpend;

  @Column(name = "guest_id", updatable = false)
  private UUID guestId;

  @Column(name = "experience_title", updatable = false, length = 1024)
  private String experienceTitle;

  @Column(name = "experience_type", updatable = false, length = 1024)
  private String experienceType;

  @Column(name = "experience_type_enum", updatable = false, length = 1024)
  private String experienceTypeEnum;

  @Column(name = "experience_price_type", updatable = false, length = 1024)
  private String experiencePriceType;

  @Column(name = "prepayment_method", updatable = false, length = 1024)
  private String prepaymentMethod;

  @Column(name = "prepayment_status", updatable = false, length = 1024)
  private String prepaymentStatus;

  @Column(name = "vendor_payout_status", updatable = false, length = 1024)
  private String vendorPayoutStatus;

  @Column(name = "currency", updatable = false, length = 16)
  private String currency;

  @Column(name = "experience_price", updatable = false, precision = 19, scale = 4)
  private BigDecimal experiencePrice;

  @Column(name = "experience_subtotal", updatable = false, precision = 19, scale = 4)
  private BigDecimal experienceSubtotal;

  @Column(name = "addons_subtotal", updatable = false, precision = 19, scale = 4)
  private BigDecimal addonsSubtotal;

  @Column(name = "service_charges", updatable = false, precision = 19, scale = 4)
  private BigDecimal serviceCharges;

  @Column(name = "prepaid_experience_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal prepaidExperienceTip;

  @Column(name = "prepaid_experience_vat", updatable = false, precision = 19, scale = 4)
  private BigDecimal prepaidExperienceVat;

  @Column(name = "prepaid_experience_total_paid", updatable = false, precision = 19, scale = 4)
  private BigDecimal prepaidExperienceTotalPaid;

  @Column(name = "experience_total_sales", updatable = false, precision = 19, scale = 4)
  private BigDecimal experienceTotalSales;

  @Column(name = "experience_total_sales_with_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal experienceTotalSalesWithTip;

  @Column(name = "prepayment_required_total", updatable = false, precision = 19, scale = 4)
  private BigDecimal prepaymentRequiredTotal;

  @Column(name = "prepayment_paid_total", updatable = false, precision = 19, scale = 4)
  private BigDecimal prepaymentPaidTotal;

  @Column(name = "pos_check_ids", updatable = false, length = 1024)
  private String posCheckIds;

  @Column(name = "pos_subtotal", updatable = false, precision = 19, scale = 4)
  private BigDecimal posSubtotal;

  @Column(name = "pos_tax", updatable = false, precision = 19, scale = 4)
  private BigDecimal posTax;

  @Column(name = "pos_service_charges", updatable = false, precision = 19, scale = 4)
  private BigDecimal posServiceCharges;

  @Column(name = "pos_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal posTip;

  @Column(name = "pos_paid", updatable = false, precision = 19, scale = 4)
  private BigDecimal posPaid;

  @Column(name = "pos_due", updatable = false, precision = 19, scale = 4)
  private BigDecimal posDue;

  @Column(name = "total_tips", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalTips;

  @Column(name = "total_vat", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalVat;

  @Column(name = "total_income", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalIncome;

  @Column(name = "total_income_with_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalIncomeWithTip;

  @Column(name = "total_revenue", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalRevenue;

  @Column(name = "total_revenue_with_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalRevenueWithTip;

  @Column(name = "total_revenue_without_tip", updatable = false, precision = 19, scale = 4)
  private BigDecimal totalRevenueWithoutTip;

  @Column(name = "pos_spend", updatable = false, precision = 19, scale = 4)
  private BigDecimal posSpend;

  @Column(name = "pos_refund", updatable = false, precision = 19, scale = 4)
  private BigDecimal posRefund;

  @Column(name = "pos_revenue", updatable = false, precision = 19, scale = 4)
  private BigDecimal posRevenue;

  @Column(name = "refund_amount", updatable = false, precision = 19, scale = 4)
  private BigDecimal refundAmount;

  @Column(name = "vendor_payout_amount", updatable = false, precision = 19, scale = 4)
  private BigDecimal vendorPayoutAmount;

  @Column(name = "guest_requests", updatable = false, length = 1024)
  private String guestRequests;

  @Column(name = "visit_notes", updatable = false, length = 1024)
  private String visitNotes;

  @Column(name = "reservation_tags", updatable = false, length = 1024)
  private String reservationTags;

  @Column(name = "guest_tags", updatable = false, length = 1024)
  private String guestTags;

  protected CanonicalReservation() {}

  private CanonicalReservation(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      ReservationInput input) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.reservationAt = input.reservationAt();
    this.partySize = input.partySize();
    this.status = input.status();
    this.tableName = input.tableName();
    this.sourceChannel = input.sourceChannel();
    this.partyName = input.partyName();
    this.createdAt = input.createdAt();
    this.cancelledAt = input.cancelledAt();
    this.noShowAt = input.noShowAt();
    this.seatedAt = input.seatedAt();
    this.finishedAt = input.finishedAt();
    this.lastUpdatedAt = input.lastUpdatedAt();
    this.totalActualDurationMinutes = input.totalActualDurationMinutes();
    this.tableType = input.tableType();
    this.diningAreaAssigned = input.diningAreaAssigned();
    this.diningAreaBooked = input.diningAreaBooked();
    this.diningAreaIdAssigned = input.diningAreaIdAssigned();
    this.diningAreaIdBooked = input.diningAreaIdBooked();
    this.waitStaff = input.waitStaff();
    this.createdBy = input.createdBy();
    this.turnTimeSetting = input.turnTimeSetting();
    this.reservationType = input.reservationType();
    this.occasion = input.occasion();
    this.reservationStatus = input.reservationStatus();
    this.onlineWaitlist = input.onlineWaitlist();
    this.isFlexReso = input.isFlexReso();
    this.quotedWaitTime = input.quotedWaitTime();
    this.totalWaitTime = input.totalWaitTime();
    this.onlineChannel = input.onlineChannel();
    this.partner = input.partner();
    this.campaignType = input.campaignType();
    this.campaignDetails = input.campaignDetails();
    this.referrerName = input.referrerName();
    this.referrerBusiness = input.referrerBusiness();
    this.restrefSource = input.restrefSource();
    this.restrefCampaignName = input.restrefCampaignName();
    this.restrefId = input.restrefId();
    this.guestFirstName = input.guestFirstName();
    this.guestLastName = input.guestLastName();
    this.guestNickname = input.guestNickname();
    this.guestEmail = input.guestEmail();
    this.guestPhone = input.guestPhone();
    this.company = input.company();
    this.marketingOptIn = input.marketingOptIn();
    this.birthday = input.birthday();
    this.anniversary = input.anniversary();
    this.firstVisitDate = input.firstVisitDate();
    this.recentVisitDate = input.recentVisitDate();
    this.upcomingVisits = input.upcomingVisits();
    this.completedVisits = input.completedVisits();
    this.cancelledCount = input.cancelledCount();
    this.noShowCount = input.noShowCount();
    this.lifetimeSpend = input.lifetimeSpend();
    this.lifetimePerCoverSpend = input.lifetimePerCoverSpend();
    this.guestId = input.guestId();
    this.experienceTitle = input.experienceTitle();
    this.experienceType = input.experienceType();
    this.experienceTypeEnum = input.experienceTypeEnum();
    this.experiencePriceType = input.experiencePriceType();
    this.prepaymentMethod = input.prepaymentMethod();
    this.prepaymentStatus = input.prepaymentStatus();
    this.vendorPayoutStatus = input.vendorPayoutStatus();
    this.currency = input.currency();
    this.experiencePrice = input.experiencePrice();
    this.experienceSubtotal = input.experienceSubtotal();
    this.addonsSubtotal = input.addonsSubtotal();
    this.serviceCharges = input.serviceCharges();
    this.prepaidExperienceTip = input.prepaidExperienceTip();
    this.prepaidExperienceVat = input.prepaidExperienceVat();
    this.prepaidExperienceTotalPaid = input.prepaidExperienceTotalPaid();
    this.experienceTotalSales = input.experienceTotalSales();
    this.experienceTotalSalesWithTip = input.experienceTotalSalesWithTip();
    this.prepaymentRequiredTotal = input.prepaymentRequiredTotal();
    this.prepaymentPaidTotal = input.prepaymentPaidTotal();
    this.posCheckIds = input.posCheckIds();
    this.posSubtotal = input.posSubtotal();
    this.posTax = input.posTax();
    this.posServiceCharges = input.posServiceCharges();
    this.posTip = input.posTip();
    this.posPaid = input.posPaid();
    this.posDue = input.posDue();
    this.totalTips = input.totalTips();
    this.totalVat = input.totalVat();
    this.totalIncome = input.totalIncome();
    this.totalIncomeWithTip = input.totalIncomeWithTip();
    this.totalRevenue = input.totalRevenue();
    this.totalRevenueWithTip = input.totalRevenueWithTip();
    this.totalRevenueWithoutTip = input.totalRevenueWithoutTip();
    this.posSpend = input.posSpend();
    this.posRefund = input.posRefund();
    this.posRevenue = input.posRevenue();
    this.refundAmount = input.refundAmount();
    this.vendorPayoutAmount = input.vendorPayoutAmount();
    this.guestRequests = input.guestRequests();
    this.visitNotes = input.visitNotes();
    this.reservationTags = input.reservationTags();
    this.guestTags = input.guestTags();
  }

  static CanonicalReservation create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      ReservationInput input) {
    return new CanonicalReservation(
        logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt, input);
  }

  boolean sameFact(ReservationInput input) {
    return Objects.equals(reservationAt, input.reservationAt())
        && partySize == input.partySize()
        && Objects.equals(status, input.status())
        && Objects.equals(tableName, input.tableName())
        && Objects.equals(sourceChannel, input.sourceChannel())
        && Objects.equals(partyName, input.partyName())
        && Objects.equals(createdAt, input.createdAt())
        && Objects.equals(cancelledAt, input.cancelledAt())
        && Objects.equals(noShowAt, input.noShowAt())
        && Objects.equals(seatedAt, input.seatedAt())
        && Objects.equals(finishedAt, input.finishedAt())
        && Objects.equals(lastUpdatedAt, input.lastUpdatedAt())
        && Objects.equals(totalActualDurationMinutes, input.totalActualDurationMinutes())
        && Objects.equals(tableType, input.tableType())
        && Objects.equals(diningAreaAssigned, input.diningAreaAssigned())
        && Objects.equals(diningAreaBooked, input.diningAreaBooked())
        && Objects.equals(diningAreaIdAssigned, input.diningAreaIdAssigned())
        && Objects.equals(diningAreaIdBooked, input.diningAreaIdBooked())
        && Objects.equals(waitStaff, input.waitStaff())
        && Objects.equals(createdBy, input.createdBy())
        && Objects.equals(turnTimeSetting, input.turnTimeSetting())
        && Objects.equals(reservationType, input.reservationType())
        && Objects.equals(occasion, input.occasion())
        && Objects.equals(reservationStatus, input.reservationStatus())
        && Objects.equals(onlineWaitlist, input.onlineWaitlist())
        && Objects.equals(isFlexReso, input.isFlexReso())
        && Objects.equals(quotedWaitTime, input.quotedWaitTime())
        && Objects.equals(totalWaitTime, input.totalWaitTime())
        && Objects.equals(onlineChannel, input.onlineChannel())
        && Objects.equals(partner, input.partner())
        && Objects.equals(campaignType, input.campaignType())
        && Objects.equals(campaignDetails, input.campaignDetails())
        && Objects.equals(referrerName, input.referrerName())
        && Objects.equals(referrerBusiness, input.referrerBusiness())
        && Objects.equals(restrefSource, input.restrefSource())
        && Objects.equals(restrefCampaignName, input.restrefCampaignName())
        && Objects.equals(restrefId, input.restrefId())
        && Objects.equals(guestFirstName, input.guestFirstName())
        && Objects.equals(guestLastName, input.guestLastName())
        && Objects.equals(guestNickname, input.guestNickname())
        && Objects.equals(guestEmail, input.guestEmail())
        && Objects.equals(guestPhone, input.guestPhone())
        && Objects.equals(company, input.company())
        && Objects.equals(marketingOptIn, input.marketingOptIn())
        && Objects.equals(birthday, input.birthday())
        && Objects.equals(anniversary, input.anniversary())
        && Objects.equals(firstVisitDate, input.firstVisitDate())
        && Objects.equals(recentVisitDate, input.recentVisitDate())
        && Objects.equals(upcomingVisits, input.upcomingVisits())
        && Objects.equals(completedVisits, input.completedVisits())
        && Objects.equals(cancelledCount, input.cancelledCount())
        && Objects.equals(noShowCount, input.noShowCount())
        && sameAmount(lifetimeSpend, input.lifetimeSpend())
        && sameAmount(lifetimePerCoverSpend, input.lifetimePerCoverSpend())
        && Objects.equals(guestId, input.guestId())
        && Objects.equals(experienceTitle, input.experienceTitle())
        && Objects.equals(experienceType, input.experienceType())
        && Objects.equals(experienceTypeEnum, input.experienceTypeEnum())
        && Objects.equals(experiencePriceType, input.experiencePriceType())
        && Objects.equals(prepaymentMethod, input.prepaymentMethod())
        && Objects.equals(prepaymentStatus, input.prepaymentStatus())
        && Objects.equals(vendorPayoutStatus, input.vendorPayoutStatus())
        && Objects.equals(currency, input.currency())
        && sameAmount(experiencePrice, input.experiencePrice())
        && sameAmount(experienceSubtotal, input.experienceSubtotal())
        && sameAmount(addonsSubtotal, input.addonsSubtotal())
        && sameAmount(serviceCharges, input.serviceCharges())
        && sameAmount(prepaidExperienceTip, input.prepaidExperienceTip())
        && sameAmount(prepaidExperienceVat, input.prepaidExperienceVat())
        && sameAmount(prepaidExperienceTotalPaid, input.prepaidExperienceTotalPaid())
        && sameAmount(experienceTotalSales, input.experienceTotalSales())
        && sameAmount(experienceTotalSalesWithTip, input.experienceTotalSalesWithTip())
        && sameAmount(prepaymentRequiredTotal, input.prepaymentRequiredTotal())
        && sameAmount(prepaymentPaidTotal, input.prepaymentPaidTotal())
        && Objects.equals(posCheckIds, input.posCheckIds())
        && sameAmount(posSubtotal, input.posSubtotal())
        && sameAmount(posTax, input.posTax())
        && sameAmount(posServiceCharges, input.posServiceCharges())
        && sameAmount(posTip, input.posTip())
        && sameAmount(posPaid, input.posPaid())
        && sameAmount(posDue, input.posDue())
        && sameAmount(totalTips, input.totalTips())
        && sameAmount(totalVat, input.totalVat())
        && sameAmount(totalIncome, input.totalIncome())
        && sameAmount(totalIncomeWithTip, input.totalIncomeWithTip())
        && sameAmount(totalRevenue, input.totalRevenue())
        && sameAmount(totalRevenueWithTip, input.totalRevenueWithTip())
        && sameAmount(totalRevenueWithoutTip, input.totalRevenueWithoutTip())
        && sameAmount(posSpend, input.posSpend())
        && sameAmount(posRefund, input.posRefund())
        && sameAmount(posRevenue, input.posRevenue())
        && sameAmount(refundAmount, input.refundAmount())
        && sameAmount(vendorPayoutAmount, input.vendorPayoutAmount())
        && Objects.equals(guestRequests, input.guestRequests())
        && Objects.equals(visitNotes, input.visitNotes())
        && Objects.equals(reservationTags, input.reservationTags())
        && Objects.equals(guestTags, input.guestTags());
  }

  // Money fields are compared by value: Postgres numeric(19,4) normalizes the scale, so a
  // re-upload of the same amount with a different scale must still count as the same fact.
  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  Instant reservationAt() {
    return reservationAt;
  }

  int partySize() {
    return partySize;
  }

  String status() {
    return status;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant cancelledAt() {
    return cancelledAt;
  }

  Instant noShowAt() {
    return noShowAt;
  }

  Instant seatedAt() {
    return seatedAt;
  }

  Instant finishedAt() {
    return finishedAt;
  }

  Instant lastUpdatedAt() {
    return lastUpdatedAt;
  }

  Integer totalActualDurationMinutes() {
    return totalActualDurationMinutes;
  }

  String tableType() {
    return tableType;
  }

  String diningAreaAssigned() {
    return diningAreaAssigned;
  }

  String diningAreaBooked() {
    return diningAreaBooked;
  }

  String diningAreaIdAssigned() {
    return diningAreaIdAssigned;
  }

  String diningAreaIdBooked() {
    return diningAreaIdBooked;
  }

  String waitStaff() {
    return waitStaff;
  }

  String createdBy() {
    return createdBy;
  }

  String turnTimeSetting() {
    return turnTimeSetting;
  }

  String reservationType() {
    return reservationType;
  }

  String occasion() {
    return occasion;
  }

  String reservationStatus() {
    return reservationStatus;
  }

  Boolean onlineWaitlist() {
    return onlineWaitlist;
  }

  Boolean isFlexReso() {
    return isFlexReso;
  }

  Integer quotedWaitTime() {
    return quotedWaitTime;
  }

  Integer totalWaitTime() {
    return totalWaitTime;
  }

  String onlineChannel() {
    return onlineChannel;
  }

  String partner() {
    return partner;
  }

  String campaignType() {
    return campaignType;
  }

  String campaignDetails() {
    return campaignDetails;
  }

  String referrerName() {
    return referrerName;
  }

  String referrerBusiness() {
    return referrerBusiness;
  }

  String restrefSource() {
    return restrefSource;
  }

  String restrefCampaignName() {
    return restrefCampaignName;
  }

  String restrefId() {
    return restrefId;
  }

  String guestFirstName() {
    return guestFirstName;
  }

  String guestLastName() {
    return guestLastName;
  }

  String guestNickname() {
    return guestNickname;
  }

  String guestEmail() {
    return guestEmail;
  }

  String guestPhone() {
    return guestPhone;
  }

  String company() {
    return company;
  }

  Boolean marketingOptIn() {
    return marketingOptIn;
  }

  LocalDate birthday() {
    return birthday;
  }

  LocalDate anniversary() {
    return anniversary;
  }

  LocalDate firstVisitDate() {
    return firstVisitDate;
  }

  LocalDate recentVisitDate() {
    return recentVisitDate;
  }

  Integer upcomingVisits() {
    return upcomingVisits;
  }

  Integer completedVisits() {
    return completedVisits;
  }

  Integer cancelledCount() {
    return cancelledCount;
  }

  Integer noShowCount() {
    return noShowCount;
  }

  BigDecimal lifetimeSpend() {
    return lifetimeSpend;
  }

  BigDecimal lifetimePerCoverSpend() {
    return lifetimePerCoverSpend;
  }

  UUID guestId() {
    return guestId;
  }

  String experienceTitle() {
    return experienceTitle;
  }

  String experienceType() {
    return experienceType;
  }

  String experienceTypeEnum() {
    return experienceTypeEnum;
  }

  String experiencePriceType() {
    return experiencePriceType;
  }

  String prepaymentMethod() {
    return prepaymentMethod;
  }

  String prepaymentStatus() {
    return prepaymentStatus;
  }

  String vendorPayoutStatus() {
    return vendorPayoutStatus;
  }

  String currency() {
    return currency;
  }

  BigDecimal experiencePrice() {
    return experiencePrice;
  }

  BigDecimal experienceSubtotal() {
    return experienceSubtotal;
  }

  BigDecimal addonsSubtotal() {
    return addonsSubtotal;
  }

  BigDecimal serviceCharges() {
    return serviceCharges;
  }

  BigDecimal prepaidExperienceTip() {
    return prepaidExperienceTip;
  }

  BigDecimal prepaidExperienceVat() {
    return prepaidExperienceVat;
  }

  BigDecimal prepaidExperienceTotalPaid() {
    return prepaidExperienceTotalPaid;
  }

  BigDecimal experienceTotalSales() {
    return experienceTotalSales;
  }

  BigDecimal experienceTotalSalesWithTip() {
    return experienceTotalSalesWithTip;
  }

  BigDecimal prepaymentRequiredTotal() {
    return prepaymentRequiredTotal;
  }

  BigDecimal prepaymentPaidTotal() {
    return prepaymentPaidTotal;
  }

  String posCheckIds() {
    return posCheckIds;
  }

  BigDecimal posSubtotal() {
    return posSubtotal;
  }

  BigDecimal posTax() {
    return posTax;
  }

  BigDecimal posServiceCharges() {
    return posServiceCharges;
  }

  BigDecimal posTip() {
    return posTip;
  }

  BigDecimal posPaid() {
    return posPaid;
  }

  BigDecimal posDue() {
    return posDue;
  }

  BigDecimal totalTips() {
    return totalTips;
  }

  BigDecimal totalVat() {
    return totalVat;
  }

  BigDecimal totalIncome() {
    return totalIncome;
  }

  BigDecimal totalIncomeWithTip() {
    return totalIncomeWithTip;
  }

  BigDecimal totalRevenue() {
    return totalRevenue;
  }

  BigDecimal totalRevenueWithTip() {
    return totalRevenueWithTip;
  }

  BigDecimal totalRevenueWithoutTip() {
    return totalRevenueWithoutTip;
  }

  BigDecimal posSpend() {
    return posSpend;
  }

  BigDecimal posRefund() {
    return posRefund;
  }

  BigDecimal posRevenue() {
    return posRevenue;
  }

  BigDecimal refundAmount() {
    return refundAmount;
  }

  BigDecimal vendorPayoutAmount() {
    return vendorPayoutAmount;
  }

  String guestRequests() {
    return guestRequests;
  }

  String visitNotes() {
    return visitNotes;
  }

  String reservationTags() {
    return reservationTags;
  }

  String guestTags() {
    return guestTags;
  }
}
