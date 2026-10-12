package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One recipe's resolved cost + Lightspeed price/cost enrichment. Disposable projection; rebuilt by
 * {@link RecipeProjector} from canonical recipe cost, sale-recipe links, and sale items.
 */
@Entity
@Table(name = "resolved_recipe")
class ResolvedRecipe {
  @Id
  @Column(name = "recipe_id", nullable = false)
  private int recipeId;

  @Column(name = "recipe_name")
  private String recipeName;

  @Column(name = "recipe_folder_name")
  private String recipeFolderName;

  @Column(name = "cost_per_portion", precision = 14, scale = 4)
  private BigDecimal costPerPortion;

  @Column(name = "cost_per_portion_incl_labour", precision = 14, scale = 4)
  private BigDecimal costPerPortionInclLabour;

  @Column(name = "total_cost", precision = 14, scale = 4)
  private BigDecimal totalCost;

  @Column(name = "portion_size")
  private String portionSize;

  @Column(name = "portion_size_value", precision = 14, scale = 4)
  private BigDecimal portionSizeValue;

  @Column(name = "menu_price", precision = 14, scale = 4)
  private BigDecimal menuPrice;

  @Column(name = "actual_cost", precision = 14, scale = 4)
  private BigDecimal actualCost;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source", nullable = false)
  private String authoritativeSource;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedRecipe() {}

  ResolvedRecipe(
      int recipeId,
      String recipeName,
      String recipeFolderName,
      BigDecimal costPerPortion,
      BigDecimal costPerPortionInclLabour,
      BigDecimal totalCost,
      String portionSize,
      BigDecimal portionSizeValue,
      BigDecimal menuPrice,
      BigDecimal actualCost,
      String resolutionType,
      String authoritativeSource,
      Instant resolvedAt) {
    this.recipeId = recipeId;
    this.recipeName = recipeName;
    this.recipeFolderName = recipeFolderName;
    this.costPerPortion = costPerPortion;
    this.costPerPortionInclLabour = costPerPortionInclLabour;
    this.totalCost = totalCost;
    this.portionSize = portionSize;
    this.portionSizeValue = portionSizeValue;
    this.menuPrice = menuPrice;
    this.actualCost = actualCost;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.resolvedAt = resolvedAt;
  }

  int recipeId() {
    return recipeId;
  }

  String recipeName() {
    return recipeName;
  }

  String recipeFolderName() {
    return recipeFolderName;
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

  BigDecimal menuPrice() {
    return menuPrice;
  }

  BigDecimal actualCost() {
    return actualCost;
  }

  String resolutionType() {
    return resolutionType;
  }

  String authoritativeSource() {
    return authoritativeSource;
  }

  Instant resolvedAt() {
    return resolvedAt;
  }
}
