package com.goldys.platform.reporting;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The fixed set of reporting tools, keyed by {@link ToolId}. */
@Component
public class ToolRegistry {
  private final Map<ToolId, ReportingTool> tools;

  public ToolRegistry(List<ReportingTool> tools) {
    this.tools =
        tools.stream()
            .collect(Collectors.toUnmodifiableMap(ReportingTool::id, Function.identity()));
  }

  public Optional<ReportingTool> find(ToolId id) {
    return Optional.ofNullable(tools.get(id));
  }
}
