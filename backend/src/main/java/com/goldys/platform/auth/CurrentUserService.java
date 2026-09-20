package com.goldys.platform.auth;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Resolves the current request's OIDC identity to the role used for permission decisions. */
@Service
public class CurrentUserService {
  private final StaffProfileService profiles;

  public CurrentUserService(StaffProfileService profiles) {
    this.profiles = profiles;
  }

  public UserRole roleOf(OidcUser user) {
    StaffProfileSummary summary =
        profiles
            .findActive(user.getIssuer().toString(), user.getSubject())
            .orElseThrow(() -> new AccessDeniedException("No active staff profile"));
    return new UserRole(
        new DepartmentCode(summary.department()), new SeniorityCode(summary.seniority()));
  }
}
