package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class OcrDocumentTextExtractorTest {

  @Test
  void transcribesAPdfPageThroughTheVisionModel() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(new Generation(new AssistantMessage("Potatoes 2 12.50 25.00")))));
    OcrDocumentTextExtractor extractor = new OcrDocumentTextExtractor(providerReturning(model));

    String text = extractor.extractText(pdf("anything"), "application/pdf");

    assertThat(text).contains("Potatoes");
  }

  @Test
  void returnsEmptyWhenNoModelIsAvailable() {
    OcrDocumentTextExtractor extractor = new OcrDocumentTextExtractor(providerReturning(null));

    assertThat(extractor.extractText(pdf("x"), "application/pdf")).isEmpty();
  }

  @Test
  void rejectsNonPdfContentType() {
    OcrDocumentTextExtractor extractor = new OcrDocumentTextExtractor(providerReturning(null));

    assertThatThrownBy(() -> extractor.extractText(new byte[] {1}, "text/csv"))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Unsupported document type");
  }

  private static ObjectProvider<ChatModel> providerReturning(ChatModel model) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(model);
    return provider;
  }

  private static byte[] pdf(String text) {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(100, 700);
        cs.showText(text);
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
