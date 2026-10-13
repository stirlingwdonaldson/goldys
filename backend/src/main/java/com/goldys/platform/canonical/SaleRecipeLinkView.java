package com.goldys.platform.canonical;

/**
 * A read-only view of one current sale-item→recipe link, for modules outside the {@code canonical}
 * package. {@code saleItemStockDescription} is the menu-item name used to match the recipe to its
 * Lightspeed sale price.
 */
public record SaleRecipeLinkView(
    int recipeId,
    String recipeName,
    String recipeFolderName,
    String saleItemStockDescription,
    Boolean isRecipeActive,
    int saleItemRecipeId,
    Integer lightspeedProductId) {}
