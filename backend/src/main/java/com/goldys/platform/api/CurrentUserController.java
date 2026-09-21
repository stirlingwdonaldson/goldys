package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.StaffProfileSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the current staff identity to the frontend. */
@RestController
@RequestMapping("/api")
public class CurrentUserController {

  @GetMapping("/me")
  StaffProfileSummary me(@AuthenticationPrincipal AccountUserDetails user) {
    return new StaffProfileSummary(user.displayName(), user.department(), user.seniority());
  }
}
