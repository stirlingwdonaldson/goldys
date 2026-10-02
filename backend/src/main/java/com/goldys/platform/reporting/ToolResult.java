package com.goldys.platform.reporting;

import java.util.List;
import java.util.Objects;

/** A tool's result: a widget spec plus any notices the caller must surface. */
public record ToolResult(WidgetSpec widget, List<String> notices) {
  public ToolResult {
    Objects.requireNonNull(widget, "widget");
    notices = notices == null ? List.of() : List.copyOf(notices);
  }
}
