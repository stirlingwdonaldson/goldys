package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ReportingToolCallbacksTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void dispatchesThroughTheToolAndRecordsTheResult() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    ToolResult result =
        new ToolResult(
            new TimeSeriesWidgetSpec("id", "Daily sales", null, List.of(), "currency", null),
            List.of("1 date(s) have no resolved total (unresolved conflict)."));
    when(dispatcher.dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER))).thenReturn(result);

    ReportingTool tool = tool();
    ConversationContext context = new ConversationContext();
    ReportingToolCallbacks adapter = new ReportingToolCallbacks();

    ToolCallback callback =
        adapter.forTools(List.of(tool), OWNER, context, dispatcher, mapper).get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":true");
    assertThat(context.toAnswerPayload().widgets()).hasSize(1);
    assertThat(context.toAnswerPayload().trace()).hasSize(1);
    assertThat(context.toAnswerPayload().notices())
        .containsExactly("1 date(s) have no resolved total (unresolved conflict).");
  }

  @Test
  void returnsAStructuredDenialInsteadOfThrowing() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(dispatcher)
        .dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER));

    ReportingToolCallbacks adapter = new ReportingToolCallbacks();
    ToolCallback callback =
        adapter
            .forTools(List.of(tool()), OWNER, new ConversationContext(), dispatcher, mapper)
            .get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":false");
    assertThat(raw).contains("access");
  }

  @Test
  void returnsAStructuredErrorInsteadOfPropagatingAnIllegalArgument() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    doThrow(new IllegalArgumentException("endDate is before startDate"))
        .when(dispatcher)
        .dispatch(eq(ToolId.GET_SALES_BY_PERIOD), any(), eq(OWNER));

    ReportingToolCallbacks adapter = new ReportingToolCallbacks();
    ToolCallback callback =
        adapter
            .forTools(List.of(tool()), OWNER, new ConversationContext(), dispatcher, mapper)
            .get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("\"ok\":false");
    assertThat(raw).contains("endDate");
  }

  @Test
  void rejectsOutOfEnumArgumentsBeforeDispatch() {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);

    ReportingToolCallbacks adapter = new ReportingToolCallbacks();
    ToolCallback callback =
        adapter
            .forTools(List.of(tool()), OWNER, new ConversationContext(), dispatcher, mapper)
            .get(0);

    assertThatThrownBy(
            () ->
                callback.call(
                    "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"REVENUE\"}"))
        .isInstanceOf(IllegalStateException.class);
    // The enum-whitelist boundary holds: an out-of-enum metric fails deserialization and
    // never reaches the dispatcher. (Recovery back to the model happens in Spring AI's tool loop.)
    verifyNoInteractions(dispatcher);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ReportingTool tool() {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(ToolId.GET_SALES_BY_PERIOD);
    when(t.name()).thenReturn("get_sales_by_period");
    when(t.description()).thenReturn("Resolved daily sales totals for a date range.");
    when(t.inputType())
        .thenReturn((Class) com.goldys.platform.reporting.GetSalesByPeriodInput.class);
    return t;
  }
}
