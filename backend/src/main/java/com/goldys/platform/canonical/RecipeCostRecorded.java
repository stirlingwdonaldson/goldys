package com.goldys.platform.canonical;

/**
 * Published after a canonical recipe-cost fact is recorded, so the recipe projector can update the
 * affected recipe's resolved projection in the same transaction.
 */
public record RecipeCostRecorded(int recipeId) {}
