package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfBoxTextExtractorTest {

  private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

  @Test
  void extractsTextFromAPdf() {
    byte[] pdf = pdf("Potatoes 2 12.50 25.00");

    String text = extractor.extractText(pdf, "application/pdf");

    assertThat(text).contains("Potatoes");
  }

  @Test
  void rejectsNonPdfContentType() {
    assertThatThrownBy(() -> extractor.extractText(new byte[] {1}, "text/csv"))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Unsupported document type");
  }

  private static byte[] pdf(String line) {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(100, 700);
        cs.showText(line);
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
