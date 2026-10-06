package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** PDFBox-backed {@link DocumentTextExtractor} for invoice PDFs. */
@Component
public class PdfBoxTextExtractor implements DocumentTextExtractor {

  @Override
  public String extractText(byte[] document, String contentType) {
    if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType.trim())) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Unsupported document type: " + contentType);
    }
    try (PDDocument pdf = Loader.loadPDF(document)) {
      return new PDFTextStripper().getText(pdf);
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Could not extract PDF text", e);
    }
  }
}
