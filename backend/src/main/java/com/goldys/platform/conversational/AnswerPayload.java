package com.goldys.platform.conversational;

import com.goldys.platform.widget.WidgetSpec;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The structured artifact attached to every answer: widgets, trace, "as of", notices, draft. */
public record AnswerPayload(
    List<WidgetSpec> widgets,
    List<TraceEntry> trace,
    Instant asOf,
    List<String> notices,
    DashboardDraft draft) {

  public AnswerPayload {
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
    trace = trace == null ? List.of() : List.copyOf(trace);
    Objects.requireNonNull(asOf, "asOf");
    notices = notices == null ? List.of() : List.copyOf(notices);
    // draft is null unless a dashboard draft was recorded during the conversation.
  }

  /** One provenance entry naming a tool the answer ran. */
  public record TraceEntry(String tool, String description) {}
}
