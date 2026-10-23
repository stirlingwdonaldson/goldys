package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.List;

/** Complete folder hierarchy and explicit outlet allocations, including empty folders. */
public record RecipeCatalog(
    List<RecipeFolder> folders, List<RecipeOutletAllocation> allocations, Instant observedAt) {
  public RecipeCatalog {
    folders = List.copyOf(folders);
    allocations = List.copyOf(allocations);
  }
}
