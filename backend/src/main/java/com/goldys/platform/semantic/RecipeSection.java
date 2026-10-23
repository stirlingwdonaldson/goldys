package com.goldys.platform.semantic;

import java.util.List;

public record RecipeSection(
    int recipeId,
    String section,
    boolean successful,
    String notice,
    List<RecipeField> fields,
    RecipeEvidence evidence) {
  public RecipeSection {
    fields = List.copyOf(fields);
  }
}
