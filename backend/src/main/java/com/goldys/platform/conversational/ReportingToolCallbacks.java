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
    ToolCallback delegate =
        FunctionToolCallback.builder(
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
    return validatedArguments(delegate, context);
  }

  private static ToolCallback validatedArguments(
      ToolCallback delegate, ConversationContext context) {
    return new ToolCallback() {
      @Override
      public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
      }

      @Override
      public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
      }

      @Override
      public String call(String input) {
        return invoke(() -> delegate.call(input));
      }

      @Override
      public String call(String input, org.springframework.ai.chat.model.ToolContext toolContext) {
        return invoke(() -> delegate.call(input, toolContext));
      }

      private String invoke(java.util.function.Supplier<String> call) {
        context.markToolInvoked();
        try {
          return call.get();
        } catch (RuntimeException error) {
          // Record construction/schema errors happen before the typed function is invoked.
          for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
              return "{\"ok\":false,\"error\":\"Invalid tool arguments. Check all required fields, enum values and ISO dates in this tool's schema, then correct the call.\"}";
            }
          }
          throw error;
        }
      }
    };
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
