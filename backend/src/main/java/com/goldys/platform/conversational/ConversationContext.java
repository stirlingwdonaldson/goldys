package com.goldys.platform.conversational;

import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.semantic.catalog.MetricProvenance;
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
  private final List<MetricProvenance> metricEvidence = new CopyOnWriteArrayList<>();
  private final Instant asOf = Instant.now();
  private volatile DashboardDraft draft;
  private volatile boolean toolInvoked;
  private volatile boolean hasUnqualifiedDataResult;
  private volatile boolean hasNumericValue;
  private volatile boolean toolLimitReached;

  public void markToolLimitReached() {
    toolLimitReached = true;
  }

  public boolean hasReachedToolLimit() {
    return toolLimitReached;
  }

  public void markToolInvoked() {
    toolInvoked = true;
  }

  public boolean hasToolInvocations() {
    return toolInvoked;
  }

  public void record(ReportingTool tool, ToolResult result) {
    trace.add(new AnswerPayload.TraceEntry(tool.name(), tool.description(), result.provenance()));
    widgets.add(result.widget());
    notices.addAll(result.notices());
    metricEvidence.addAll(result.provenance());
    hasNumericValue |= containsNumericValue(result.widget());
    // Legacy category/recipe results may contain real values without metric coverage metadata.
    if (result.provenance().isEmpty() && tool.id() != ToolId.DESCRIBE_METRICS) {
      hasUnqualifiedDataResult = true;
    }
  }

  /** Never let a model turn an entirely unobserved metric result into a recorded zero. */
  public boolean hasOnlyMissingMetricEvidence() {
    return !hasUnqualifiedDataResult
        && !hasNumericValue
        && !metricEvidence.isEmpty()
        && metricEvidence.stream()
            .allMatch(
                p ->
                    p.coverage() != null
                        && p.coverage().observedDates().isEmpty()
                        && !p.coverage().missingDates().isEmpty());
  }

  private static boolean containsNumericValue(WidgetSpec widget) {
    // Group coverage can be incomplete on every date while individual groups still have values.
    // Coverage alone therefore cannot justify discarding all returned figures.
    return switch (widget) {
      case com.goldys.platform.widget.TableWidgetSpec table ->
          table.rows().stream().anyMatch(row -> row.get("value") instanceof Number);
      case com.goldys.platform.widget.TimeSeriesWidgetSpec series ->
          series.series().stream().flatMap(s -> s.points().stream()).anyMatch(p -> p.y() != null);
      case com.goldys.platform.widget.StatWidgetSpec stat -> stat.value() != null;
      default -> true; // Leave unrecognized result shapes intact.
    };
  }

  /** Records a validated dashboard draft (carried on the answer, not persisted). */
  public void recordDraft(DashboardDraft draft) {
    this.draft = draft;
  }

  public DashboardDraft draft() {
    return draft;
  }

  public AnswerPayload toAnswerPayload() {
    return new AnswerPayload(
        List.copyOf(widgets), List.copyOf(trace), asOf, List.copyOf(notices), draft);
  }
}
