package com.goldys.platform.reporting;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.metrics.OperationalMetrics;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** The single entry point for tool calls: route, authorize, execute. */
@Component
public class ToolDispatcher {
  private final ToolRegistry registry;
  private final PermissionService permissions;
  private final MetricCatalog catalog;
  private final OperationalMetrics metrics;

  public ToolDispatcher(
      ToolRegistry registry,
      PermissionService permissions,
      MetricCatalog catalog,
      OperationalMetrics metrics) {
    this.registry = registry;
    this.permissions = permissions;
    this.catalog = catalog;
    this.metrics = metrics;
  }

  public ToolResult dispatch(ToolId id, ToolInput input, UserRole role) {
    ReportingTool tool =
        registry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    permissions.require(role, tool.resource(), PermissionAction.READ);
    // Per-metric data gate: authorize every metric this tool will read, before execution.
    for (MetricQuery q : tool.toMetricQueries(input)) {
      String required = catalog.definition(q.metric()).requiredPermission();
      permissions.require(role, new ResourceKey(required), PermissionAction.READ);
    }
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
