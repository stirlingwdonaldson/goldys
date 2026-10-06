package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** One date/department's resolved labour hours and cost. Disposable projection; reconstructed from
 * {@code canonical_labour_entry} by {@link LabourProjector}. Cost is null while unknown. */
@Entity
@Table(name = "resolved_labour_day")
@IdClass(ResolvedLabourDay.Id.class)
class ResolvedLabourDay {
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @jakarta.persistence.Id
  @Column(name = "department", nullable = false)
  private String department;

  @Column(name = "scheduled_hours", precision = 14, scale = 4)
  private BigDecimal scheduledHours;

  @Column(name = "actual_hours", precision = 14, scale = 4)
  private BigDecimal actualHours;

  @Column(name = "scheduled_cost", precision = 14, scale = 4)
  private BigDecimal scheduledCost;

  @Column(name = "actual_cost", precision = 14, scale = 4)
  private BigDecimal actualCost;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedLabourDay() {}

  ResolvedLabourDay(
      LocalDate tradingDate,
      String department,
      BigDecimal scheduledHours,
      BigDecimal actualHours,
      BigDecimal scheduledCost,
      BigDecimal actualCost,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.department = department;
    this.scheduledHours = scheduledHours;
    this.actualHours = actualHours;
    this.scheduledCost = scheduledCost;
    this.actualCost = actualCost;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String department() {
    return department;
  }

  BigDecimal scheduledHours() {
    return scheduledHours;
  }

  BigDecimal actualHours() {
    return actualHours;
  }

  BigDecimal scheduledCost() {
    return scheduledCost;
  }

  BigDecimal actualCost() {
    return actualCost;
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
    private String department;

    public Id() {}

    Id(LocalDate tradingDate, String department) {
      this.tradingDate = tradingDate;
      this.department = department;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(department, other.department);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tradingDate, department);
    }
  }
}
