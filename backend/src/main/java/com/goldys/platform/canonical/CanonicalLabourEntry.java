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
 * One version of a source's labour fact (a shift/timesheet entry). Fact fields are immutable; a
 * correction closes this row and inserts a successor sharing the same logical identity (the source
 * record ref). The field list is provisional until the Deputy payload schema is confirmed.
 */
@Entity
@Table(name = "canonical_labour_entry")
class CanonicalLabourEntry extends BitemporalEntity {
  @Column(name = "staff_ref", updatable = false)
  private String staffRef;

  @Column(name = "department", nullable = false, updatable = false)
  private String department;

  @Column(name = "labour_date", nullable = false, updatable = false)
  private LocalDate labourDate;

  @Column(name = "scheduled_hours", nullable = false, updatable = false, precision = 10, scale = 2)
  private BigDecimal scheduledHours;

  @Column(name = "actual_hours", nullable = false, updatable = false, precision = 10, scale = 2)
  private BigDecimal actualHours;

  @Column(name = "scheduled_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal scheduledCost;

  @Column(name = "actual_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal actualCost;

  @Column(name = "shift_start", updatable = false)
  private Instant shiftStart;

  @Column(name = "shift_end", updatable = false)
  private Instant shiftEnd;

  protected CanonicalLabourEntry() {}

  private CanonicalLabourEntry(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String staffRef,
      String department,
      LocalDate labourDate,
      BigDecimal scheduledHours,
      BigDecimal actualHours,
      BigDecimal scheduledCost,
      BigDecimal actualCost,
      Instant shiftStart,
      Instant shiftEnd) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.staffRef = staffRef;
    this.department = department;
    this.labourDate = labourDate;
    this.scheduledHours = scheduledHours;
    this.actualHours = actualHours;
    this.scheduledCost = scheduledCost;
    this.actualCost = actualCost;
    this.shiftStart = shiftStart;
    this.shiftEnd = shiftEnd;
  }

  static CanonicalLabourEntry create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String staffRef,
      String department,
      LocalDate labourDate,
      BigDecimal scheduledHours,
      BigDecimal actualHours,
      BigDecimal scheduledCost,
      BigDecimal actualCost,
      Instant shiftStart,
      Instant shiftEnd) {
    return new CanonicalLabourEntry(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        staffRef,
        department,
        labourDate,
        scheduledHours,
        actualHours,
        scheduledCost,
        actualCost,
        shiftStart,
        shiftEnd);
  }

  boolean sameFact(LabourInput input) {
    return Objects.equals(staffRef, input.staffRef())
        && Objects.equals(department, input.department())
        && Objects.equals(labourDate, input.labourDate())
        && sameAmount(scheduledHours, input.scheduledHours())
        && sameAmount(actualHours, input.actualHours())
        && sameAmount(scheduledCost, input.scheduledCost())
        && sameAmount(actualCost, input.actualCost())
        && Objects.equals(shiftStart, input.shiftStart())
        && Objects.equals(shiftEnd, input.shiftEnd());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  String staffRef() {
    return staffRef;
  }

  String department() {
    return department;
  }

  LocalDate labourDate() {
    return labourDate;
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

  Instant shiftStart() {
    return shiftStart;
  }

  Instant shiftEnd() {
    return shiftEnd;
  }
}
