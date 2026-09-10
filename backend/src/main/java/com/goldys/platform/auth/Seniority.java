package com.goldys.platform.auth;

/**
 * Other axis of the role model (spec Requirement 3). Ordered loosely by access breadth, but do NOT
 * encode "Owner > Manager > Staff implies access" as numeric comparison in permission checks -
 * Owner's broader access is expressed as explicit Permission rows (see PermissionService), not as
 * an ordinal shortcut, so the model stays table-driven and auditable.
 */
public enum Seniority {
  STAFF,
  MANAGER,
  OWNER
}
