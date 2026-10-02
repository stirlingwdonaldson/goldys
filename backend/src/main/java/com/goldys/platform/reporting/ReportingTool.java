package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;

/** A fixed reporting tool: an LLM-facing name/description plus an execution path. */
public interface ReportingTool {
  ToolId id();

  String name();

  String description();

  /** The concrete {@link ToolInput} type; Spring AI derives the tool's JSON schema from it. */
  Class<? extends ToolInput> inputType();

  ToolResult execute(ToolInput input, UserRole role);
}
