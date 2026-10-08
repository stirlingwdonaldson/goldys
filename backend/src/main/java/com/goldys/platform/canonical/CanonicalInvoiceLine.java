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
 * links the line to its {@link CanonicalInvoice} for audit/matching. The {@code stockCode} / {@code
 * uom} / {@code unitQuantity} / {@code packSize} / {@code wetAmount} fields are PDF enrichment
 * (nullable; CSV stays authoritative for quantity/unitCost/lineTotal).
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

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_cost", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "line_total", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal lineTotal;

  @Column(name = "category", updatable = false)
  private String category;

  @Column(name = "uom", updatable = false, length = 32)
  private String uom;

  @Column(name = "unit_quantity", updatable = false, precision = 14, scale = 4)
  private BigDecimal unitQuantity;

  @Column(name = "pack_size", updatable = false, precision = 14, scale = 4)
  private BigDecimal packSize;

  @Column(name = "wet_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal wetAmount;

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
      String stockCode,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String category,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.invoiceNumber = invoiceNumber;
    this.invoiceDate = invoiceDate;
    this.productNameKey = productNameKey;
    this.stockCode = stockCode;
    this.quantity = quantity;
    this.unitCost = unitCost;
    this.lineTotal = lineTotal;
    this.category = category;
    this.uom = uom;
    this.unitQuantity = unitQuantity;
    this.packSize = packSize;
    this.wetAmount = wetAmount;
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
      String stockCode,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String category,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {
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
        stockCode,
        quantity,
        unitCost,
        lineTotal,
        category,
        uom,
        unitQuantity,
        packSize,
        wetAmount);
  }

  boolean sameFact(InvoiceLineInput input) {
    return Objects.equals(invoiceNumber, input.invoiceNumber())
        && Objects.equals(invoiceDate, input.invoiceDate())
        && Objects.equals(productNameKey, input.productNameKey())
        && Objects.equals(stockCode, input.stockCode())
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

  String stockCode() {
    return stockCode;
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

  String uom() {
    return uom;
  }

  BigDecimal unitQuantity() {
    return unitQuantity;
  }

  BigDecimal packSize() {
    return packSize;
  }

  BigDecimal wetAmount() {
    return wetAmount;
  }
}
