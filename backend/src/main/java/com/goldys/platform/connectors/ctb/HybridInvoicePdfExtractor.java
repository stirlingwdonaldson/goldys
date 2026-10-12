package com.goldys.platform.connectors.ctb;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The production {@link InvoicePdfExtractor}: tries each deterministic per-supplier template in
 * order, and falls back to the LLM when none of them produce any lines. This is the single bean
 * other code injects for PDF parsing (marked {@code @Primary} so it wins over the individual
 * templates and the LLM extractor, which are also {@link InvoicePdfExtractor}s).
 */
@Component
@Primary
public class HybridInvoicePdfExtractor implements InvoicePdfExtractor {

  private final List<DeterministicPdfExtractor> deterministic;
  private final LlmInvoicePdfExtractor llm;

  public HybridInvoicePdfExtractor(
      List<DeterministicPdfExtractor> deterministic, LlmInvoicePdfExtractor llm) {
    this.deterministic = deterministic;
    this.llm = llm;
  }

  @Override
  public PdfExtractedInvoice extract(String text) {
    for (DeterministicPdfExtractor template : deterministic) {
      PdfExtractedInvoice out = template.extract(text);
      if (!out.lines().isEmpty()) {
        return sanitize(out);
      }
    }
    return sanitize(llm.extract(text));
  }

  /**
   * Applies {@link InvoicePdfSanitizer} to whichever extractor won: a label returned as the invoice
   * number becomes {@code null} (so the caller falls back to the filename join instead of matching
   * against a non-existent invoice), and annotation rows are dropped before they can be flagged.
   */
  private static PdfExtractedInvoice sanitize(PdfExtractedInvoice out) {
    List<PdfExtractedLine> lines =
        out.lines().stream().filter(line -> !InvoicePdfSanitizer.isAnnotation(line)).toList();
    return new PdfExtractedInvoice(InvoicePdfSanitizer.invoiceNumber(out.invoiceNumber()), lines);
  }
}
