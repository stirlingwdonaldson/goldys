package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source's recipe ingredient-line fact. Fact fields are immutable; a correction
 * closes this row and inserts a successor sharing the same logical identity.
 *
 * <p>{@code parentRecipeId} is the recipe the raw payload was fetched for; {@code lineRecipeId} is
 * the recipe the line belongs to (the parent itself for main lines, a sub-recipe for sub-recipe
 * lines). Identity is {@code (parentRecipeId, recipeDetailId, stockId)}: {@code recipeDetailId} is
 * CTB's OWNER-scoped line id, so the parent recipe must be part of the key, and {@code stockId}
 * disambiguates lines that share a detail id.
 */
@Entity
@Table(name = "canonical_recipe_ingredient")
class CanonicalRecipeIngredient extends BitemporalEntity {
  @Column(name = "parent_recipe_id", nullable = false, updatable = false)
  private int parentRecipeId;

  @Column(name = "line_recipe_id", updatable = false)
  private Integer lineRecipeId;

  @Column(name = "line_recipe_name", updatable = false)
  private String lineRecipeName;

  @Column(name = "is_sub_recipe", updatable = false)
  private Boolean isSubRecipe;

  @Column(name = "recipe_detail_id", nullable = false, updatable = false)
  private int recipeDetailId;

  @Column(name = "stock_id", updatable = false)
  private Integer stockId;

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "stock_description", updatable = false)
  private String stockDescription;

  @Column(name = "ingredient_description", updatable = false)
  private String ingredientDescription;

  @Column(name = "supplier_name", updatable = false)
  private String supplierName;

  @Column(name = "supplier_stock_code", updatable = false)
  private String supplierStockCode;

  @Column(name = "portion_weight", updatable = false, precision = 14, scale = 4)
  private BigDecimal portionWeight;

  @Column(name = "unit_of_measurement", updatable = false, length = 64)
  private String unitOfMeasurement;

  @Column(name = "stock_uom_shortname", updatable = false, length = 32)
  private String stockUomShortname;

  @Column(name = "cost_per_qty", updatable = false, precision = 14, scale = 4)
  private BigDecimal costPerQty;

  @Column(name = "cost_per_unit", updatable = false, precision = 14, scale = 4)
  private BigDecimal costPerUnit;

  @Column(name = "stock_usable", updatable = false, precision = 14, scale = 4)
  private BigDecimal stockUsable;

  @Column(name = "stock_wastage", updatable = false, precision = 14, scale = 4)
  private BigDecimal stockWastage;

  @Column(name = "is_broken_link", updatable = false)
  private Boolean isBrokenLink;

  protected CanonicalRecipeIngredient() {}

  private CanonicalRecipeIngredient(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int parentRecipeId,
      Integer lineRecipeId,
      String lineRecipeName,
      Boolean isSubRecipe,
      int recipeDetailId,
      Integer stockId,
      String stockCode,
      String stockDescription,
      String ingredientDescription,
      String supplierName,
      String supplierStockCode,
      BigDecimal portionWeight,
      String unitOfMeasurement,
      String stockUomShortname,
      BigDecimal costPerQty,
      BigDecimal costPerUnit,
      BigDecimal stockUsable,
      BigDecimal stockWastage,
      Boolean isBrokenLink) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.parentRecipeId = parentRecipeId;
    this.lineRecipeId = lineRecipeId;
    this.lineRecipeName = lineRecipeName;
    this.isSubRecipe = isSubRecipe;
    this.recipeDetailId = recipeDetailId;
    this.stockId = stockId;
    this.stockCode = stockCode;
    this.stockDescription = stockDescription;
    this.ingredientDescription = ingredientDescription;
    this.supplierName = supplierName;
    this.supplierStockCode = supplierStockCode;
    this.portionWeight = portionWeight;
    this.unitOfMeasurement = unitOfMeasurement;
    this.stockUomShortname = stockUomShortname;
    this.costPerQty = costPerQty;
    this.costPerUnit = costPerUnit;
    this.stockUsable = stockUsable;
    this.stockWastage = stockWastage;
    this.isBrokenLink = isBrokenLink;
  }

  static CanonicalRecipeIngredient create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int parentRecipeId,
      Integer lineRecipeId,
      String lineRecipeName,
      Boolean isSubRecipe,
      int recipeDetailId,
      Integer stockId,
      String stockCode,
      String stockDescription,
      String ingredientDescription,
      String supplierName,
      String supplierStockCode,
      BigDecimal portionWeight,
      String unitOfMeasurement,
      String stockUomShortname,
      BigDecimal costPerQty,
      BigDecimal costPerUnit,
      BigDecimal stockUsable,
      BigDecimal stockWastage,
      Boolean isBrokenLink) {
    return new CanonicalRecipeIngredient(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        parentRecipeId,
        lineRecipeId,
        lineRecipeName,
        isSubRecipe,
        recipeDetailId,
        stockId,
        stockCode,
        stockDescription,
        ingredientDescription,
        supplierName,
        supplierStockCode,
        portionWeight,
        unitOfMeasurement,
        stockUomShortname,
        costPerQty,
        costPerUnit,
        stockUsable,
        stockWastage,
        isBrokenLink);
  }

  boolean sameFact(RecipeIngredientInput input) {
    return parentRecipeId == input.parentRecipeId()
        && Objects.equals(lineRecipeId, input.lineRecipeId())
        && Objects.equals(lineRecipeName, input.lineRecipeName())
        && Objects.equals(isSubRecipe, input.isSubRecipe())
        && recipeDetailId == input.recipeDetailId()
        && Objects.equals(stockId, input.stockId())
        && Objects.equals(stockCode, input.stockCode())
        && Objects.equals(stockDescription, input.stockDescription())
        && Objects.equals(ingredientDescription, input.ingredientDescription())
        && Objects.equals(supplierName, input.supplierName())
        && Objects.equals(supplierStockCode, input.supplierStockCode())
        && sameAmount(portionWeight, input.portionWeight())
        && Objects.equals(unitOfMeasurement, input.unitOfMeasurement())
        && Objects.equals(stockUomShortname, input.stockUomShortname())
        && sameAmount(costPerQty, input.costPerQty())
        && sameAmount(costPerUnit, input.costPerUnit())
        && sameAmount(stockUsable, input.stockUsable())
        && sameAmount(stockWastage, input.stockWastage())
        && Objects.equals(isBrokenLink, input.isBrokenLink());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  int parentRecipeId() {
    return parentRecipeId;
  }

  Integer lineRecipeId() {
    return lineRecipeId;
  }

  String lineRecipeName() {
    return lineRecipeName;
  }

  Boolean isSubRecipe() {
    return isSubRecipe;
  }

  int recipeDetailId() {
    return recipeDetailId;
  }

  Integer stockId() {
    return stockId;
  }

  String stockCode() {
    return stockCode;
  }

  String stockDescription() {
    return stockDescription;
  }

  String ingredientDescription() {
    return ingredientDescription;
  }

  String supplierName() {
    return supplierName;
  }

  String supplierStockCode() {
    return supplierStockCode;
  }

  BigDecimal portionWeight() {
    return portionWeight;
  }

  String unitOfMeasurement() {
    return unitOfMeasurement;
  }

  String stockUomShortname() {
    return stockUomShortname;
  }

  BigDecimal costPerQty() {
    return costPerQty;
  }

  BigDecimal costPerUnit() {
    return costPerUnit;
  }

  BigDecimal stockUsable() {
    return stockUsable;
  }

  BigDecimal stockWastage() {
    return stockWastage;
  }

  Boolean isBrokenLink() {
    return isBrokenLink;
  }
}
