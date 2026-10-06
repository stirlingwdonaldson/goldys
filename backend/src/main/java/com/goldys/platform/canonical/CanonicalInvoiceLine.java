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
 * One version of an invoice line-item fact. Fact fields are immutable; a correction closes this row
 * and inserts a successor sharing the same logical identity.
 *
 * <p>{@code invoiceDate} is denormalized onto the line so COGS can be bucketed by date from lines
 * alone (a line ingested before its invoice metadata still carries its date). {@code invoiceNumber}
 * links the line to its {@link CanonicalInvoice} for audit/matching.
 */
@Entity
@Table(name = "canonical_invoice_line")
class CanonicalInvoiceLine extends BitemporalEntity {
  @Column(name = "invoice_number", nullable = false, updatable = false)
  private String invoiceNumber;

  @Column(name = "invoice_date", nullable = false, updatable = false)
  private LocalDate invoiceDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_cost", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "line_total", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal lineTotal;

  @Column(name = "category", updatable = false)
  private String category;

  protected CanonicalInvoiceLine() {}

  private CanonicalInvoiceLine(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String invoiceNumber,
      LocalDate invoiceDate,
      String productNameKey,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String category) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.invoiceNumber = invoiceNumber;
    this.invoiceDate = invoiceDate;
    this.productNameKey = productNameKey;
    this.quantity = quantity;
    this.unitCost = unitCost;
    this.lineTotal = lineTotal;
    this.category = category;
  }

  static CanonicalInvoiceLine create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String invoiceNumber,
      LocalDate invoiceDate,
      String productNameKey,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String category) {
    return new CanonicalInvoiceLine(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        invoiceNumber,
        invoiceDate,
        productNameKey,
        quantity,
        unitCost,
        lineTotal,
        category);
  }

  boolean sameFact(InvoiceLineInput input) {
    return Objects.equals(invoiceNumber, input.invoiceNumber())
        && Objects.equals(invoiceDate, input.invoiceDate())
        && Objects.equals(productNameKey, input.productNameKey())
        && sameAmount(quantity, input.quantity())
        && sameAmount(unitCost, input.unitCost())
        && sameAmount(lineTotal, input.lineTotal())
        && Objects.equals(category, input.category());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  String invoiceNumber() {
    return invoiceNumber;
  }

  LocalDate invoiceDate() {
    return invoiceDate;
  }

  String productNameKey() {
    return productNameKey;
  }

  BigDecimal quantity() {
    return quantity;
  }

  BigDecimal unitCost() {
    return unitCost;
  }

  BigDecimal lineTotal() {
    return lineTotal;
  }

  String category() {
    return category;
  }
}
