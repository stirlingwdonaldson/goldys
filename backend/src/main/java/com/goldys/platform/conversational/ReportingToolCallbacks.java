package com.goldys.platform.conversational;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * Adapts each {@link ReportingTool} into a Spring AI {@link ToolCallback}, bound to the request's
 * {@link UserRole}, so the model can only reach the fixed tool set through the {@link
 * ToolDispatcher} (the single authorization + enum-validation choke point).
 */
@Component
public class ReportingToolCallbacks {

  public List<ToolCallback> forTools(
      List<ReportingTool> tools,
      UserRole role,
      ConversationContext context,
      ToolDispatcher dispatcher,
      ObjectMapper mapper) {
    return tools.stream().map(t -> toCallback(t, role, context, dispatcher, mapper)).toList();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ToolCallback toCallback(
      ReportingTool tool,
      UserRole role,
      ConversationContext context,
      ToolDispatcher dispatcher,
      ObjectMapper mapper) {
    return FunctionToolCallback.builder(
            tool.name(),
            (Function<ToolInput, Map<String, Object>>)
                input -> {
                  try {
                    ToolResult result = dispatcher.dispatch(tool.id(), input, role);
                    if (tool.id() == ToolId.CREATE_DASHBOARD_DRAFT) {
                      context.recordDraft(((CreateDashboardDraftTool) tool).toDraft(input));
                    } else {
                      context.record(tool, result);
                    }
                    return outcome(true, result);
                  } catch (AccessDeniedException e) {
                    return outcome(false, "You don't have access to that data.");
                  } catch (IllegalArgumentException e) {
                    return outcome(false, e.getMessage());
                  }
                })
        .description(tool.description())
        .inputType((Class) tool.inputType())
        .build();
  }

  private static Map<String, Object> outcome(boolean ok, Object resultOrMessage) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ok", ok);
    if (ok) {
      ToolResult result = (ToolResult) resultOrMessage;
      m.put("widget", result.widget());
      m.put("notices", result.notices());
      m.put("provenance", result.provenance());
      m.put("relatedMetrics", result.relatedMetrics());
    } else {
      m.put("error", resultOrMessage);
    }
    return m;
  }
}
