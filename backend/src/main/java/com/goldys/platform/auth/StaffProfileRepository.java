package com.goldys.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
  Optional<StaffProfile> findByOidcIssuerAndOidcSubjectAndActiveTrue(
      String oidcIssuer, String oidcSubject);
}
