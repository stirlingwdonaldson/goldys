package com.goldys.platform.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements PermissionService {

  private final PermissionRepository permissionRepository;

  public PermissionServiceImpl(PermissionRepository permissionRepository) {
    this.permissionRepository = permissionRepository;
  }

  @Override
  public boolean canRead(UserRole role, String resource) {
    return matchingPermissions(role, resource).stream().anyMatch(Permission::isCanRead);
  }

  @Override
  public boolean canWrite(UserRole role, String resource) {
    return matchingPermissions(role, resource).stream().anyMatch(Permission::isCanWrite);
  }

  @Override
  public void requireRead(UserRole role, String resource) {
    if (!canRead(role, resource)) {
      throw new AccessDeniedException(role, resource, "read");
    }
  }

  @Override
  public void requireWrite(UserRole role, String resource) {
    if (!canWrite(role, resource)) {
      throw new AccessDeniedException(role, resource, "write");
    }
  }

  private List<Permission> matchingPermissions(UserRole role, String resource) {
    // department() is the role's own department; Department.ALL rows also apply to any role
    // (e.g. an Owner-only resource might be granted to (ALL, OWNER) rather than
    // (BOH, OWNER) and (FOH, OWNER) separately - both patterns are valid, decide per resource
    // when populating the field-to-role mapping).
    //
    // Copy into a fresh list before merging: a query result is not ours to mutate, and repository
    // returns are not guaranteed mutable (an unmodifiable wrapper or a projection would make the
    // in-place merge throw, turning "denied" into a 500).
    List<Permission> permissions =
        new ArrayList<>(
            permissionRepository.findByDepartmentAndSeniorityAndResource(
                role.department(), role.seniority(), resource));
    permissions.addAll(
        permissionRepository.findByDepartmentAndSeniorityAndResource(
            Department.ALL, role.seniority(), resource));
    return permissions;
  }
}
