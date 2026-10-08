package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser for the "header-anchored table" invoice layout: a block of column headers
 * (QTY / CODE / DESCRIPTION / UNIT / UNIT PRICE, one per extracted line) followed by one 5-line
 * block per line item. Suppliers that linearize their table this way include Tim & Terry Oyster.
 * Unknown layouts return an empty invoice so the caller can fall back to the LLM (spec §6). This is
 * the FIRST template; more get added as more layouts are pinned.
 */
@Component
public class DeterministicInvoicePdfExtractor implements InvoicePdfExtractor {

  private static final Pattern INVOICE_NUMBER =
      Pattern.compile("Invoice Number\\s+([A-Za-z0-9]+)");

  private static final Pattern HEADER =
      Pattern.compile("QTY\\s+CODE\\s+DESCRIPTION\\s+UNIT\\s+UNIT PRICE");

  @Override
  public PdfExtractedInvoice extract(String text) {
    String invoiceNumber = match(text, INVOICE_NUMBER);

    Matcher header = HEADER.matcher(text);
    if (!header.find()) {
      return new PdfExtractedInvoice(invoiceNumber, List.of());
    }

    String[] rows = text.substring(header.end()).trim().split("\\R");
    List<PdfExtractedLine> lines = new ArrayList<>();
    for (int i = 0; i + 4 < rows.length; i += 5) {
      BigDecimal quantity = number(rows[i]);
      BigDecimal unitPrice = number(rows[i + 4]);
      if (quantity == null || unitPrice == null) {
        break; // a non-numeric row ends the table
      }
      lines.add(
          new PdfExtractedLine(
              rows[i + 1].trim(),
              rows[i + 2].trim(),
              quantity,
              rows[i + 3].trim(),
              null,
              null,
              null));
    }
    return new PdfExtractedInvoice(invoiceNumber, lines);
  }

  private static String match(String text, Pattern p) {
    Matcher m = p.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static BigDecimal number(String s) {
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
