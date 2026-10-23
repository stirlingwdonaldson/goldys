package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

  @Column(name = "menu_state", nullable = false)
  private String menuState;

  @Column(name = "price_source", nullable = false)
  private String priceSource;

  @Column(name = "last_sold_price", precision = 14, scale = 4)
  private BigDecimal lastSoldPrice;

  @Column(name = "last_sold_cost", precision = 14, scale = 4)
  private BigDecimal lastSoldCost;

  @Column(name = "last_sold_date")
  private LocalDate lastSoldDate;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source", nullable = false)
  private String authoritativeSource;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(nullable = false)
  private java.util.List<com.goldys.platform.semantic.RecipeEvidence> costEvidence =
      java.util.List.of();

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(nullable = false)
  private java.util.List<com.goldys.platform.semantic.RecipeEvidence> priceEvidence =
      java.util.List.of();

  java.util.List<com.goldys.platform.semantic.RecipeEvidence> costEvidence() {
    return costEvidence;
  }

  java.util.List<com.goldys.platform.semantic.RecipeEvidence> priceEvidence() {
    return priceEvidence;
  }

  void evidence(
      java.util.List<com.goldys.platform.semantic.RecipeEvidence> cost,
      java.util.List<com.goldys.platform.semantic.RecipeEvidence> price) {
    costEvidence = cost;
    priceEvidence = price;
  }

  ResolvedRecipe withName(String name) {
    var row =
        new ResolvedRecipe(
            recipeId,
            name,
            recipeFolderName,
            costPerPortion,
            costPerPortionInclLabour,
            totalCost,
            portionSize,
            portionSizeValue,
            menuPrice,
            actualCost,
            menuState,
            priceSource,
            lastSoldPrice,
            lastSoldCost,
            lastSoldDate,
            resolutionType,
            authoritativeSource,
            resolvedAt);
    row.evidence(costEvidence, priceEvidence);
    return row;
  }

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
      String menuState,
      String priceSource,
      BigDecimal lastSoldPrice,
      BigDecimal lastSoldCost,
      LocalDate lastSoldDate,
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
    this.menuState = menuState;
    this.priceSource = priceSource;
    this.lastSoldPrice = lastSoldPrice;
    this.lastSoldCost = lastSoldCost;
    this.lastSoldDate = lastSoldDate;
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

  String menuState() {
    return menuState;
  }

  String priceSource() {
    return priceSource;
  }

  BigDecimal lastSoldPrice() {
    return lastSoldPrice;
  }

  BigDecimal lastSoldCost() {
    return lastSoldCost;
  }

  LocalDate lastSoldDate() {
    return lastSoldDate;
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
