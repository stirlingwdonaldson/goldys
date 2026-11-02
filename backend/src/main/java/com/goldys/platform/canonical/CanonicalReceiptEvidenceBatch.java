package com.goldys.platform.canonical;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable observation aggregate. Omitted rows never retract a previous observation. */
@Entity
@Table(name = "canonical_receipt_evidence_batch")
class CanonicalReceiptEvidenceBatch extends BitemporalEntity {
  @Column(nullable = false, updatable = false)
  private String kind;

  @Column(name = "scope_raw_id", nullable = false, updatable = false)
  private UUID scopeRawId;

  @Column(name = "site_number", nullable = false, updatable = false)
  private long siteNumber;

  @Column(name = "site_name", nullable = false, updatable = false)
  private String siteName;

  @Column(name = "window_from", nullable = false, updatable = false)
  private LocalDate windowFrom;

  @Column(name = "window_to", nullable = false, updatable = false)
  private LocalDate windowTo;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mapping_rows", columnDefinition = "jsonb", nullable = false, updatable = false)
  private List<ReceiptMappingInput> mappingRows;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "transaction_rows",
      columnDefinition = "jsonb",
      nullable = false,
      updatable = false)
  private List<SaleTransactionInput> transactionRows;

  protected CanonicalReceiptEvidenceBatch() {}

  CanonicalReceiptEvidenceBatch(
      String kind,
      UUID rawId,
      UUID scopeRawId,
      ReceiptImportScope scope,
      List<ReceiptMappingInput> mappings,
      List<SaleTransactionInput> transactions) {
    // Valid time is the observation time: source-effective revision ordering is unverified.
    super(rawId, "LIGHTSPEED", kind + ":" + rawId, rawId, Instant.now(), Instant.now());
    this.kind = kind;
    this.scopeRawId = scopeRawId;
    siteNumber = scope.siteNumber();
    siteName = scope.siteName();
    windowFrom = scope.from();
    windowTo = scope.to();
    mappingRows = List.copyOf(mappings);
    transactionRows = List.copyOf(transactions);
  }
}
