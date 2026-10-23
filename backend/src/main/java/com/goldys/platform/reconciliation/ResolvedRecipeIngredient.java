package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One recipe's resolved ingredient line. Disposable projection; rebuilt by {@link
 * RecipeIngredientProjector} from canonical recipe ingredients. Row identity is a surrogate id:
 * {@code line_recipe_id} and {@code stock_id} are nullable, so no natural composite key is
 * possible.
 */
@Entity
@Table(name = "resolved_recipe_ingredient")
class ResolvedRecipeIngredient {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "parent_recipe_id", nullable = false)
  private int parentRecipeId;

  @Column(name = "line_recipe_id")
  private Integer lineRecipeId;

  @Column(name = "line_recipe_name")
  private String lineRecipeName;

  @Column(name = "is_sub_recipe")
  private Boolean isSubRecipe;

  @Column(name = "recipe_detail_id", nullable = false)
  private int recipeDetailId;

  @Column(name = "stock_id")
  private Integer stockId;

  @Column(name = "stock_code")
  private String stockCode;

  @Column(name = "stock_description")
  private String stockDescription;

  @Column(name = "portion_weight", precision = 14, scale = 4)
  private BigDecimal portionWeight;

  @Column(name = "unit_of_measurement", length = 64)
  private String unitOfMeasurement;

  @Column(name = "cost_per_qty", precision = 14, scale = 4)
  private BigDecimal costPerQty;

  @Column(name = "cost_per_unit", precision = 14, scale = 4)
  private BigDecimal costPerUnit;

  @Column(name = "supplier_name")
  private String supplierName;

  @Column(name = "stock_uom_shortname", length = 32)
  private String stockUomShortname;

  @Column(name = "stock_usable", precision = 14, scale = 4)
  private BigDecimal stockUsable;

  @Column(name = "stock_wastage", precision = 14, scale = 4)
  private BigDecimal stockWastage;

  @Column(name = "is_broken_link")
  private Boolean isBrokenLink;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedRecipeIngredient() {}

  ResolvedRecipeIngredient(
      int parentRecipeId,
      Integer lineRecipeId,
      String lineRecipeName,
      Boolean isSubRecipe,
      int recipeDetailId,
      Integer stockId,
      String stockCode,
      String stockDescription,
      BigDecimal portionWeight,
      String unitOfMeasurement,
      BigDecimal costPerQty,
      BigDecimal costPerUnit,
      String supplierName,
      Instant resolvedAt,
      String stockUomShortname,
      BigDecimal stockUsable,
      BigDecimal stockWastage,
      Boolean isBrokenLink) {
    this.id = UUID.randomUUID();
    this.parentRecipeId = parentRecipeId;
    this.lineRecipeId = lineRecipeId;
    this.lineRecipeName = lineRecipeName;
    this.isSubRecipe = isSubRecipe;
    this.recipeDetailId = recipeDetailId;
    this.stockId = stockId;
    this.stockCode = stockCode;
    this.stockDescription = stockDescription;
    this.portionWeight = portionWeight;
    this.unitOfMeasurement = unitOfMeasurement;
    this.costPerQty = costPerQty;
    this.costPerUnit = costPerUnit;
    this.supplierName = supplierName;
    this.resolvedAt = resolvedAt;
    this.stockUomShortname = stockUomShortname;
    this.stockUsable = stockUsable;
    this.stockWastage = stockWastage;
    this.isBrokenLink = isBrokenLink;
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

  BigDecimal portionWeight() {
    return portionWeight;
  }

  String unitOfMeasurement() {
    return unitOfMeasurement;
  }

  BigDecimal costPerQty() {
    return costPerQty;
  }

  BigDecimal costPerUnit() {
    return costPerUnit;
  }

  String supplierName() {
    return supplierName;
  }

  String stockUomShortname() {
    return stockUomShortname;
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
