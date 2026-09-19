package com.goldys.platform.api;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.StaffProfileService;
import com.goldys.platform.auth.StaffProfileService.StaffProfileSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the current staff identity to the frontend.
 *
 * <p>The browser never touches OIDC tokens; it only receives the local profile the backend resolved
 * for the authenticated session.
 */
@RestController
@RequestMapping("/api")
class CurrentUserController {
  private static final String NO_ACTIVE_PROFILE =
      "This identity is not assigned an active Goldy's staff profile";

  private final StaffProfileService profiles;

  CurrentUserController(StaffProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/me")
  StaffProfileSummary me(@AuthenticationPrincipal OidcUser oidcUser) {
    if (oidcUser == null) {
      throw new AccessDeniedException(NO_ACTIVE_PROFILE);
    }
    return profiles
        .findActive(oidcUser.getIssuer().toString(), oidcUser.getSubject())
        .orElseThrow(() -> new AccessDeniedException(NO_ACTIVE_PROFILE));
  }
}
