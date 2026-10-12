package com.goldys.platform.connectors.ctb;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * OCR-backed {@link DocumentTextExtractor} for scanned (image-only) invoice PDFs: renders each page
 * with PDFBox, then sends the page image to the vision {@link ChatModel} to transcribe the text.
 * Used as the fallback when {@link PdfBoxTextExtractor} returns no text layer.
 *
 * <p>The model is injected as an {@link ObjectProvider} because it is absent when {@code
 * spring.ai.model.chat=none} (the default). With no model available the extractor returns empty
 * text, so a scanned PDF is reported as unparseable rather than silently dropped.
 */
@Component
public class OcrDocumentTextExtractor implements DocumentTextExtractor {

  private static final Logger log = LoggerFactory.getLogger(OcrDocumentTextExtractor.class);
  private static final MimeType PNG = new MimeType("image", "png");
  private static final float DPI = 150;

  /**
   * Longest edge, in pixels, of the page image sent to the vision model. The model downscales large
   * images anyway, and an oversized base64 payload trips Jackson's 20 MB string limit inside Spring
   * AI's request serialization, so large pages are capped here rather than failing OCR outright.
   */
  private static final int MAX_DIMENSION = 2000;

  private final ObjectProvider<ChatModel> chatModel;

  public OcrDocumentTextExtractor(ObjectProvider<ChatModel> chatModel) {
    this.chatModel = chatModel;
  }

  @Override
  public String extractText(byte[] document, String contentType) {
    if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType.trim())) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Unsupported document type: " + contentType);
    }
    ChatModel model = chatModel.getIfAvailable();
    if (model == null) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    try (PDDocument pdf = Loader.loadPDF(document)) {
      PDFRenderer renderer = new PDFRenderer(pdf);
      for (int page = 0; page < pdf.getNumberOfPages(); page++) {
        BufferedImage image = renderer.renderImageWithDPI(page, DPI);
        String pageText = transcribe(model, toPng(resizeToFit(image, MAX_DIMENSION)));
        if (pageText != null && !pageText.isBlank()) {
          text.append(pageText).append('\n');
        }
      }
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "Could not OCR invoice PDF", e);
    }
    return text.toString();
  }

  private String transcribe(ChatModel model, byte[] png) {
    try {
      Media image = Media.builder().mimeType(PNG).data(png).build();
      Message prompt =
          new UserMessage.Builder()
              .text(
                  "Transcribe all text from this supplier invoice page, preserving line breaks. "
                      + "Return only the transcribed text.")
              .media(List.of(image))
              .build();
      ChatResponse response = model.call(new Prompt(List.of(prompt), temperatureZero()));
      return response.getResult().getOutput().getText();
    } catch (RuntimeException e) {
      log.warn("OCR transcription failed for a page; returning empty text", e);
      return "";
    }
  }

  private static byte[] toPng(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  /** Scales {@code image} down so its longest edge is at most {@code maxDim}, preserving ratio. */
  private static BufferedImage resizeToFit(BufferedImage image, int maxDim) {
    int longest = Math.max(image.getWidth(), image.getHeight());
    if (longest <= maxDim) {
      return image;
    }
    double scale = (double) maxDim / longest;
    int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
    BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = resized.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(image, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return resized;
  }

  private static ChatOptions temperatureZero() {
    return new DefaultChatOptionsBuilder().temperature(0.0).build();
  }
}
