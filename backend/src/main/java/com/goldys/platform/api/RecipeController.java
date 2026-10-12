package com.goldys.platform.api;

import com.goldys.platform.application.RecipeReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.RecipeCosting;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resolved recipe costing for the Recipes screen. Delivery-only. */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
  private final RecipeReportingService reporting;
  private final CurrentUserService currentUser;

  public RecipeController(RecipeReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<RecipeCosting> list(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.costing(currentUser.roleOf(user));
  }
}
