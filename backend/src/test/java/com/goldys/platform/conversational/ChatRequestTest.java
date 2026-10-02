package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

  @Test
  void acceptsUserAndAssistantMessages() {
    ChatRequest request =
        new ChatRequest(
            List.of(
                new ChatRequest.ChatMessage("user", "What were sales last week?"),
                new ChatRequest.ChatMessage("assistant", "I need a date range.")));

    assertThat(request.messages()).hasSize(2);
    assertThat(request.messages().get(0).role()).isEqualTo("user");
  }

  @Test
  void rejectsAnUnknownRole() {
    assertThatThrownBy(() -> new ChatRequest.ChatMessage("system", "be helpful"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role");
  }

  @Test
  void rejectsANullContent() {
    assertThatThrownBy(() -> new ChatRequest.ChatMessage("user", null))
        .isInstanceOf(NullPointerException.class);
  }
}
