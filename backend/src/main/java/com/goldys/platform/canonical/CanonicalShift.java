package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One version of a canonical shift.
 *
 * <p>Fact fields are immutable; a correction closes this row and inserts a successor sharing the
 * same logical identity. Matching tolerances are deliberately absent until shift matching is
 * designed from Deputy/OpenTable samples.
 */
@Entity
@Table(name = "canonical_shift")
class CanonicalShift extends BitemporalEntity {
  @Column(name = "staff_member_ref", updatable = false)
  private UUID staffMemberRef;

  @Column(name = "shift_start", updatable = false)
  private Instant shiftStart;

  @Column(name = "shift_end", updatable = false)
  private Instant shiftEnd;

  protected CanonicalShift() {}

  private CanonicalShift(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID staffMemberRef,
      Instant shiftStart,
      Instant shiftEnd) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.staffMemberRef = staffMemberRef;
    this.shiftStart = shiftStart;
    this.shiftEnd = shiftEnd;
  }

  static CanonicalShift create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID staffMemberRef,
      Instant shiftStart,
      Instant shiftEnd) {
    return new CanonicalShift(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        staffMemberRef,
        shiftStart,
        shiftEnd);
  }

  UUID staffMemberRef() {
    return staffMemberRef;
  }

  Instant shiftStart() {
    return shiftStart;
  }

  Instant shiftEnd() {
    return shiftEnd;
  }
}
