package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the "header-anchored table" invoice layout: a block of column headers
 * (QTY / CODE / DESCRIPTION / UNIT / UNIT PRICE, one per extracted line) followed by one 5-line
 * block per line item. Suppliers that linearize their table this way include Tim & Terry Oyster.
 * Unknown layouts return an empty invoice so the caller can fall back to the LLM (spec §6).
 */
@Component
public class DeterministicInvoicePdfExtractor extends LinearizedColumnPdfExtractor {

  private static final Pattern HEADER =
      Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UNIT\\s+UNIT PRICE");

  public DeterministicInvoicePdfExtractor() {
    super(HEADER, 5);
  }

  @Override
  protected PdfExtractedLine mapRow(String[] cells) {
    BigDecimal quantity = number(cells[0]);
    BigDecimal unitPrice = number(cells[4]);
    if (quantity == null || unitPrice == null) {
      return null; // a non-numeric row ends the table
    }
    return new PdfExtractedLine(
        cells[1].trim(), cells[2].trim(), quantity, cells[3].trim(), null, null, null);
  }
}
