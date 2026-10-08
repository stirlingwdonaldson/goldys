package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridInvoicePdfExtractorTest {

  @Test
  void returnsTheDeterministicResultWhenItMatches() {
    DeterministicPdfExtractor deterministic = mock(DeterministicPdfExtractor.class);
    when(deterministic.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-1",
                List.of(
                    new PdfExtractedLine(
                        "A", "apples", new BigDecimal("3"), "KG", null, null, null))));
    LlmInvoicePdfExtractor llm = mock(LlmInvoicePdfExtractor.class);

    PdfExtractedInvoice out =
        new HybridInvoicePdfExtractor(List.of(deterministic), llm).extract("text");

    assertThat(out.invoiceNumber()).isEqualTo("INV-1");
    assertThat(out.lines()).hasSize(1);
    verify(llm, org.mockito.Mockito.never()).extract(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void fallsBackToTheLlmWhenDeterministicReturnsNoLines() {
    DeterministicPdfExtractor deterministic = mock(DeterministicPdfExtractor.class);
    when(deterministic.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));
    LlmInvoicePdfExtractor llm = mock(LlmInvoicePdfExtractor.class);
    when(llm.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-1",
                List.of(
                    new PdfExtractedLine(
                        "B",
                        "beer",
                        new BigDecimal("12"),
                        "EACH",
                        null,
                        null,
                        new BigDecimal("1.50")))));

    PdfExtractedInvoice out =
        new HybridInvoicePdfExtractor(List.of(deterministic), llm).extract("text");

    assertThat(out.lines()).hasSize(1);
    assertThat(out.lines().get(0).stockCode()).isEqualTo("B");
    assertThat(out.lines().get(0).wetAmount()).isEqualByComparingTo(new BigDecimal("1.50"));
  }

  @Test
  void triesDeterministicTemplatesInOrder() {
    DeterministicPdfExtractor first = mock(DeterministicPdfExtractor.class);
    DeterministicPdfExtractor second = mock(DeterministicPdfExtractor.class);
    when(first.extract("text")).thenReturn(new PdfExtractedInvoice(null, List.of()));
    when(second.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-2",
                List.of(new PdfExtractedLine("C", "cheese", null, "KG", null, null, null))));
    LlmInvoicePdfExtractor llm = mock(LlmInvoicePdfExtractor.class);

    PdfExtractedInvoice out =
        new HybridInvoicePdfExtractor(List.of(first, second), llm).extract("text");

    assertThat(out.invoiceNumber()).isEqualTo("INV-2");
    assertThat(out.lines()).hasSize(1);
    verify(llm, org.mockito.Mockito.never()).extract(org.mockito.ArgumentMatchers.any());
  }
}
