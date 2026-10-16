package com.goldys.platform.auth;

import org.springframework.stereotype.Service;

/**
 * The one and only permission enforcement point.
 *
 * <p>Every protected read and override action must route through {@link #require}. There is no
 * owner bypass and no ordinal seniority comparison here; both would defeat the table-driven model
 * this service exists to protect.
 */
@Service
public class PermissionService {
  private final PermissionLookup lookup;

  public PermissionService(PermissionLookup lookup) {
    this.lookup = lookup;
  }

  /**
   * Authorizes a request, throwing {@link AccessDeniedException} when the role is not explicitly
   * granted the action on the resource.
   */
  public void require(UserRole role, ResourceKey resource, PermissionAction action) {
    if (!lookup.isAllowed(role, resource, action)) {
      throw AccessDeniedException.forResource(resource.value());
    }
  }

  /** True when the role is granted the action on the resource (a query, not an enforcement). */
  public boolean isAllowed(UserRole role, ResourceKey resource, PermissionAction action) {
    return lookup.isAllowed(role, resource, action);
  }
}
