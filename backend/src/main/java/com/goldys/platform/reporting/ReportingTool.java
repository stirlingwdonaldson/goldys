package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.util.List;

/** A fixed reporting tool: an LLM-facing name/description plus an execution path. */
public interface ReportingTool {
  ToolId id();

  String name();

  String description();

  /** The concrete {@link ToolInput} type; Spring AI derives the tool's JSON schema from it. */
  Class<? extends ToolInput> inputType();

  /** The permission resource this tool's data is gated by (enforced by the dispatcher). */
  ResourceKey resource();

  ToolResult execute(ToolInput input, UserRole role);

  /** The bounded MetricQueries this tool computes, for persisting a saved widget. */
  default List<com.goldys.platform.semantic.catalog.MetricQuery> toMetricQueries(ToolInput input) {
    throw new UnsupportedOperationException(id() + " does not support metric-query persistence");
  }
}
