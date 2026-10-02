package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Wires the Conversational BI beans, gated on the OpenAI chat model being selected ({@code
 * spring.ai.model.chat=openai}, which is {@code none} by default in application.yml). Gating on the
 * property rather than {@code @ConditionalOnBean(ChatModel)} avoids the bean-ordering ambiguity of
 * that annotation outside an auto-configuration.
 */
@Configuration
public class ConversationalAiConfig {

  private static final String OPENAI_CHAT_SELECTED = "spring.ai.model.chat";

  @Bean
  @ConditionalOnProperty(name = OPENAI_CHAT_SELECTED, havingValue = "openai")
  ChatClient conversationalChatClient(
      ChatModel model,
      @Value("${app.conversational.system-prompt:prompts/ask-goldys-system.txt}")
          String promptResource)
      throws IOException {
    String systemPrompt;
    try (var in = new ClassPathResource(promptResource).getInputStream()) {
      systemPrompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    return ChatClient.builder(model).defaultSystem(systemPrompt).build();
  }

  @Bean
  @ConditionalOnProperty(name = OPENAI_CHAT_SELECTED, havingValue = "openai")
  AssistantService assistantService(
      ChatClient chatClient,
      List<ReportingTool> tools,
      ToolDispatcher dispatcher,
      ReportingToolCallbacks callbacks,
      ObjectMapper mapper) {
    return new ChatClientAssistantService(chatClient, tools, dispatcher, callbacks, mapper);
  }
}
