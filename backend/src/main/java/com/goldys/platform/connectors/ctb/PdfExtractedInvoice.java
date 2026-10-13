package com.goldys.platform.connectors.ctb;

import java.util.List;

/**
 * An invoice's PDF text reduced to its enrichment-relevant lines.
 *
 * <p>{@code extractionError} is non-null only when the LLM fallback was attempted and failed (its
 * call threw or its output could not be parsed); deterministic extractors and a successful parse
 * leave it null. A null {@code extractionError} with empty {@code lines} means "no lines were
 * found", which is distinct from "the extractor failed".
 *
 * <p>{@code annotations} are the non-costable delivery-note rows the sanitizer pulled out of {@code
 * lines} (e.g. {@code "(2 BOXES)"}, {@code "COMMENT"}), so they can be kept as invoice notes rather
 * than silently discarded.
 */
public record PdfExtractedInvoice(
    String invoiceNumber,
    List<PdfExtractedLine> lines,
    String extractionError,
    List<String> annotations) {

  /** Convenience constructor for the success (or no-lines) case with no annotations. */
  public PdfExtractedInvoice(
      String invoiceNumber, List<PdfExtractedLine> lines, String extractionError) {
    this(invoiceNumber, lines, extractionError, List.of());
  }

  /** Convenience constructor for the common success (or no-lines) case. */
  public PdfExtractedInvoice(String invoiceNumber, List<PdfExtractedLine> lines) {
    this(invoiceNumber, lines, null, List.of());
  }
}
