package com.goldys.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionRepository extends JpaRepository<Permission, UUID> {
  Optional<Permission> findByDepartmentAndSeniorityAndResource(
      String department, String seniority, String resource);
}
