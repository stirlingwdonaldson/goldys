package com.goldys.platform.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  List<UserAccount> findAllByOrderByEmailAsc();

  long countByDepartmentAndSeniorityAndActiveTrue(String department, String seniority);
}
