package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Proves the Conversational BI beans activate once a chat model + key are configured. */
@SpringBootTest(properties = {"spring.ai.model.chat=openai", "spring.ai.openai.api-key=test-key"})
@Import(PostgresContainerConfiguration.class)
class ConversationalAiConfigWithKeyTest {

  @Autowired ObjectProvider<ChatModel> chatModel;
  @Autowired ObjectProvider<AssistantService> assistant;

  @Test
  void assistantServiceIsPresentWhenAKeyIsConfigured() {
    // Bean construction makes no network call; only object creation is exercised.
    assertThat(chatModel.getIfAvailable()).isNotNull();
    assertThat(assistant.getIfAvailable()).isNotNull();
  }
}
