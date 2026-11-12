package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.*;
import com.goldys.platform.reporting.*;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class AssistantCompletionRecoveryTest {
  private static final String PLAN = "I need to query the gross sales metric. Let me use the tool.";
  private final ChatModel model = mock(ChatModel.class);
  private final ToolDispatcher dispatcher = mock(ToolDispatcher.class);

  @Test
  void toolLimitGetsOneFinalResponseWithNoToolsAndNoAdditionalQueries() {
    var turns = new java.util.concurrent.atomic.AtomicInteger();
    when(model.stream(any(Prompt.class)))
        .thenAnswer(
            invocation -> {
              if (turns.incrementAndGet() <= BoundedToolCallingManager.MAX_TOOL_CALLS + 1)
                return call();
              Prompt prompt = invocation.getArgument(0);
              if (prompt.getOptions()
                  instanceof org.springframework.ai.model.tool.ToolCallingChatOptions options) {
                assertThat(options.getToolCallbacks()).isNullOrEmpty();
                assertThat(options.getToolNames()).isNullOrEmpty();
              }
              assertThat(prompt.getInstructions())
                  .anySatisfy(
                      message -> assertThat(message.getText()).contains("Retrieved evidence:"));
              return text("Completed from the retrieved evidence.");
            });
    when(dispatcher.dispatch(any(), any(), any()))
        .thenReturn(
            new ToolResult(
                new TimeSeriesWidgetSpec("id", "Sales", null, List.of(), "currency", null),
                List.of(),
                List.of(),
                List.of()));
    var events = run();
    assertThat(events)
        .contains(new ConversationEvent.TextDelta("Completed from the retrieved evidence."));
    verify(dispatcher, times(BoundedToolCallingManager.MAX_TOOL_CALLS))
        .dispatch(any(), any(), any());
    verify(model, times(BoundedToolCallingManager.MAX_TOOL_CALLS + 2)).stream(any(Prompt.class));
  }

  @Test
  void missingEvidenceOverridesAModelClaimOfKnownZeroButObservedZeroDoesNot() {
    var day = java.time.LocalDate.of(2026, 11, 10);
    var range =
        new com.goldys.platform.semantic.catalog.TimeRange(
            day, day, com.goldys.platform.semantic.catalog.Calendar.CALENDAR);
    var provenance =
        new com.goldys.platform.semantic.catalog.MetricProvenance(
                com.goldys.platform.semantic.catalog.MetricId.SALES_GROSS,
                "1",
                range,
                com.goldys.platform.semantic.catalog.TimeGrain.TOTAL,
                "resolved_daily_sales",
                null,
                List.of(day),
                "1")
            .withCoverage(
                new com.goldys.platform.semantic.catalog.MetricCoverage(
                    day, day, List.of(), List.of(), List.of(day)));
    when(model.stream(any(Prompt.class)))
        .thenReturn(call(), text("Sales were AUD 0, a known zero."));
    var result =
        new ToolResult(
            new TimeSeriesWidgetSpec("id", "Sales", null, List.of(), "currency", null),
            List.of("No record for " + day),
            List.of(provenance),
            List.of());
    when(dispatcher.dispatch(any(), any(), any())).thenReturn(result);
    var events = run();
    assertThat(events)
        .filteredOn(ConversationEvent.TextDelta.class::isInstance)
        .allSatisfy(
            event ->
                assertThat(((ConversationEvent.TextDelta) event).delta())
                    .contains("missing or unresolved, not a recorded zero")
                    .doesNotContain("AUD 0"));
    assertThat(((ConversationEvent.Answer) events.getLast()).payload().notices())
        .contains("No record for " + day);
    var context = new ConversationContext();
    context.record(
        mock(ReportingTool.class),
        new ToolResult(
            result.widget(),
            List.of(),
            List.of(
                provenance.withCoverage(
                    new com.goldys.platform.semantic.catalog.MetricCoverage(
                        day, day, List.of(day), List.of(), List.of()))),
            List.of()));
    assertThat(context.hasOnlyMissingMetricEvidence()).isFalse();
    var mixed = new ConversationContext();
    mixed.record(mock(ReportingTool.class), result);
    assertThat(mixed.hasOnlyMissingMetricEvidence()).isTrue();
    var category = mock(ReportingTool.class);
    when(category.id()).thenReturn(ToolId.GET_SALES_BY_CATEGORY);
    mixed.record(
        category,
        new ToolResult(
            new com.goldys.platform.widget.TableWidgetSpec(
                "category",
                "Observed category totals",
                null,
                List.of(new com.goldys.platform.widget.Column("amount", "Amount", "currency")),
                List.of(
                    java.util.Map.<String, Object>of("amount", new java.math.BigDecimal("2002"))),
                null),
            List.of(),
            List.of(),
            List.of()));
    assertThat(mixed.hasOnlyMissingMetricEvidence()).isFalse();
    var grouped = new ConversationContext();
    grouped.record(
        mock(ReportingTool.class),
        new ToolResult(
            new com.goldys.platform.widget.TableWidgetSpec(
                "grouped",
                "Partial product results",
                null,
                List.of(),
                List.of(java.util.Map.<String, Object>of("value", new java.math.BigDecimal("12"))),
                null),
            List.of("Other product observations are missing"),
            List.of(provenance),
            List.of()));
    assertThat(grouped.hasOnlyMissingMetricEvidence()).isFalse();
  }

  @Test
  void retriesAnUnexecutedPromiseAndPublishesOnlyTheCompletedAnswer() {
    when(model.stream(any(Prompt.class)))
        .thenReturn(text(PLAN), call(), text("Results retrieved."));
    when(dispatcher.dispatch(any(), any(), any()))
        .thenReturn(
            new ToolResult(
                new TimeSeriesWidgetSpec("id", "Sales", null, List.of(), "currency", null),
                List.of(),
                List.of(),
                List.of()));
    var events = run();
    assertThat(events)
        .filteredOn(ConversationEvent.TextDelta.class::isInstance)
        .containsExactly(new ConversationEvent.TextDelta("Results retrieved."));
    assertThat(((ConversationEvent.Answer) events.getLast()).payload().trace()).hasSize(1);
    verify(model, times(3)).stream(any(Prompt.class));
    verify(dispatcher).dispatch(any(), any(), any());
  }

  @Test
  void repeatedPromisesStopAfterOneRecoveryAndNeverPretendToHaveData() {
    when(model.stream(any(Prompt.class))).thenReturn(text(PLAN));
    assertThat(run())
        .contains(
            new ConversationEvent.TextDelta(
                "I couldn't complete the requested lookup. No figures were retrieved."));
    verify(model, times(2)).stream(any(Prompt.class));
    verifyNoInteractions(dispatcher);
  }

  @Test
  void doesNotAskPermissionToPerformTheRequestedRead() {
    when(model.stream(any(Prompt.class)))
        .thenReturn(
            text("Should I query that, or do you need a different metric?"),
            text("No supported result is available."));
    assertThat(run())
        .contains(new ConversationEvent.TextDelta("No supported result is available."));
    verify(model, times(2)).stream(any(Prompt.class));
  }

  @Test
  void genuineClarificationDoesNotTriggerRecovery() {
    when(model.stream(any(Prompt.class))).thenReturn(text("Which date range should I query?"));
    assertThat(run()).contains(new ConversationEvent.TextDelta("Which date range should I query?"));
    verify(model).stream(any(Prompt.class));
  }

  @Test
  void deniedToolInvocationDoesNotTriggerRecoveryEvenIfModelPromisesAnotherLookup() {
    when(model.stream(any(Prompt.class))).thenReturn(call(), text(PLAN));
    when(dispatcher.dispatch(any(), any(), any())).thenThrow(new AccessDeniedException("denied"));
    var events = run();
    assertThat(((ConversationEvent.Answer) events.getLast()).payload().widgets()).isEmpty();
    verify(model, times(2)).stream(any(Prompt.class));
    verify(dispatcher).dispatch(any(), any(), any());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<ConversationEvent> run() {
    ReportingTool tool = mock(ReportingTool.class);
    when(tool.id()).thenReturn(ToolId.GET_METRIC);
    when(tool.name()).thenReturn("get_metric");
    when(tool.description()).thenReturn("Retrieve a metric.");
    when(tool.inputType()).thenReturn((Class) GetMetricInput.class);
    return new ChatClientAssistantService(
            ChatClient.builder(model).build(),
            List.of(tool),
            dispatcher,
            new ReportingToolCallbacks(),
            new ObjectMapper().findAndRegisterModules(),
            Clock.systemUTC())
        .stream(
                List.of(new UserMessage("Gross sales on 10 November 2026?")),
                new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER")))
            .collectList()
            .block();
  }

  private Flux<ChatResponse> text(String text) {
    return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
  }

  private Flux<ChatResponse> call() {
    var call =
        new AssistantMessage.ToolCall(
            "call-1",
            "function",
            "get_metric",
            "{\"metric\":\"SALES_GROSS\",\"startDate\":\"2026-11-10\",\"endDate\":\"2026-11-10\",\"grain\":\"TOTAL\",\"dimensions\":[]}");
    return Flux.just(
        new ChatResponse(
            List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build()))));
  }
}
