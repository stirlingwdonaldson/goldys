package com.goldys.platform.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
  List<Permission> findByDepartmentAndSeniorityAndResource(
      Department department, Seniority seniority, String resource);

  List<Permission> findByResource(String resource);
}
