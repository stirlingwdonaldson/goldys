package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** A line item parsed from an invoice PDF's text (enrichment only — never overrides CSV totals). */
public record PdfExtractedLine(
    String stockCode,
    String description,
    BigDecimal quantity,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount) {}
