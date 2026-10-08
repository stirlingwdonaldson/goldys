package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the beverage-invoice layout (e.g. Sealane): the linearized QTY / CODE /
 * DESCRIPTION / UNIT PRICE table where the QTY cell is the coarse {@code "4 CTN / 48 EACH"} form.
 * Splits that cell into pack size (4), unit of measure (CTN) and per-pack unit quantity (48) — the
 * same mapping the CSV side applies (spec §6.1). Returns an empty invoice on any other layout so
 * the hybrid extractor falls through (spec §6).
 */
@Component
public class BeverageCaseInvoicePdfExtractor extends LinearizedColumnPdfExtractor {

  private static final Pattern HEADER = Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UNIT PRICE");

  /**
   * Matches "4 CTN / 48 EACH": group(1)=pack size, group(2)=outer unit, group(3)=per-pack count.
   */
  private static final Pattern CASE_QUANTITY =
      Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+(\\S+)\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s+(\\S+)");

  public BeverageCaseInvoicePdfExtractor() {
    super(HEADER, 4);
  }

  @Override
  protected PdfExtractedLine mapRow(String[] cells) {
    Matcher quantity = CASE_QUANTITY.matcher(cells[0].trim());
    BigDecimal unitPrice = number(cells[3]);
    if (!quantity.matches() || unitPrice == null) {
      return null;
    }
    return new PdfExtractedLine(
        cells[1].trim(),
        cells[2].trim(),
        number(quantity.group(1)),
        quantity.group(2),
        number(quantity.group(3)),
        null,
        null);
  }
}
