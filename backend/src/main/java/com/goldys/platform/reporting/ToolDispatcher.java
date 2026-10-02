package com.goldys.platform.reporting;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import org.springframework.stereotype.Component;

/** The single entry point for tool calls: route, authorize, execute. */
@Component
public class ToolDispatcher {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ToolRegistry registry;
  private final PermissionService permissions;

  public ToolDispatcher(ToolRegistry registry, PermissionService permissions) {
    this.registry = registry;
    this.permissions = permissions;
  }

  public ToolResult dispatch(ToolId id, ToolInput input, UserRole role) {
    ReportingTool tool =
        registry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return tool.execute(input, role);
  }
}
