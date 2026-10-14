package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class LlmInvoicePdfExtractorTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ParameterNamesModule());

  private final LlmExtractionCacheRepository cache = mock(LlmExtractionCacheRepository.class);

  @BeforeEach
  void noCacheByDefault() {
    when(cache.findById(any())).thenReturn(Optional.empty());
  }

  private LlmInvoicePdfExtractor extractor(ChatModel model) {
    return new LlmInvoicePdfExtractor(providerReturning(model), MAPPER, cache);
  }

  @Test
  void parsesSchemaJsonIntoTheContract() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "{\"invoiceNumber\":\"F58991755\",\"lines\":["
                                + "{\"stockCode\":\"BEEF025\",\"description\":\"BEEF RUMP CAP\","
                                + "\"quantity\":3.25,\"uom\":\"KG\","
                                + "\"unitQuantity\":null,\"packSize\":null,\"wetAmount\":null}"
                                + "]}")))));

    PdfExtractedInvoice out = extractor(model).extract("Tax Invoice\nBEEF RUMP CAP ...");

    assertThat(out.invoiceNumber()).isEqualTo("F58991755");
    assertThat(out.lines()).hasSize(1);
    PdfExtractedLine line = out.lines().get(0);
    assertThat(line.stockCode()).isEqualTo("BEEF025");
    assertThat(line.description()).isEqualTo("BEEF RUMP CAP");
    assertThat(line.quantity()).isEqualByComparingTo(new BigDecimal("3.25"));
    assertThat(line.uom()).isEqualTo("KG");
  }

  @Test
  void parsesTaxTreatmentFields() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "{\"invoiceNumber\":\"INV-9\",\"lines\":["
                                + "{\"stockCode\":\"BEEF025\",\"description\":\"BEEF RUMP CAP\","
                                + "\"quantity\":3.25,\"uom\":\"KG\","
                                + "\"amountExTax\":102.38,\"amountIncTax\":112.62,"
                                + "\"gstAmount\":10.24,\"taxRate\":10,\"incGst\":true}"
                                + "]}")))));

    PdfExtractedInvoice out = extractor(model).extract("Tax Invoice\nBEEF RUMP CAP ...");

    PdfExtractedLine line = out.lines().get(0);
    assertThat(line.amountExTax()).isEqualByComparingTo("102.38");
    assertThat(line.amountIncTax()).isEqualByComparingTo("112.62");
    assertThat(line.gstAmount()).isEqualByComparingTo("10.24");
    assertThat(line.taxRate()).isEqualByComparingTo("10");
    assertThat(line.incGst()).isTrue();
  }

  @Test
  void stripsMarkdownFencesAroundTheJson() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "```json\n"
                                + "{\"invoiceNumber\":\"INV-1\",\"lines\":["
                                + "{\"stockCode\":\"A\",\"description\":\"x\",\"quantity\":1,"
                                + "\"uom\":\"EACH\"}]}\n"
                                + "```")))));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.lines()).hasSize(1);
    assertThat(out.invoiceNumber()).isEqualTo("INV-1");
  }

  @Test
  void returnsEmptyWhenNoModelIsAvailable() {
    PdfExtractedInvoice out = extractor(null).extract("text");

    assertThat(out.lines()).isEmpty();
    // No model is not a failure: environments without an AI key must not fill the flag list.
    assertThat(out.extractionError()).isNull();
  }

  @Test
  void returnsEmptyWhenModelOutputIsNotJson() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("I can't do that.")))));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.lines()).isEmpty();
    assertThat(out.extractionError()).isNotNull();
  }

  @Test
  void returnsEmptyWhenSchemaShapedJsonOmitsLines() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"invoiceNumber\":\"INV-9\"}")))));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.invoiceNumber()).isEqualTo("INV-9");
    assertThat(out.lines()).isEmpty();
    // Parseable JSON that omits lines is "no lines", not an LLM failure.
    assertThat(out.extractionError()).isNull();
  }

  @Test
  void returnsEmptyWhenLinesIsExplicitlyNull() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage("{\"invoiceNumber\":null,\"lines\":null}")))));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.lines()).isEmpty();
    assertThat(out.extractionError()).isNull();
  }

  @Test
  void returnsEmptyWithErrorWhenTheModelCallFails() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.lines()).isEmpty();
    assertThat(out.extractionError()).contains("boom");
  }

  @Test
  void returnsCachedExtractionWithoutCallingTheModel() {
    ChatModel model = mock(ChatModel.class);
    LlmExtractionCache cached =
        LlmExtractionCache.create(
            "abc",
            "{\"invoiceNumber\":\"INV-1\",\"lines\":["
                + "{\"stockCode\":\"A\",\"description\":\"x\",\"quantity\":1,\"uom\":\"EACH\"}]}",
            Instant.now());
    when(cache.findById(any())).thenReturn(Optional.of(cached));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.invoiceNumber()).isEqualTo("INV-1");
    assertThat(out.lines()).hasSize(1);
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  void storesASuccessfulExtraction() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "{\"invoiceNumber\":\"INV-2\",\"lines\":["
                                + "{\"stockCode\":\"B\",\"description\":\"y\",\"quantity\":2,"
                                + "\"uom\":\"CTN\"}]}")))));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.lines()).hasSize(1);
    verify(cache).save(any(LlmExtractionCache.class));
  }

  @Test
  void reExtractsWhenCachedJsonIsUnreadable() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            "{\"invoiceNumber\":\"INV-3\",\"lines\":["
                                + "{\"stockCode\":\"C\",\"description\":\"z\",\"quantity\":3,"
                                + "\"uom\":\"EACH\"}]}")))));
    when(cache.findById(any()))
        .thenReturn(Optional.of(LlmExtractionCache.create("abc", "not json", Instant.now())));

    PdfExtractedInvoice out = extractor(model).extract("text");

    assertThat(out.invoiceNumber()).isEqualTo("INV-3");
    verify(model).call(any(Prompt.class));
  }

  private static ObjectProvider<ChatModel> providerReturning(ChatModel model) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(model);
    return provider;
  }
}
