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
 * One open reconciliation exception. Disposable projection; {@code detectedAt} is the time the
 * exception first opened and is preserved while it stays open.
 */
@Entity
@Table(name = "reconciliation_exception")
@IdClass(ReconciliationExceptionRow.Id.class)
class ReconciliationExceptionRow {
  @jakarta.persistence.Id
  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @jakarta.persistence.Id
  @Column(name = "entity_key", nullable = false)
  private String entityKey;

  @jakarta.persistence.Id
  @Column(name = "field_key", nullable = false)
  private String fieldKey;

  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "detected_at", nullable = false)
  private Instant detectedAt;

  protected ReconciliationExceptionRow() {}

  ReconciliationExceptionRow(
      String entityType,
      String entityKey,
      String fieldKey,
      LocalDate tradingDate,
      String status,
      Instant detectedAt) {
    this.entityType = entityType;
    this.entityKey = entityKey;
    this.fieldKey = fieldKey;
    this.tradingDate = tradingDate;
    this.status = status;
    this.detectedAt = detectedAt;
  }

  void changeStatus(String newStatus) {
    this.status = newStatus;
  }

  String entityType() {
    return entityType;
  }

  String entityKey() {
    return entityKey;
  }

  String fieldKey() {
    return fieldKey;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String status() {
    return status;
  }

  Instant detectedAt() {
    return detectedAt;
  }

  static class Id implements Serializable {
    private String entityType;
    private String entityKey;
    private String fieldKey;

    public Id() {}

    Id(String entityType, String entityKey, String fieldKey) {
      this.entityType = entityType;
      this.entityKey = entityKey;
      this.fieldKey = fieldKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(entityType, other.entityType)
          && Objects.equals(entityKey, other.entityKey)
          && Objects.equals(fieldKey, other.fieldKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entityType, entityKey, fieldKey);
    }
  }
}
