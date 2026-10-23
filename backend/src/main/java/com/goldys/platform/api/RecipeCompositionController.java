package com.goldys.platform.api;

import com.goldys.platform.application.RecipeCompositionReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.RecipeCatalog;
import com.goldys.platform.semantic.RecipeComposition;
import org.springframework.http.ResponseEntity;
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
  ResponseEntity<RecipeComposition> composition(
      @PathVariable int recipeId, @AuthenticationPrincipal AccountUserDetails user) {
    RecipeComposition value = reporting.composition(recipeId, users.roleOf(user));
    return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
  }

  @GetMapping("/folders")
  ResponseEntity<RecipeCatalog> folders(@AuthenticationPrincipal AccountUserDetails user) {
    RecipeCatalog value = reporting.catalog(users.roleOf(user));
    return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
  }
}
