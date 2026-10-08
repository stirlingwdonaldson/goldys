package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class FallbackDocumentTextExtractorTest {

  @Test
  void returnsPdfBoxTextWhenPresent() {
    PdfBoxTextExtractor pdfBox = mock(PdfBoxTextExtractor.class);
    OcrDocumentTextExtractor ocr = mock(OcrDocumentTextExtractor.class);
    when(pdfBox.extractText(new byte[] {1}, "application/pdf")).thenReturn("text layer");

    FallbackDocumentTextExtractor extractor = new FallbackDocumentTextExtractor(pdfBox, ocr);

    assertThat(extractor.extractText(new byte[] {1}, "application/pdf")).isEqualTo("text layer");
    verify(ocr, never())
        .extractText(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void fallsBackToOcrWhenPdfBoxReturnsBlank() {
    PdfBoxTextExtractor pdfBox = mock(PdfBoxTextExtractor.class);
    OcrDocumentTextExtractor ocr = mock(OcrDocumentTextExtractor.class);
    when(pdfBox.extractText(new byte[] {1}, "application/pdf")).thenReturn("   ");
    when(ocr.extractText(new byte[] {1}, "application/pdf")).thenReturn("ocred text");

    FallbackDocumentTextExtractor extractor = new FallbackDocumentTextExtractor(pdfBox, ocr);

    assertThat(extractor.extractText(new byte[] {1}, "application/pdf")).isEqualTo("ocred text");
  }
}
