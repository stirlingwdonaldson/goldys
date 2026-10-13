package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * One invoice line for the invoice graph or invoice detail; {@code uom} is null when not
 * PDF-enriched, and the trailing tax-treatment fields are null when the PDF did not print them (or
 * the caller does not need them, e.g. the supplier graph).
 */
public record LineGraphNode(
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String uom,
    BigDecimal lineTotalIncTax,
    BigDecimal lineGstAmount,
    BigDecimal lineTaxRate,
    Boolean incGst) {

  /** Convenience constructor for callers that do not carry tax treatment. */
  public LineGraphNode(
      String productNameKey,
      String stockCode,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal lineTotal,
      String uom) {
    this(productNameKey, stockCode, quantity, unitCost, lineTotal, uom, null, null, null, null);
  }
}
