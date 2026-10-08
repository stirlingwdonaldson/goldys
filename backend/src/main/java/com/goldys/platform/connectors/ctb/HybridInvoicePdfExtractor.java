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
        return out;
      }
    }
    return llm.extract(text);
  }
}
