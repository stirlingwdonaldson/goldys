package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;

/** A fixed reporting tool: an LLM-facing name/description plus an execution path. */
public interface ReportingTool {
  ToolId id();

  String name();

  String description();

  ToolResult execute(ToolInput input, UserRole role);
}
