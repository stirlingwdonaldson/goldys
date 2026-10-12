package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/**
 * A line item parsed from an invoice PDF's text (enrichment only — never overrides CSV totals). The
 * {@code amountExTax} component also carries the ex-tax line amount used by gap recovery to
 * reconcile PDF lines against the invoice header.
 */
public record PdfExtractedLine(
    String stockCode,
    String description,
    BigDecimal quantity,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount,
    BigDecimal amountExTax) {}
