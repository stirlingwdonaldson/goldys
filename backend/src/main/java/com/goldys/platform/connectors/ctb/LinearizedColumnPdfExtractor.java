package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared shape for deterministic templates that parse a "linearized" invoice table: a block of
 * column headers (one per extracted line) followed by a fixed number of lines per line item.
 * Subclasses supply the header pattern + column count and map each row's cells to a line. A
 * non-matching header returns an empty invoice so the hybrid extractor falls through to the next
 * template or the LLM (spec §6).
 */
abstract class LinearizedColumnPdfExtractor implements DeterministicPdfExtractor {

  private static final Pattern INVOICE_NUMBER =
      Pattern.compile("Invoice\\s+(?:Number|No|#)\\s*:?\\s*([A-Za-z0-9-]+)");

  private final Pattern header;
  private final int columnsPerRow;

  protected LinearizedColumnPdfExtractor(Pattern header, int columnsPerRow) {
    this.header = header;
    this.columnsPerRow = columnsPerRow;
  }

  @Override
  public final PdfExtractedInvoice extract(String text) {
    String invoiceNumber = invoiceNumber(text);
    Matcher m = header.matcher(text);
    if (!m.find()) {
      return new PdfExtractedInvoice(invoiceNumber, List.of());
    }
    String[] rows = text.substring(m.end()).trim().split("\\R");
    List<PdfExtractedLine> lines = new ArrayList<>();
    for (int i = 0; i + columnsPerRow <= rows.length; i += columnsPerRow) {
      String[] cells = Arrays.copyOfRange(rows, i, i + columnsPerRow);
      PdfExtractedLine line = mapRow(cells);
      if (line == null) {
        break; // a non-matching row ends the table
      }
      lines.add(line);
    }
    return new PdfExtractedInvoice(invoiceNumber, lines);
  }

  /** Maps one row's cells to a line, or returns {@code null} to stop (a non-matching row). */
  protected abstract PdfExtractedLine mapRow(String[] cells);

  protected static BigDecimal number(String s) {
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String invoiceNumber(String text) {
    Matcher m = INVOICE_NUMBER.matcher(text);
    return m.find() ? m.group(1) : null;
  }
}
