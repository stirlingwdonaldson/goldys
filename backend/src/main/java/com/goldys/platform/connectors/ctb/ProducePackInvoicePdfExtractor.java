package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the produce-invoice layout (e.g. Oranges & Lemons): the linearized QTY /
 * CODE / DESCRIPTION / UOM / PACK table, adding unit-of-measure and pack-size columns. Returns an
 * empty invoice on any other layout so the hybrid extractor falls through (spec §6).
 */
@Component
public class ProducePackInvoicePdfExtractor extends LinearizedColumnPdfExtractor {

  private static final Pattern HEADER =
      Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UOM\\s+PACK");

  public ProducePackInvoicePdfExtractor() {
    super(HEADER, 5);
  }

  @Override
  protected PdfExtractedLine mapRow(String[] cells) {
    BigDecimal quantity = number(cells[0]);
    if (quantity == null) {
      return null;
    }
    return new PdfExtractedLine(
        cells[1].trim(), cells[2].trim(), quantity, cells[3].trim(), null, number(cells[4]), null);
  }
}
