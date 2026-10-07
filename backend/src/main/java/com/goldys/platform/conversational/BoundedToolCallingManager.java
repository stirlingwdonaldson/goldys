package com.goldys.platform.conversational;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A {@link ToolCallingManager} decorator that hard-caps the number of tool-call rounds per turn.
 * Each {@link #executeToolCalls} invocation is one round — one model response that requested tools.
 * Once the cap is exceeded the manager stops delegating and returns a {@code returnDirect} result
 * carrying a bounded error, which ends the tool loop and tells the model to summarize with the data
 * it already gathered instead of looping unbounded.
 *
 * <p>Instances are created per-turn (fresh inside {@link ChatClientAssistantService#stream}), so
 * the round counter is naturally scoped to a single {@code stream(...)} call and is never shared
 * across turns. The cap only counts rounds; it never authorizes or executes a tool itself — that
 * still flows through the {@link com.goldys.platform.reporting.ToolDispatcher}.
 */
public class BoundedToolCallingManager implements ToolCallingManager {

  /** Default maximum tool-call rounds per turn. */
  public static final int MAX_TOOL_CALLS = 8;

  private static final String LIMIT_MESSAGE =
      "tool-call limit reached: summarize using the data already gathered";

  private final ToolCallingManager delegate;
  private final AtomicInteger rounds = new AtomicInteger();

  public BoundedToolCallingManager(ToolCallingManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
    return delegate.resolveToolDefinitions(chatOptions);
  }

  @Override
  public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
    if (rounds.incrementAndGet() > MAX_TOOL_CALLS) {
      return refusal();
    }
    return delegate.executeToolCalls(prompt, chatResponse);
  }

  private static ToolExecutionResult refusal() {
    ToolResponseMessage.ToolResponse response =
        new ToolResponseMessage.ToolResponse("bounded-rounds", "bounded-rounds", LIMIT_MESSAGE);
    ToolResponseMessage message =
        ToolResponseMessage.builder().responses(List.of(response)).build();
    return ToolExecutionResult.builder()
        .conversationHistory(List.<Message>of(message))
        .returnDirect(true)
        .build();
  }
}
