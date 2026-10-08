package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.widget.WidgetSpec;
import java.util.List;
import java.util.Objects;

/** A tool's result: a widget spec, notices, provenance, and the metric's related metrics. */
public record ToolResult(
    WidgetSpec widget,
    List<String> notices,
    List<MetricProvenance> provenance,
    List<MetricId> relatedMetrics) {
  public ToolResult {
    Objects.requireNonNull(widget, "widget");
    notices = notices == null ? List.of() : List.copyOf(notices);
    provenance = provenance == null ? List.of() : List.copyOf(provenance);
    relatedMetrics = relatedMetrics == null ? List.of() : List.copyOf(relatedMetrics);
  }
}
