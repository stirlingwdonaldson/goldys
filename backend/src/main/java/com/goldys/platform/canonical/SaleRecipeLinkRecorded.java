package com.goldys.platform.canonical;

/**
 * Published after a canonical sale-item→recipe link is recorded, so the recipe projector can update
 * the affected recipe's resolved projection in the same transaction.
 */
public record SaleRecipeLinkRecorded(int recipeId) {}
