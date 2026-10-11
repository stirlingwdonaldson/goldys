package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the Infiniti Group invoice layout: a single header line followed by one
 * text line per line item. Each row is stock code, free-text description, unit, QTY, Sup, B/O, then
 * four money columns ({@code Each Ex}, {@code Total Ex Tax}, {@code GST}, {@code Total Inc}).
 * {@code amountExTax} is populated from the second money column ({@code Total Ex Tax}) — the value
 * the CTB CSV export omits for this supplier. Returns an empty invoice on any other layout so the
 * hybrid extractor falls through (spec §6).
 *
 * <p>This layout is one text row per item, so it deliberately does not extend {@link
 * LinearizedColumnPdfExtractor}, which assumes a fixed multi-line block per line item.
 */
@Component
@Order(0)
public class InfinitiGroupInvoicePdfExtractor implements DeterministicPdfExtractor {

  private static final Pattern HEADER =
      Pattern.compile(
          "Product\\s+Code\\s+Product\\s+Description\\s+Unit\\s+QTY\\s+Sup\\s+B/O\\s+"
              + "Each\\s+Ex\\s+Total\\s+Ex\\s+Tax\\s+Total\\s+Inc");

  private static final Pattern INVOICE_NUMBER =
      Pattern.compile("Tax\\s+Invoice\\s+#\\s*([A-Za-z0-9-]+)");

  /**
   * One item row. The description is free text (multi-word); it is pinned on the left by the stock
   * code and on the right by the trailing unit + three numeric cells (QTY/Sup/B/O) + four money
   * cells, so the greedy description consumes exactly the words between them.
   */
  private static final Pattern ROW =
      Pattern.compile(
          "^(\\S+)\\s+(.+)\\s+(\\S+)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+"
              + "(\\d+(?:\\.\\d+)?)\\s+\\$([\\d,]+(?:\\.\\d+)?)\\s+\\$([\\d,]+(?:\\.\\d+)?)\\s+"
              + "\\$([\\d,]+(?:\\.\\d+)?)\\s+\\$([\\d,]+(?:\\.\\d+)?)$");

  @Override
  public PdfExtractedInvoice extract(String text) {
    String invoiceNumber = invoiceNumber(text);
    Matcher header = HEADER.matcher(text);
    if (!header.find()) {
      return new PdfExtractedInvoice(invoiceNumber, List.of());
    }
    List<PdfExtractedLine> lines = new ArrayList<>();
    for (String row : text.substring(header.end()).split("\\R")) {
      String trimmed = row.trim();
      if (trimmed.isEmpty()) {
        continue; // blank separator, not a row
      }
      PdfExtractedLine line = mapRow(trimmed);
      if (line == null) {
        break; // the first non-matching row (e.g. the "Total Ex GST" footer) ends the table
      }
      lines.add(line);
    }
    return new PdfExtractedInvoice(invoiceNumber, lines);
  }

  private static PdfExtractedLine mapRow(String row) {
    Matcher m = ROW.matcher(row);
    if (!m.matches()) {
      return null;
    }
    BigDecimal quantity = number(m.group(4));
    BigDecimal amountExTax = money(m.group(8));
    if (quantity == null || amountExTax == null) {
      return null;
    }
    return new PdfExtractedLine(
        m.group(1).trim(),
        m.group(2).trim(),
        quantity,
        m.group(3).trim(),
        null,
        null,
        null,
        amountExTax);
  }

  private static BigDecimal number(String s) {
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal money(String s) {
    try {
      return new BigDecimal(s.replace(",", "").trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String invoiceNumber(String text) {
    Matcher m = INVOICE_NUMBER.matcher(text);
    return m.find() ? m.group(1) : null;
  }
}
