package com.goldys.platform.reporting;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.metrics.OperationalMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** The single entry point for tool calls: route, authorize, execute. */
@Component
public class ToolDispatcher {
  private final ToolRegistry registry;
  private final PermissionService permissions;
  private final OperationalMetrics metrics;

  public ToolDispatcher(
      ToolRegistry registry, PermissionService permissions, OperationalMetrics metrics) {
    this.registry = registry;
    this.permissions = permissions;
    this.metrics = metrics;
  }

  public ToolResult dispatch(ToolId id, ToolInput input, UserRole role) {
    ReportingTool tool =
        registry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    permissions.require(role, tool.resource(), PermissionAction.READ);
    Timer.Sample sample = metrics.start();
    try {
      return tool.execute(input, role);
    } catch (RuntimeException e) {
      metrics.toolFailure(id.name());
      throw e;
    } finally {
      metrics.stopTool(sample, id.name());
    }
  }
}
