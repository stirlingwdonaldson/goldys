package com.goldys.platform.semantic;

import java.util.List;

public record RecipeDetails(
    int recipeId,
    String name,
    String costSelection,
    List<RecipeSection> sections,
    List<RecipeSection> latestAttempts,
    List<RecipeReference> usedIn,
    List<RecipeReference> uses,
    List<RecipeEvidence> compositionSources,
    ConnectorHealth collection,
    boolean collectionEnabled,
    long freshnessLimitSeconds) {}
