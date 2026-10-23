package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.RecipeCosting;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleRecipeMatcherTest {
  static RecipeCosting recipe(int id, String name) {
    return new RecipeCosting(
        id,
        name,
        null,
        "1",
        new BigDecimal("2.88"),
        null,
        null,
        null,
        null,
        null,
        "UNKNOWN",
        "NONE",
        null,
        null,
        null,
        java.time.Instant.now(),
        "CTB",
        "COMPLETE",
        false);
  }

  @Test
  void resolvesExactSpecialAndLinkedGlassWithoutConfusingRegularSteak() {
    var matcher =
        new SaleRecipeMatcher(
            List.of(
                recipe(722, "$28 Steak Frites"),
                recipe(710, "Steak Frites"),
                recipe(57, "Swiftsure gls")),
            List.of(
                new SaleRecipeMatcher.Link("$28 Steak Frites", -1, false),
                new SaleRecipeMatcher.Link("G - Swiftsure Sauvignon Blanc", 57, true)));
    assertThat(matcher.match("$28 Steak Frites").recipe().recipeId()).isEqualTo(722);
    assertThat(matcher.match("$28 Steak Frites").method()).isEqualTo("EXACT_RECIPE_NAME");
    assertThat(matcher.match("G - Swiftsure Sauvignon Blanc").recipe().recipeId()).isEqualTo(57);
    assertThat(matcher.match("Steak Frites").recipe().recipeId()).isEqualTo(710);
  }

  @Test
  void refusesAmbiguityInactiveLinksAndMissingExplicitRecipes() {
    var recipes = List.of(recipe(722, "$28 Steak Frites"), recipe(723, "$28 Steak Frites"));
    assertThat(new SaleRecipeMatcher(recipes, List.of()).match("$28 Steak Frites").status())
        .isEqualTo("AMBIGUOUS_NAME");
    assertThat(
            new SaleRecipeMatcher(
                    recipes, List.of(new SaleRecipeMatcher.Link("$28 Steak Frites", 722, false)))
                .match("$28 Steak Frites")
                .status())
        .isEqualTo("AMBIGUOUS_OR_INACTIVE_LINK");
    assertThat(
            new SaleRecipeMatcher(List.of(), List.of(new SaleRecipeMatcher.Link("wine", 57, true)))
                .match("wine")
                .status())
        .isEqualTo("RECIPE_MISSING");
    assertThat(new SaleRecipeMatcher(recipes, List.of()).match(null).status())
        .isEqualTo("UNMATCHED");
  }
}
