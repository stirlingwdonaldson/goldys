package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared persistence shape for every bitemporal canonical entity.
 *
 * <p>Two independent time axes: valid time ({@code validFrom}/{@code validTo}, when the fact was
 * true) and system time ({@code recordedAt}/{@code supersededAt}, when we recorded or replaced it).
 * Fact and provenance fields are immutable; the only thing that ever changes on a row is {@code
 * supersededAt}, and only by closing it in favour of a successor.
 */
@MappedSuperclass
public abstract class BitemporalEntity {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "logical_entity_id", nullable = false, updatable = false)
  private UUID logicalEntityId;

  @Column(name = "source_system", nullable = false, updatable = false)
  private String sourceSystem;

  @Column(name = "source_record_ref", nullable = false, updatable = false)
  private String sourceRecordRef;

  @Column(name = "raw_record_id", nullable = false, updatable = false)
  private UUID rawRecordId;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private Instant validFrom;

  @Column(name = "valid_to", updatable = false)
  private Instant validTo;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected BitemporalEntity() {}

  protected BitemporalEntity(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.logicalEntityId = Objects.requireNonNull(logicalEntityId, "logicalEntityId");
    this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
    this.sourceRecordRef = Objects.requireNonNull(sourceRecordRef, "sourceRecordRef");
    this.rawRecordId = Objects.requireNonNull(rawRecordId, "rawRecordId");
    this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  /**
   * Closes this version as of {@code at}, in favour of a successor. The timestamp cannot precede
   * the moment this version was recorded, and a version can only be closed once.
   */
  void supersede(Instant at) {
    Objects.requireNonNull(at, "at");
    if (at.isBefore(recordedAt)) {
      throw new IllegalArgumentException("Supersession cannot precede recordedAt");
    }
    if (supersededAt != null) {
      throw new IllegalStateException("Version " + id + " is already superseded");
    }
    this.supersededAt = at;
  }

  UUID id() {
    return id;
  }

  UUID logicalEntityId() {
    return logicalEntityId;
  }

  String sourceSystem() {
    return sourceSystem;
  }

  String sourceRecordRef() {
    return sourceRecordRef;
  }

  UUID rawRecordId() {
    return rawRecordId;
  }

  Instant validFrom() {
    return validFrom;
  }

  Instant validTo() {
    return validTo;
  }

  Instant recordedAt() {
    return recordedAt;
  }

  Instant supersededAt() {
    return supersededAt;
  }
}
