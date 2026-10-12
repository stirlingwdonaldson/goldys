package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source's sale-item→recipe link. Fact fields are immutable; a correction closes
 * this row and inserts a successor sharing the same logical identity (the link id).
 */
@Entity
@Table(name = "canonical_sale_recipe_link")
class CanonicalSaleRecipeLink extends BitemporalEntity {
  @Column(name = "sale_item_recipe_id", nullable = false, updatable = false)
  private int saleItemRecipeId;

  @Column(name = "sale_item_stock_code", updatable = false)
  private String saleItemStockCode;

  @Column(name = "sale_item_stock_description", updatable = false)
  private String saleItemStockDescription;

  @Column(name = "recipe_id", nullable = false, updatable = false)
  private int recipeId;

  @Column(name = "recipe_name", updatable = false)
  private String recipeName;

  @Column(name = "recipe_folder_name", updatable = false)
  private String recipeFolderName;

  @Column(name = "is_recipe_active", updatable = false)
  private Boolean isRecipeActive;

  protected CanonicalSaleRecipeLink() {}

  private CanonicalSaleRecipeLink(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int saleItemRecipeId,
      String saleItemStockCode,
      String saleItemStockDescription,
      int recipeId,
      String recipeName,
      String recipeFolderName,
      Boolean isRecipeActive) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.saleItemRecipeId = saleItemRecipeId;
    this.saleItemStockCode = saleItemStockCode;
    this.saleItemStockDescription = saleItemStockDescription;
    this.recipeId = recipeId;
    this.recipeName = recipeName;
    this.recipeFolderName = recipeFolderName;
    this.isRecipeActive = isRecipeActive;
  }

  static CanonicalSaleRecipeLink create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int saleItemRecipeId,
      String saleItemStockCode,
      String saleItemStockDescription,
      int recipeId,
      String recipeName,
      String recipeFolderName,
      Boolean isRecipeActive) {
    return new CanonicalSaleRecipeLink(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        saleItemRecipeId,
        saleItemStockCode,
        saleItemStockDescription,
        recipeId,
        recipeName,
        recipeFolderName,
        isRecipeActive);
  }

  boolean sameFact(SaleRecipeLinkInput input) {
    return Objects.equals(saleItemStockCode, input.saleItemStockCode())
        && Objects.equals(saleItemStockDescription, input.saleItemStockDescription())
        && recipeId == input.recipeId()
        && Objects.equals(recipeName, input.recipeName())
        && Objects.equals(recipeFolderName, input.recipeFolderName())
        && Objects.equals(isRecipeActive, input.isRecipeActive());
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

  String saleItemStockDescription() {
    return saleItemStockDescription;
  }

  String saleItemStockCode() {
    return saleItemStockCode;
  }

  Boolean isRecipeActive() {
    return isRecipeActive;
  }
}
