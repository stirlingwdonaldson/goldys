package com.goldys.platform.tools;

import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.UserRole;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Phase 2 stub. Registers the fixed AiTool set and enforces the role model (spec Requirement 9)
 * before dispatching to a tool - resource string convention: "tool.<tool-name>" (see
 * auth.Permission's resource javadoc). No tools are registered yet in the scaffold.
 */
@Component
public class ToolRegistry {

  private final Map<String, AiTool> tools = new HashMap<>();
  private final PermissionService permissionService;

  public ToolRegistry(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  public void register(AiTool tool) {
    tools.put(tool.name(), tool);
  }

  public Object invoke(UserRole callerRole, String toolName, Map<String, Object> params) {
    AiTool tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Unknown tool: " + toolName);
    }
    permissionService.requireRead(callerRole, "tool." + toolName);
    return tool.execute(callerRole, params);
  }
}
