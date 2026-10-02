package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ToolDispatcher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ChatClientAssistantServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsTextDeltasThenATerminalAnswer() {
    ChatModel model = mock(ChatModel.class);
    when(model.stream(any(Prompt.class)))
        .thenReturn(
            Flux.just(
                new ChatResponse(
                    List.of(new Generation(new AssistantMessage("Sales were $27,650.66."))))));

    ChatClientAssistantService service =
        new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(),
            mock(ToolDispatcher.class),
            new ReportingToolCallbacks(),
            new ObjectMapper());

    List<ConversationEvent> events =
        service.stream(
                new ChatRequest(
                    List.of(new ChatRequest.ChatMessage("user", "What were sales last week?"))),
                OWNER)
            .collectList()
            .block();

    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(ConversationEvent.TextDelta.class);
    assertThat(((ConversationEvent.TextDelta) events.get(0)).delta()).contains("$27,650.66");
    assertThat(events.get(1)).isInstanceOf(ConversationEvent.Answer.class);
    ConversationEvent.Answer answer = (ConversationEvent.Answer) events.get(1);
    assertThat(answer.payload().widgets()).isEmpty();
    assertThat(answer.payload().trace()).isEmpty();
    assertThat(answer.payload().notices()).isEmpty();
  }

  @Test
  void mapsAModelErrorToAnErrorEvent() {
    ChatModel model = mock(ChatModel.class);
    when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));

    ChatClientAssistantService service =
        new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(),
            mock(ToolDispatcher.class),
            new ReportingToolCallbacks(),
            new ObjectMapper());

    List<ConversationEvent> events =
        service.stream(
                new ChatRequest(List.of(new ChatRequest.ChatMessage("user", "hello"))), OWNER)
            .collectList()
            .block();

    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(ConversationEvent.Error.class);
  }
}
