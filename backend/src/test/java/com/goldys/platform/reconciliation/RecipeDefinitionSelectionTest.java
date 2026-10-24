package com.goldys.platform.reconciliation;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.semantic.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class RecipeDefinitionSelectionTest {
  @Test
  void folderIdentityComesFromTheResolvedDefinitionNotItsDisplayName() {
    var definition = definition("first_314");
    var definitions = mock(ResolvedRecipeDefinitionRepository.class);
    var folders = mock(ResolvedRecipeCatalogRepository.class);
    when(definitions.findById(314))
        .thenReturn(Optional.of(new ResolvedRecipeDefinition(definition)));
    var adapter = new RecipeDefinitionReadAdapter(definitions, folders);
    var recipe =
        new RecipeCosting(
            314,
            "Martini",
            "Duplicate folder name",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "UNKNOWN",
            "NONE",
            null,
            null,
            null);
    assertThat(adapter.status(recipe).folderId()).isEqualTo(definition.header().folderId());
    when(definitions.findById(314)).thenReturn(Optional.empty());
    assertThat(adapter.status(recipe).folderId()).isNull();
  }

  @Test
  void comparesActualCaptureTimeAndNeverUsesProjectionRebuildTime() {
    var definition = definition("third_696");
    var definitions = mock(ResolvedRecipeDefinitionRepository.class);
    var folders = mock(ResolvedRecipeCatalogRepository.class);
    when(definitions.findAll()).thenReturn(List.of(new ResolvedRecipeDefinition(definition)));
    when(folders.findById(1)).thenReturn(Optional.empty());
    var legacy = mock(ResolvedRecipe.class);
    when(legacy.recipeId()).thenReturn(696);
    when(legacy.costPerPortion()).thenReturn(new BigDecimal("999"));
    var old =
        new RecipeEvidence(
            UUID.randomUUID(),
            "CTB",
            "legacy",
            "legacy",
            null,
            definition.observedAt().minusSeconds(1),
            null,
            null);
    var newer =
        new RecipeEvidence(
            UUID.randomUUID(),
            "CTB",
            "legacy",
            "legacy",
            null,
            definition.observedAt().plusSeconds(1),
            null,
            null);
    var adapter = new RecipeDefinitionReadAdapter(definitions, folders);
    when(legacy.costEvidence()).thenReturn(List.of(old));
    assertThat(adapter.overlay(List.of(legacy)).getFirst().costPerPortion())
        .isEqualByComparingTo(definition.header().costPerPortion());
    when(legacy.costEvidence()).thenReturn(List.of(newer));
    assertThat(adapter.overlay(List.of(legacy)).getFirst().costPerPortion())
        .isEqualByComparingTo("999");
  }
}
