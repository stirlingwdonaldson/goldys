package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.math.BigDecimal;
import java.util.List;
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
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(providerReturning(model), MAPPER);

    PdfExtractedInvoice out = extractor.extract("Tax Invoice\nBEEF RUMP CAP ...");

    assertThat(out.invoiceNumber()).isEqualTo("F58991755");
    assertThat(out.lines()).hasSize(1);
    PdfExtractedLine line = out.lines().get(0);
    assertThat(line.stockCode()).isEqualTo("BEEF025");
    assertThat(line.description()).isEqualTo("BEEF RUMP CAP");
    assertThat(line.quantity()).isEqualByComparingTo(new BigDecimal("3.25"));
    assertThat(line.uom()).isEqualTo("KG");
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
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(providerReturning(model), MAPPER);

    assertThat(extractor.extract("text").lines()).hasSize(1);
    assertThat(extractor.extract("text").invoiceNumber()).isEqualTo("INV-1");
  }

  @Test
  void returnsEmptyWhenNoModelIsAvailable() {
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(providerReturning(null), MAPPER);

    assertThat(extractor.extract("text").lines()).isEmpty();
  }

  @Test
  void returnsEmptyWhenModelOutputIsNotJson() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("I can't do that.")))));
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(providerReturning(model), MAPPER);

    assertThat(extractor.extract("text").lines()).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheModelCallFails() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    LlmInvoicePdfExtractor extractor = new LlmInvoicePdfExtractor(providerReturning(model), MAPPER);

    assertThat(extractor.extract("text").lines()).isEmpty();
  }

  private static ObjectProvider<ChatModel> providerReturning(ChatModel model) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(model);
    return provider;
  }
}
