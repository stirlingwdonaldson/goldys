package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class OpenAiClusterNamerTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ParameterNamesModule());

  private OpenAiClusterNamer namer(ChatModel model) {
    return new OpenAiClusterNamer(providerReturning(model), MAPPER);
  }

  @Test
  void parsesModelJsonIntoSuggestions() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            chatResponse(
                "[{\"name\":\"Beer\",\"group\":\"Drinks\",\"hint\":\"merges clusters 0 and 2\"},"
                    + "{\"name\":\"Red Wine\",\"group\":\"Wine\",\"hint\":null}]"));

    List<SuggestedCategory> out = namer(model).suggest(List.of(summary(0), summary(1)));

    assertThat(out)
        .containsExactly(
            new SuggestedCategory("Beer", "Drinks", "merges clusters 0 and 2"),
            new SuggestedCategory("Red Wine", "Wine", null));
  }

  @Test
  void returnsEmptyWhenNoModelConfigured() {
    ChatModel model = mock(ChatModel.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    List<SuggestedCategory> out =
        new OpenAiClusterNamer(provider, MAPPER).suggest(List.of(summary(0)));

    assertThat(out).isEmpty();
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  void returnsEmptyOnUnparseableOutput() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(chatResponse("not json"));

    assertThat(namer(model).suggest(List.of(summary(0)))).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheModelThrows() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

    assertThat(namer(model).suggest(List.of(summary(0)))).isEmpty();
  }

  private static ChatResponse chatResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static ClusterSummary summary(int index) {
    return new ClusterSummary(index, List.of("product-" + index), 1);
  }

  private static ObjectProvider<ChatModel> providerReturning(ChatModel model) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(model);
    return provider;
  }
}
