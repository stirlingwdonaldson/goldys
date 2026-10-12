package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source's recipe-cost fact. Fact fields are immutable; a correction (a changed
 * cost) closes this row and inserts a successor sharing the same logical identity (the recipe id).
 */
@Entity
@Table(name = "canonical_recipe_cost")
class CanonicalRecipeCost extends BitemporalEntity {
  @Column(name = "recipe_id", nullable = false, updatable = false)
  private int recipeId;

  @Column(name = "recipe_name", updatable = false)
  private String recipeName;

  @Column(name = "cost_per_portion", updatable = false, precision = 14, scale = 4)
  private BigDecimal costPerPortion;

  @Column(name = "cost_per_portion_incl_labour", updatable = false, precision = 14, scale = 4)
  private BigDecimal costPerPortionInclLabour;

  @Column(name = "total_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal totalCost;

  @Column(name = "portion_size", updatable = false)
  private String portionSize;

  @Column(name = "portion_size_value", updatable = false, precision = 14, scale = 4)
  private BigDecimal portionSizeValue;

  protected CanonicalRecipeCost() {}

  private CanonicalRecipeCost(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int recipeId,
      String recipeName,
      BigDecimal costPerPortion,
      BigDecimal costPerPortionInclLabour,
      BigDecimal totalCost,
      String portionSize,
      BigDecimal portionSizeValue) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.recipeId = recipeId;
    this.recipeName = recipeName;
    this.costPerPortion = costPerPortion;
    this.costPerPortionInclLabour = costPerPortionInclLabour;
    this.totalCost = totalCost;
    this.portionSize = portionSize;
    this.portionSizeValue = portionSizeValue;
  }

  static CanonicalRecipeCost create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int recipeId,
      String recipeName,
      BigDecimal costPerPortion,
      BigDecimal costPerPortionInclLabour,
      BigDecimal totalCost,
      String portionSize,
      BigDecimal portionSizeValue) {
    return new CanonicalRecipeCost(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        recipeId,
        recipeName,
        costPerPortion,
        costPerPortionInclLabour,
        totalCost,
        portionSize,
        portionSizeValue);
  }

  boolean sameFact(RecipeCostInput input) {
    return recipeId == input.recipeId()
        && Objects.equals(recipeName, input.recipeName())
        && Objects.equals(costPerPortion, input.costPerPortion())
        && Objects.equals(costPerPortionInclLabour, input.costPerPortionInclLabour())
        && Objects.equals(totalCost, input.totalCost())
        && Objects.equals(portionSize, input.portionSize())
        && Objects.equals(portionSizeValue, input.portionSizeValue());
  }

  int recipeId() {
    return recipeId;
  }

  String recipeName() {
    return recipeName;
  }

  BigDecimal costPerPortion() {
    return costPerPortion;
  }

  BigDecimal costPerPortionInclLabour() {
    return costPerPortionInclLabour;
  }

  BigDecimal totalCost() {
    return totalCost;
  }

  String portionSize() {
    return portionSize;
  }

  BigDecimal portionSizeValue() {
    return portionSizeValue;
  }
}
