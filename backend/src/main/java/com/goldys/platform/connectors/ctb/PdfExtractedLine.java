package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/**
 * A line item parsed from an invoice PDF's text (enrichment only — never overrides CSV totals). The
 * {@code amountExTax} component also carries the ex-tax line amount used by gap recovery to
 * reconcile PDF lines against the invoice header.
 *
 * <p>The trailing tax components carry what the PDF prints about a line's tax treatment, so the
 * pipeline does not silently drop it: {@code amountIncTax} (the tax-inclusive line total), {@code
 * gstAmount} (the line's GST), {@code taxRate} (the percentage, e.g. {@code 10.0} for 10%) and
 * {@code incGst} (whether the line is priced GST-inclusive). All are nullable: a layout or the LLM
 * that does not print a value leaves it null rather than guessing.
 */
public record PdfExtractedLine(
    String stockCode,
    String description,
    BigDecimal quantity,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount,
    BigDecimal amountExTax,
    BigDecimal amountIncTax,
    BigDecimal gstAmount,
    BigDecimal taxRate,
    Boolean incGst) {

  /** Convenience constructor for callers that do not capture tax treatment. */
  public PdfExtractedLine(
      String stockCode,
      String description,
      BigDecimal quantity,
      String uom,
      BigDecimal unitQuantity,
      BigDecimal packSize,
      BigDecimal wetAmount,
      BigDecimal amountExTax) {
    this(
        stockCode,
        description,
        quantity,
        uom,
        unitQuantity,
        packSize,
        wetAmount,
        amountExTax,
        null,
        null,
        null,
        null);
  }
}
