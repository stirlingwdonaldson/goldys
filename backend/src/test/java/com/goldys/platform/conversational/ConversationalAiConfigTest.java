package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationalAiConfigTest {

  @Autowired ObjectProvider<AssistantService> assistant;

  @Test
  void assistantServiceIsAbsentWithoutAnApiKey() {
    // The default context runs with spring.ai.model.chat=none and no key.
    assertThat(assistant.getIfAvailable()).isNull();
  }
}
