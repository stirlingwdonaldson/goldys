package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
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

  protected CanonicalReservation() {}

  private CanonicalReservation(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Instant reservationAt,
      int partySize,
      String status,
      String tableName,
      String sourceChannel,
      String partyName) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.reservationAt = reservationAt;
    this.partySize = partySize;
    this.status = status;
    this.tableName = tableName;
    this.sourceChannel = sourceChannel;
    this.partyName = partyName;
  }

  static CanonicalReservation create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Instant reservationAt,
      int partySize,
      String status,
      String tableName,
      String sourceChannel,
      String partyName) {
    return new CanonicalReservation(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        reservationAt,
        partySize,
        status,
        tableName,
        sourceChannel,
        partyName);
  }

  boolean sameFact(ReservationInput input) {
    return Objects.equals(reservationAt, input.reservationAt())
        && partySize == input.partySize()
        && Objects.equals(status, input.status())
        && Objects.equals(tableName, input.tableName())
        && Objects.equals(sourceChannel, input.sourceChannel())
        && Objects.equals(partyName, input.partyName());
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
}
