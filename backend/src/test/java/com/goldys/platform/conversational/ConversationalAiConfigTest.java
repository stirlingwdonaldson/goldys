package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationalAiConfigTest {

  @Autowired ObjectProvider<AssistantService> assistant;

  @Test
  void assistantServiceIsAbsentWithoutAnApiKey() {
    // The default context runs with spring.ai.model.chat=none and no key.
    assertThat(assistant.getIfAvailable()).isNull();
  }

  @Test
  void systemPromptLoadsAndForbidsPresentingCorrelationAsCausation() throws IOException {
    ClassPathResource resource = new ClassPathResource("prompts/ask-goldys-system.txt");
    assertThat(resource.exists()).isTrue();

    String prompt;
    try (var in = resource.getInputStream()) {
      prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(prompt).contains("Never present correlation as causation");
  }

  @Test
  void systemPromptHedgesUntrustedToolResults() throws IOException {
    ClassPathResource resource = new ClassPathResource("prompts/ask-goldys-system.txt");
    assertThat(resource.exists()).isTrue();

    String prompt;
    try (var in = resource.getInputStream()) {
      prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(prompt).contains("surface that caveat explicitly");
    assertThat(prompt).contains("rather than presenting the number as settled");
  }
}
