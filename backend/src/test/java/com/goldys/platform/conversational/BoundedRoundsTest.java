package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.GetSalesByPeriodInput;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class BoundedRoundsTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void capsToolCallRounds() {
    // A model that requests a tool call on every turn, indefinitely.
    ChatModel model = mock(ChatModel.class);
    AssistantMessage.ToolCall toolCall =
        new AssistantMessage.ToolCall(
            "call-1",
            "function",
            "get_sales_by_period",
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"SALES_GROSS\"}");
    AssistantMessage toolCallMessage =
        AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
    ChatResponse toolCallResponse =
        ChatResponse.builder().generations(List.of(new Generation(toolCallMessage))).build();
    when(model.stream(any(Prompt.class))).thenReturn(Flux.just(toolCallResponse));

    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    ToolResult result =
        new ToolResult(
            new TimeSeriesWidgetSpec("id", "Daily sales", null, List.of(), "currency", null),
            List.of(),
            List.of(),
            List.of());
    when(dispatcher.dispatch(any(), any(), any())).thenReturn(result);

    ReportingTool tool = tool();
    ChatClientAssistantService service =
        new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(tool),
            dispatcher,
            new ReportingToolCallbacks(),
            new ObjectMapper());

    List<ConversationEvent> events =
        service.stream(List.of(new UserMessage("What were sales?")), OWNER)
            .collectList()
            .block(Duration.ofSeconds(30));

    // The loop must terminate (no unbounded recursion) and the recorded trace must be capped.
    assertThat(events).isNotNull().isNotEmpty();
    ConversationEvent.Answer answer = (ConversationEvent.Answer) events.get(events.size() - 1);
    assertThat(answer.payload().trace()).hasSize(BoundedToolCallingManager.MAX_TOOL_CALLS);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ReportingTool tool() {
    ReportingTool tool = mock(ReportingTool.class);
    when(tool.id()).thenReturn(ToolId.GET_SALES_BY_PERIOD);
    when(tool.name()).thenReturn("get_sales_by_period");
    when(tool.description()).thenReturn("Resolved daily sales totals for a date range.");
    when(tool.inputType()).thenReturn((Class) GetSalesByPeriodInput.class);
    return tool;
  }
}
