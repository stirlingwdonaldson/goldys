package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the liquor-invoice layout (e.g. Paramount Liquor): the linearized QTY /
 * CODE / DESCRIPTION / UNIT / UNIT PRICE / WET table, adding the Wine Equalisation Tax column.
 * Returns an empty invoice on any other layout so the hybrid extractor falls through (spec §6).
 */
@Component
public class LiquorWetInvoicePdfExtractor extends LinearizedColumnPdfExtractor {

  private static final Pattern HEADER =
      Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UNIT\\s+UNIT PRICE\\s+WET");

  public LiquorWetInvoicePdfExtractor() {
    super(HEADER, 6);
  }

  @Override
  protected PdfExtractedLine mapRow(String[] cells) {
    BigDecimal quantity = number(cells[0]);
    BigDecimal unitPrice = number(cells[4]);
    if (quantity == null || unitPrice == null) {
      return null;
    }
    return new PdfExtractedLine(
        cells[1].trim(), cells[2].trim(), quantity, cells[3].trim(), null, null, number(cells[5]));
  }
}
