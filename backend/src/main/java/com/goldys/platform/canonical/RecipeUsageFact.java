package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
record RecipeUsageFact(
    @Column(name = "line_id") int lineId,
    @Column(name = "recipe_id") int recipeId,
    @Column(name = "name", columnDefinition = "text") String name,
    @Column(name = "quantity", precision = 28, scale = 12) BigDecimal quantity,
    @Column(name = "unit_id") Integer unitId,
    @Column(name = "unit", columnDefinition = "text") String unit,
    @Column(name = "original_portion_size", precision = 28, scale = 12)
        BigDecimal originalPortionSize,
    @Column(name = "original_unit_id") Integer originalUnitId,
    @Column(name = "original_unit", columnDefinition = "text") String originalUnit,
    @Column(name = "converted_quantity", precision = 28, scale = 12) BigDecimal convertedQuantity,
    @Column(name = "source_cost_per_portion", precision = 28, scale = 12)
        BigDecimal sourceCostPerPortion,
    @Column(name = "cost", precision = 28, scale = 12) BigDecimal cost,
    @Column(name = "cost_including_labour", precision = 28, scale = 12)
        BigDecimal costIncludingLabour,
    @Column(name = "broken_link") Boolean brokenLink) {
  static RecipeUsageFact from(RecipeUsage value) {
    return new RecipeUsageFact(
        value.lineId(),
        value.recipeId(),
        value.name(),
        value.quantity(),
        value.unitId(),
        value.unit(),
        value.originalPortionSize(),
        value.originalUnitId(),
        value.originalUnit(),
        value.convertedQuantity(),
        value.sourceCostPerPortion(),
        value.cost(),
        value.costIncludingLabour(),
        value.brokenLink());
  }

  RecipeUsage toValue() {
    return new RecipeUsage(
        lineId,
        recipeId,
        name,
        quantity,
        unitId,
        unit,
        originalPortionSize,
        originalUnitId,
        originalUnit,
        convertedQuantity,
        sourceCostPerPortion,
        cost,
        costIncludingLabour,
        brokenLink);
  }
}
