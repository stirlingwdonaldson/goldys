package com.goldys.platform.reconciliation;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RecipeCompositionCalculatorTest {
  final Instant now = Instant.parse("2026-10-23T01:00:00Z");

  RecipeComposition calculate(RecipeDefinition root, Map<Integer, RecipeDefinition> graph) {
    return new RecipeCompositionCalculator(graph, now, Duration.ofHours(36), new BigDecimal("0.01"))
        .calculate(root, null);
  }

  @Test
  void negativeStockAdjustmentRemainsNegative() {
    var recipe = definition("third_651");
    assertThat(calculate(recipe, Map.of(651, recipe)).calculatedTotal())
        .isEqualByComparingTo("-0.94");
  }

  @Test
  void nestedRecipesAndSignedSubRecipeUsageAreScaledWithoutFlattening() {
    // Synthetic graph mutations of real observations exercise cycles and scaling; these are
    // explicit test scenarios, not claimed source discovery.
    var child = definition("second_451"); // 6 litre batch, actual captured stock costs.
    var parent = definition("second_461");
    var usage = parent.usages().stream().filter(u -> u.recipeId() == 451).findFirst().orElseThrow();
    var onlyUsage =
        new RecipeDefinition(parent.header(), List.of(), List.of(usage), parent.observedAt());
    assertThat(calculate(onlyUsage, Map.of(461, onlyUsage, 451, child)).calculatedTotal())
        .isEqualByComparingTo("0.0845");
    var negative =
        new RecipeUsage(
            usage.lineId(),
            451,
            usage.name(),
            new BigDecimal("-15"),
            usage.unitId(),
            usage.unit(),
            usage.originalPortionSize(),
            usage.originalUnitId(),
            usage.originalUnit(),
            new BigDecimal("-0.015"),
            usage.sourceCostPerPortion(),
            new BigDecimal("-0.0845"),
            null,
            false);
    var adjustment =
        new RecipeDefinition(parent.header(), List.of(), List.of(negative), parent.observedAt());
    assertThat(calculate(adjustment, Map.of(461, adjustment, 451, child)).calculatedTotal())
        .isEqualByComparingTo("-0.0845");
  }

  @Test
  void missingChildrenCyclesAndWrongYieldProduceNoCompleteEstimate() {
    var recipe = definition("second_463");
    assertThat(calculate(recipe, Map.of(463, recipe)).issues()).contains("MISSING_SUB_RECIPE:462");
    var u = recipe.usages().getFirst();
    var self =
        new RecipeUsage(
            u.lineId(),
            463,
            u.name(),
            u.quantity(),
            u.unitId(),
            u.unit(),
            recipe.header().portionSize(),
            recipe.header().unitId(),
            recipe.header().unit(),
            u.convertedQuantity(),
            u.sourceCostPerPortion(),
            u.cost(),
            u.costIncludingLabour(),
            false);
    var cyclic =
        new RecipeDefinition(recipe.header(), recipe.stocks(), List.of(self), recipe.observedAt());
    assertThat(calculate(cyclic, Map.of(463, cyclic)).issues())
        .contains("RECIPE_CYCLE_OR_DEPTH_LIMIT");
    assertThat(calculate(cyclic, Map.of(463, cyclic)).calculatedTotal()).isNull();
    var wrong = definition("second_451");
    assertThat(calculate(recipe, Map.of(463, recipe, 462, wrong)).calculatedTotal()).isNull();
  }

  @Test
  void staleObservationsAreQualifiedAndQuestionableSourceCostsArePreserved() {
    var r = definition("third_645");
    var old =
        new RecipeDefinition(r.header(), r.stocks(), r.usages(), now.minus(Duration.ofDays(3)));
    var result = calculate(old, Map.of(645, old));
    assertThat(result.stale()).isTrue();
    assertThat(result.definition().usages().get(1).quantity()).isEqualByComparingTo("10");
    assertThat(result.definition().usages().get(1).unit()).isEqualTo("kilograms");
    assertThat(result.definition().usages().get(1).cost()).isEqualByComparingTo("20.81");
  }
}
