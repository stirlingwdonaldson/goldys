package com.goldys.platform.conversational;

import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.widget.WidgetSpec;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-request accumulator of what the tool callbacks produced. Populated by the callbacks as Spring
 * AI runs the tool loop, then read once the stream completes to build the terminal {@link
 * AnswerPayload}. Thread-safe because the callbacks can run on reactive scheduler threads.
 */
public class ConversationContext {
  private final List<AnswerPayload.TraceEntry> trace = new CopyOnWriteArrayList<>();
  private final List<WidgetSpec> widgets = new CopyOnWriteArrayList<>();
  private final List<String> notices = new CopyOnWriteArrayList<>();
  private final Instant asOf = Instant.now();

  public void record(ReportingTool tool, ToolResult result) {
    trace.add(new AnswerPayload.TraceEntry(tool.name(), tool.description()));
    widgets.add(result.widget());
    notices.addAll(result.notices());
  }

  public AnswerPayload toAnswerPayload() {
    return new AnswerPayload(List.copyOf(widgets), List.copyOf(trace), asOf, List.copyOf(notices));
  }
}
