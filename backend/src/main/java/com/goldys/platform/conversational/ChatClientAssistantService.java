package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
  public Flux<ConversationEvent> stream(ChatRequest request, UserRole role) {
    ConversationContext context = new ConversationContext();
    List<ToolCallback> toolCallbacks = callbacks.forTools(tools, role, context, dispatcher, mapper);
    List<Message> messages = request.messages().stream().map(this::toMessage).toList();

    return chatClient
        .prompt()
        .messages(messages)
        .toolCallbacks(toolCallbacks.toArray(ToolCallback[]::new))
        .stream()
        .content()
        .filter(Objects::nonNull)
        .<ConversationEvent>map(delta -> new ConversationEvent.TextDelta(delta))
        // Deferred so the payload is built after the tool callbacks have populated the context.
        .concatWith(
            Mono.fromSupplier(() -> new ConversationEvent.Answer(context.toAnswerPayload())))
        .onErrorResume(
            e ->
                Mono.just(
                    new ConversationEvent.Error("Something went wrong generating the answer.")));
  }

  private Message toMessage(ChatRequest.ChatMessage m) {
    return switch (m.role()) {
      case "user" -> new UserMessage(m.content());
      case "assistant" -> new AssistantMessage(m.content());
      default -> throw new IllegalArgumentException("Unknown message role: " + m.role());
    };
  }
}
