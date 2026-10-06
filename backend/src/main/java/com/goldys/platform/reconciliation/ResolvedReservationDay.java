package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One date/service-period's resolved reservation counts. Disposable projection; reconstructed from
 * {@code canonical_reservation} by {@link ReservationProjector}. Counts are raw daily totals;
 * ratios (avg party size, no-show rate, conversion) are derived in the semantic layer, not
 * persisted.
 */
@Entity
@Table(name = "resolved_reservation_day")
@IdClass(ResolvedReservationDay.Id.class)
class ResolvedReservationDay {
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @jakarta.persistence.Id
  @Column(name = "service_period", nullable = false)
  private String servicePeriod;

  @Column(name = "bookings", nullable = false)
  private long bookings;

  @Column(name = "attended", nullable = false)
  private long attended;

  @Column(name = "covers", nullable = false)
  private long covers;

  @Column(name = "cancelled", nullable = false)
  private long cancelled;

  @Column(name = "no_shows", nullable = false)
  private long noShows;

  @Column(name = "walk_ins", nullable = false)
  private long walkIns;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedReservationDay() {}

  ResolvedReservationDay(
      LocalDate tradingDate,
      String servicePeriod,
      long bookings,
      long attended,
      long covers,
      long cancelled,
      long noShows,
      long walkIns,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.servicePeriod = servicePeriod;
    this.bookings = bookings;
    this.attended = attended;
    this.covers = covers;
    this.cancelled = cancelled;
    this.noShows = noShows;
    this.walkIns = walkIns;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String servicePeriod() {
    return servicePeriod;
  }

  long bookings() {
    return bookings;
  }

  long attended() {
    return attended;
  }

  long covers() {
    return covers;
  }

  long cancelled() {
    return cancelled;
  }

  long noShows() {
    return noShows;
  }

  long walkIns() {
    return walkIns;
  }

  String resolutionType() {
    return resolutionType;
  }

  String authoritativeSource() {
    return authoritativeSource;
  }

  boolean hasConflict() {
    return hasConflict;
  }

  Instant resolvedAt() {
    return resolvedAt;
  }

  static class Id implements Serializable {
    private LocalDate tradingDate;
    private String servicePeriod;

    public Id() {}

    Id(LocalDate tradingDate, String servicePeriod) {
      this.tradingDate = tradingDate;
      this.servicePeriod = servicePeriod;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(servicePeriod, other.servicePeriod);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tradingDate, servicePeriod);
    }
  }
}
