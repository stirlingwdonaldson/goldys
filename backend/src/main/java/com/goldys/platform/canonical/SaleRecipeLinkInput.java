package com.goldys.platform.canonical;

import java.util.UUID;

/** A normalized sale-item→recipe link from one source, to be recorded as a canonical version. */
public record SaleRecipeLinkInput(
    String sourceSystem,
    int saleItemRecipeId,
    String saleItemStockCode,
    String saleItemStockDescription,
    int recipeId,
    String recipeName,
    String recipeFolderName,
    Boolean isRecipeActive,
    Integer lightspeedProductId,
    UUID rawRecordId) {}
