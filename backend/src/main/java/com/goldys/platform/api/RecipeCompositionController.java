package com.goldys.platform.api;

import com.goldys.platform.application.RecipeCompositionReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.RecipeCatalog;
import com.goldys.platform.semantic.RecipeComposition;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes")
public class RecipeCompositionController {
  private final RecipeCompositionReportingService reporting;
  private final CurrentUserService users;

  public RecipeCompositionController(
      RecipeCompositionReportingService reporting, CurrentUserService users) {
    this.reporting = reporting;
    this.users = users;
  }

  @GetMapping("/{recipeId}/composition")
  RecipeComposition composition(
      @PathVariable int recipeId, @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.composition(recipeId, users.roleOf(user));
  }

  @GetMapping("/folders")
  RecipeCatalog folders(@AuthenticationPrincipal AccountUserDetails user) {
    return reporting.catalog(users.roleOf(user));
  }
}
