package com.goldys.platform.connectors.ctb;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(HybridInvoicePdfExtractor.class);

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
    // No per-supplier template produced lines; the LLM is the expensive fallback, so call it out.
    long started = System.currentTimeMillis();
    PdfExtractedInvoice out = llm.extract(text);
    log.info(
        "No deterministic invoice template matched; LLM fallback took {} ms",
        System.currentTimeMillis() - started);
    return sanitize(out);
  }

  /**
   * Applies {@link InvoicePdfSanitizer} to whichever extractor won: a label returned as the invoice
   * number becomes {@code null} (so the caller falls back to the filename join instead of matching
   * against a non-existent invoice), and annotation rows are pulled out of the line list and kept
   * as notes rather than silently discarded.
   */
  private static PdfExtractedInvoice sanitize(PdfExtractedInvoice out) {
    List<PdfExtractedLine> lines = new ArrayList<>();
    List<String> annotations = new ArrayList<>();
    for (PdfExtractedLine line : out.lines()) {
      if (InvoicePdfSanitizer.isAnnotation(line)) {
        if (line.description() != null && !line.description().isBlank()) {
          annotations.add(line.description().trim());
        }
      } else {
        lines.add(line);
      }
    }
    return new PdfExtractedInvoice(
        InvoicePdfSanitizer.invoiceNumber(out.invoiceNumber()),
        lines,
        out.extractionError(),
        annotations);
  }
}
