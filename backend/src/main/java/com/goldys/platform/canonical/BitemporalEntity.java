package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * Base pattern every canonical entity extends (spec Requirement 2, pinned in system-context.md so
 * every module implements versioning the same way rather than each build session inventing its
 * own).
 *
 * <p>Two independent time axes, NOT one: - valid_from / valid_to -> VALID TIME: when the fact was
 * true in the real world - recorded_at / superseded_at -> SYSTEM TIME: when we recorded/superseded
 * it
 *
 * <p>Invariant: a canonical row is never UPDATEd in place when a later raw record supersedes it.
 * Close the old row (set supersededAt) and insert a new one. This is what makes "query as of a past
 * system time" and "recompute a resolution rule without losing history" possible - see spec
 * Requirement 2's acceptance criteria and the Reconciliation Engine's "recomputable resolved views"
 * invariant in system-context.md.
 */
@MappedSuperclass
public abstract class BitemporalEntity {

  @Id @GeneratedValue private UUID id;

  /** When the fact this row represents became true in the real world. */
  @Column(nullable = false)
  private Instant validFrom;

  /** When the fact stopped being true, or null if still current. */
  private Instant validTo;

  /** When this row was recorded (system time) - never changes after insert. */
  @Column(nullable = false, updatable = false)
  private Instant recordedAt;

  /** When this row was superseded by a newer row for the same entity, or null if current. */
  private Instant supersededAt;

  protected BitemporalEntity() {
    // JPA
  }

  protected BitemporalEntity(Instant validFrom, Instant recordedAt) {
    this.validFrom = validFrom;
    this.recordedAt = recordedAt;
  }

  public boolean isCurrent() {
    return supersededAt == null;
  }

  public void supersede(Instant when) {
    this.supersededAt = when;
  }

  public UUID getId() {
    return id;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidTo() {
    return validTo;
  }

  public void setValidTo(Instant validTo) {
    this.validTo = validTo;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public Instant getSupersededAt() {
    return supersededAt;
  }
}
