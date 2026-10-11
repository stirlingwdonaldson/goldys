package com.goldys.platform.api;

import com.goldys.platform.application.PlatformMapService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.NetworkGraphDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The platform dependency map for the Data health screen. Delivery-only. */
@RestController
public class PlatformMapController {
  private final PlatformMapService platformMap;
  private final CurrentUserService currentUser;

  public PlatformMapController(PlatformMapService platformMap, CurrentUserService currentUser) {
    this.platformMap = platformMap;
    this.currentUser = currentUser;
  }

  @GetMapping("/api/platform-map")
  NetworkGraphDto map(@AuthenticationPrincipal AccountUserDetails user) {
    return platformMap.map(currentUser.roleOf(user), user.email());
  }
}
