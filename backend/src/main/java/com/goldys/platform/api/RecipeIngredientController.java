package com.goldys.platform.api;

import com.goldys.platform.application.RecipeIngredientReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.RecipeIngredient;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resolved recipe ingredient lines for the Recipes screen. Delivery-only. */
@RestController
@RequestMapping("/api/recipes")
public class RecipeIngredientController {
  private final RecipeIngredientReportingService reporting;
  private final CurrentUserService currentUser;

  public RecipeIngredientController(
      RecipeIngredientReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/{recipeId}/ingredients")
  List<RecipeIngredient> ingredients(
      @PathVariable int recipeId, @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.ingredients(currentUser.roleOf(user), recipeId);
  }
}
