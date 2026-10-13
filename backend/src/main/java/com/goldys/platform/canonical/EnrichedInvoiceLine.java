package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current invoice line carrying the full enrichment field set (stock code,
 * UOM, per-pack unit quantity, pack size, WET) plus the PDF-parsed tax treatment (tax-inclusive
 * line total, GST amount, tax rate, GST-inclusive flag). Used by line-level reporting
 * (unit-cost-per-UOM, COGS-by-supplier/WET); CSV remains authoritative for quantity/unitCost/
 * lineTotal.
 */
public record EnrichedInvoiceLine(
    String invoiceNumber,
    LocalDate invoiceDate,
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount,
    BigDecimal lineTotalIncTax,
    BigDecimal lineGstAmount,
    BigDecimal lineTaxRate,
    Boolean incGst) {

  /** Convenience constructor for callers that do not carry tax treatment. */
  public EnrichedInvoiceLine(
      String invoiceNumber,
      LocalDate invoiceDate,
      String productNameKey,
      String stockCode,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount) {
    this(
        invoiceNumber,
        invoiceDate,
        productNameKey,
        stockCode,
        quantity,
        unitCost,
        lineTotal,
        uom,
        unitQuantity,
        packSize,
        wetAmount,
        null,
        null,
        null,
        null);
  }
}
