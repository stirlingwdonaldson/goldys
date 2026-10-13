package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of a source's guest fact. Fact fields are immutable; a correction closes this row and
 * inserts a successor sharing the same logical identity (the OpenTable Guest ID).
 *
 * <p>Schema-only this slice: the reservation export carries no Guest ID, so the entity is not
 * populated until the reviews slice. No ingest/service/query facade exists yet.
 */
@Entity
@Table(name = "canonical_guest")
class CanonicalGuest extends BitemporalEntity {
  @Column(name = "guest_name", updatable = false, length = 1024)
  private String guestName;

  @Column(name = "first_name", updatable = false, length = 1024)
  private String firstName;

  @Column(name = "last_name", updatable = false, length = 1024)
  private String lastName;

  @Column(name = "nickname", updatable = false, length = 1024)
  private String nickname;

  @Column(name = "email", updatable = false, length = 1024)
  private String email;

  @Column(name = "phone", updatable = false, length = 1024)
  private String phone;

  @Column(name = "company", updatable = false, length = 1024)
  private String company;

  @Column(name = "marketing_opt_in", updatable = false)
  private Boolean marketingOptIn;

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

  protected CanonicalGuest() {}

  private CanonicalGuest(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String guestName,
      String firstName,
      String lastName,
      String nickname,
      String email,
      String phone,
      String company,
      Boolean marketingOptIn,
      LocalDate firstVisitDate,
      LocalDate recentVisitDate,
      Integer upcomingVisits,
      Integer completedVisits,
      Integer cancelledCount,
      Integer noShowCount,
      BigDecimal lifetimeSpend,
      BigDecimal lifetimePerCoverSpend) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.guestName = guestName;
    this.firstName = firstName;
    this.lastName = lastName;
    this.nickname = nickname;
    this.email = email;
    this.phone = phone;
    this.company = company;
    this.marketingOptIn = marketingOptIn;
    this.firstVisitDate = firstVisitDate;
    this.recentVisitDate = recentVisitDate;
    this.upcomingVisits = upcomingVisits;
    this.completedVisits = completedVisits;
    this.cancelledCount = cancelledCount;
    this.noShowCount = noShowCount;
    this.lifetimeSpend = lifetimeSpend;
    this.lifetimePerCoverSpend = lifetimePerCoverSpend;
  }

  static CanonicalGuest create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String guestName,
      String firstName,
      String lastName,
      String nickname,
      String email,
      String phone,
      String company,
      Boolean marketingOptIn,
      LocalDate firstVisitDate,
      LocalDate recentVisitDate,
      Integer upcomingVisits,
      Integer completedVisits,
      Integer cancelledCount,
      Integer noShowCount,
      BigDecimal lifetimeSpend,
      BigDecimal lifetimePerCoverSpend) {
    return new CanonicalGuest(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        guestName,
        firstName,
        lastName,
        nickname,
        email,
        phone,
        company,
        marketingOptIn,
        firstVisitDate,
        recentVisitDate,
        upcomingVisits,
        completedVisits,
        cancelledCount,
        noShowCount,
        lifetimeSpend,
        lifetimePerCoverSpend);
  }
}
