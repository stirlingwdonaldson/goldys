package com.goldys.platform.auth;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves an OIDC identity to the active staff profile it is assigned to.
 *
 * <p>The JPA entity stays package-private; callers outside {@code auth} see only the immutable
 * summary record, so the profile entity never becomes a cross-module contract.
 */
@Service
public class StaffProfileService {
  private final StaffProfileRepository profiles;

  public StaffProfileService(StaffProfileRepository profiles) {
    this.profiles = profiles;
  }

  /**
   * Returns the active profile for the given issuer and subject, or empty when the identity is
   * unknown or the profile is inactive.
   */
  public Optional<StaffProfileSummary> findActive(String oidcIssuer, String oidcSubject) {
    return profiles
        .findByOidcIssuerAndOidcSubjectAndActiveTrue(oidcIssuer, oidcSubject)
        .map(
            profile ->
                new StaffProfileSummary(
                    profile.displayName(), profile.department(), profile.seniority()));
  }
}
