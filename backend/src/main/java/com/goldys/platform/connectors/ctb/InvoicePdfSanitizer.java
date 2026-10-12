package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Cleans what the PDF extractors return before it reaches matching. Two failure modes measured in
 * production flags motivate it:
 *
 * <ul>
 *   <li>An invoice "number" that is really a header label (the LLM fallback returned {@code "Tax
 *       Invoice No"}). Matching against it can never succeed, so every line of that PDF was flagged
 *       as missing from the CSV.
 *   <li>Annotation rows parsed as line items ({@code "(2 BOXES)"}, {@code "COMMENT"}). They are
 *       notes on the delivery, not purchases, so they can never match a CSV line.
 * </ul>
 *
 * Both rules are deliberately narrow: a value is only dropped when it cannot be a real invoice
 * number or a costable line, so a genuine line is never hidden from the work queue.
 */
final class InvoicePdfSanitizer {

  /** A leading label the model sometimes keeps in front of the value ("Tax Invoice No: 123"). */
  private static final Pattern LEADING_LABEL =
      Pattern.compile(
          "^(?:tax\\s+)?invoice\\s*(?:number|no\\.?|#)?\\s*[:#]?\\s*", Pattern.CASE_INSENSITIVE);

  private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

  /** A description that is wholly parenthesised, e.g. "(2 BOXES)" or "(pick up saturday)". */
  private static final Pattern PARENTHESISED = Pattern.compile("^\\(.*\\)$");

  /** A description that is only a note marker, e.g. "COMMENT", "Notes:". */
  private static final Pattern NOTE_MARKER =
      Pattern.compile("^(?:comments?|notes?)\\s*:?$", Pattern.CASE_INSENSITIVE);

  private InvoicePdfSanitizer() {}

  /**
   * The invoice number with any leading label stripped, or {@code null} when what remains has no
   * digit. Every CTB invoice number seen so far carries at least one digit (e.g. {@code NU3379},
   * {@code 758262}), while a bare label ("Tax Invoice No") has none, so the digit rule rejects
   * labels without guessing at supplier formats.
   */
  static String invoiceNumber(String raw) {
    if (raw == null) {
      return null;
    }
    String value = LEADING_LABEL.matcher(raw.trim()).replaceFirst("").trim();
    if (value.isEmpty() || !HAS_DIGIT.matcher(value).find()) {
      return null;
    }
    return value;
  }

  /**
   * True when a parsed line is an annotation rather than a purchase: it has no stock code and no
   * amount, and either reads as a note ({@link #isAnnotationText}) or carries nothing else a CSV
   * line could be enriched with (no quantity, no unit of measure).
   */
  static boolean isAnnotation(PdfExtractedLine line) {
    if (!isBlank(line.stockCode()) || hasAmount(line.amountExTax())) {
      return false;
    }
    if (isAnnotationText(line.stockCode(), line.description())) {
      return true;
    }
    return line.quantity() == null && isBlank(line.uom());
  }

  /**
   * The text-only half of {@link #isAnnotation}, usable where only the stock code and description
   * survive (an open flag row): no stock code, and a blank, parenthesised or note-marker
   * description.
   */
  static boolean isAnnotationText(String stockCode, String description) {
    if (!isBlank(stockCode)) {
      return false;
    }
    if (isBlank(description)) {
      return true;
    }
    String d = description.trim();
    return PARENTHESISED.matcher(d).matches() || NOTE_MARKER.matcher(d).matches();
  }

  private static boolean hasAmount(BigDecimal amount) {
    return amount != null && amount.signum() != 0;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
