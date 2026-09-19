package com.goldys.platform.auth;

import org.springframework.stereotype.Component;

/**
 * Database-backed {@link PermissionLookup}.
 *
 * <p>An absent row is denied; a present row grants the requested action only. There is no inference
 * across seniorities and no owner branch.
 */
@Component
class JpaPermissionLookup implements PermissionLookup {
  private final PermissionRepository repository;

  JpaPermissionLookup(PermissionRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean isAllowed(UserRole role, ResourceKey resource, PermissionAction action) {
    return repository
        .findByDepartmentAndSeniorityAndResource(
            role.department().value(), role.seniority().value(), resource.value())
        .map(
            permission ->
                action == PermissionAction.READ ? permission.canRead() : permission.canWrite())
        .orElse(false);
  }
}
