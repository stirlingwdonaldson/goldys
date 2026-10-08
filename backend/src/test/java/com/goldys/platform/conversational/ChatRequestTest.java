package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

  @Test
  void acceptsANewConversationWithoutAThreadId() {
    ChatRequest request = new ChatRequest(null, "What were sales last week?");

    assertThat(request.threadId()).isNull();
    assertThat(request.message()).isEqualTo("What were sales last week?");
  }

  @Test
  void acceptsAnExistingThreadId() {
    UUID threadId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(threadId, "And the week before?");

    assertThat(request.threadId()).isEqualTo(threadId);
    assertThat(request.message()).isEqualTo("And the week before?");
  }
}
