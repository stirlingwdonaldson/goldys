package com.goldys.platform.tools;

import com.goldys.platform.auth.UserRole;

import java.util.Map;

/**
 * Phase 2 stub (spec Requirement 9). NOT used by anything in Phase 1 - exists
 * now only so the shared scaffolding (system-context.md Build Order
 * Prerequisite #1) has a fixed shape before connector/reconciliation work
 * proceeds in parallel.
 *
 * The architectural invariant this interface exists to enforce: the
 * Conversational BI / Smart Exporter assistant NEVER gets a generic
 * "run this query" tool. Every tool is purpose-built (e.g.
 * get_sales_by_period, get_labor_cost_variance) with an enum-validated
 * parameter surface - see paramSchema(). Do not add a tool whose parameters
 * accept an arbitrary field name or free-text filter expression; that is
 * freeform query generation with extra steps; see system-context.md's
 * "Restricted, tool-mediated AI access" invariant and PRD Non-Goals.
 */
public interface AiTool {

    String name();

    /** Enum-validated parameter names/types this tool accepts - no open-ended params. */
    Map<String, Class<?>> paramSchema();

    /** Tool execution MUST go through PermissionService (spec Requirement 9) - see ToolRegistry. */
    Object execute(UserRole callerRole, Map<String, Object> params);
}
