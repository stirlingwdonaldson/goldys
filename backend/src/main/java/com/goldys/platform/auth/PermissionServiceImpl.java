package com.goldys.platform.auth;

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
    List<Permission> exact =
        permissionRepository.findByDepartmentAndSeniorityAndResource(
            role.department(), role.seniority(), resource);
    List<Permission> allDept =
        permissionRepository.findByDepartmentAndSeniorityAndResource(
            Department.ALL, role.seniority(), resource);
    exact.addAll(allDept);
    return exact;
  }
}
