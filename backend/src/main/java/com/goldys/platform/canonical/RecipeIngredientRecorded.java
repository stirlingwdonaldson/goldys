package com.goldys.platform.canonical;

/**
 * Published after canonical recipe-ingredient facts are recorded, so the recipe-ingredient
 * projector can rebuild the resolved read model in the same transaction.
 */
public record RecipeIngredientRecorded(int parentRecipeId) {}
