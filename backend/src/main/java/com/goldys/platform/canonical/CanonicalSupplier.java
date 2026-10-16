package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source-scoped supplier identity. The source's supplier code is the identity; the
 * human name is an attribute that may change, so a supplier rename supersedes this row and inserts
 * a successor sharing the same {@code logicalEntityId}. Two suppliers that happen to share a name
 * but have different source codes remain distinct entities — the name never implies identity.
 */
@Entity
@Table(name = "canonical_supplier")
class CanonicalSupplier extends BitemporalEntity {
  @Column(name = "supplier_code", updatable = false)
  private String supplierCode;

  @Column(name = "name", updatable = false)
  private String name;

  protected CanonicalSupplier() {}

  private CanonicalSupplier(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String supplierCode,
      String name) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.supplierCode = supplierCode;
    this.name = name;
  }

  static CanonicalSupplier create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String supplierCode,
      String name) {
    return new CanonicalSupplier(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        supplierCode,
        name);
  }

  boolean sameFact(SupplierInput input) {
    return Objects.equals(supplierCode, input.supplierCode())
        && Objects.equals(name, input.supplierName());
  }

  String supplierCode() {
    return supplierCode;
  }

  String name() {
    return name;
  }
}
