package com.goldys.platform.auth;

/**
 * The lookup the sole permission service delegates every decision to.
 *
 * <p>Package-private on purpose: only the database-backed adapter in this package implements it.
 * {@link PermissionService} is the public boundary; callers never see how a decision is sourced.
 */
@FunctionalInterface
interface PermissionLookup {
  boolean isAllowed(UserRole role, ResourceKey resource, PermissionAction action);
}
