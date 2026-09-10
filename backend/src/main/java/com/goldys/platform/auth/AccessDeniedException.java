package com.goldys.platform.auth;

/**
 * Thrown when a permission check fails. Spec Requirement 3's acceptance criteria: a denied request
 * must come back as an EXPLICIT "not permitted" - never a silently filtered or partial result.
 * Every caller of PermissionService that denies access should throw or propagate this rather than
 * quietly omitting data.
 */
public class AccessDeniedException extends RuntimeException {
  public AccessDeniedException(UserRole role, String resource, String action) {
    super("Role " + role + " is not permitted to " + action + " " + resource);
  }
}
