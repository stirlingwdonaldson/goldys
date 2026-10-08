package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The {@link AssistantService} backed by a Spring AI {@link ChatClient} + tool callbacks. */
public class ChatClientAssistantService implements AssistantService {

  private final ChatClient chatClient;
  private final List<ReportingTool> tools;
  private final ToolDispatcher dispatcher;
  private final ReportingToolCallbacks callbacks;
  private final ObjectMapper mapper;

  public ChatClientAssistantService(
      ChatClient chatClient,
      List<ReportingTool> tools,
      ToolDispatcher dispatcher,
      ReportingToolCallbacks callbacks,
      ObjectMapper mapper) {
    this.chatClient = chatClient;
    this.tools = tools;
    this.dispatcher = dispatcher;
    this.callbacks = callbacks;
    this.mapper = mapper;
  }

  @Override
  public Flux<ConversationEvent> stream(List<Message> context, UserRole role) {
    ConversationContext accumulator = new ConversationContext();
    List<ToolCallback> toolCallbacks =
        callbacks.forTools(tools, role, accumulator, dispatcher, mapper);

    ChatClient.ChatClientRequestSpec request =
        chatClient
            .prompt()
            .messages(context)
            .toolCallbacks(toolCallbacks.toArray(ToolCallback[]::new));

    // Drive the tool loop through a bounded advisor rather than relying on the model's
    // internal tool execution, so the per-turn round cap is applied and testable with a
    // plain ChatModel. When there are no tools there is nothing to bound.
    if (!toolCallbacks.isEmpty()) {
      request = request.advisors(boundedToolCallAdvisor());
    }

    return request.stream()
        .content()
        .filter(Objects::nonNull)
        .<ConversationEvent>map(delta -> new ConversationEvent.TextDelta(delta))
        // Deferred so the payload is built after the tool callbacks have populated the context.
        .concatWith(
            Mono.fromSupplier(() -> new ConversationEvent.Answer(accumulator.toAnswerPayload())))
        .onErrorResume(
            e ->
                Mono.just(
                    new ConversationEvent.Error("Something went wrong generating the answer.")));
  }

  private static ToolCallAdvisor boundedToolCallAdvisor() {
    return ToolCallAdvisor.builder()
        .toolCallingManager(new BoundedToolCallingManager(ToolCallingManager.builder().build()))
        .build();
  }
}
