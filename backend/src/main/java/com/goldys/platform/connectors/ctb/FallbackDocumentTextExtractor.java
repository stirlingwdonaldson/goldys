package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The production {@link DocumentTextExtractor}: extracts the PDF text layer with PDFBox, and falls
 * back to OCR (the vision model) when the PDF has no extractable text — i.e. the scanned invoices.
 * Marked {@code @Primary} so single injections of {@link DocumentTextExtractor} resolve to this
 * composite rather than the individual text/OCR extractors.
 */
@Component
@Primary
public class FallbackDocumentTextExtractor implements DocumentTextExtractor {

  private final PdfBoxTextExtractor pdfBox;
  private final OcrDocumentTextExtractor ocr;

  public FallbackDocumentTextExtractor(PdfBoxTextExtractor pdfBox, OcrDocumentTextExtractor ocr) {
    this.pdfBox = pdfBox;
    this.ocr = ocr;
  }

  @Override
  public String extractText(byte[] document, String contentType) {
    String text = pdfBox.extractText(document, contentType);
    if (text != null && !text.isBlank()) {
      return text;
    }
    return ocr.extractText(document, contentType);
  }
}
