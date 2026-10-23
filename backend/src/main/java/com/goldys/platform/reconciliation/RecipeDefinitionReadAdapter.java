package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeDefinition;
import com.goldys.platform.semantic.RecipeIngredient;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/** Adapts complete resolved observations to the established list/ingredient contracts. */
@Service
class RecipeDefinitionReadAdapter {
  private final ResolvedRecipeDefinitionRepository definitions;
  private final ResolvedRecipeCatalogRepository catalogs;

  RecipeDefinitionReadAdapter(
      ResolvedRecipeDefinitionRepository definitions, ResolvedRecipeCatalogRepository catalogs) {
    this.definitions = definitions;
    this.catalogs = catalogs;
  }

  List<ResolvedRecipe> overlay(List<ResolvedRecipe> legacy) {
    Map<Integer, ResolvedRecipe> rows = new LinkedHashMap<>();
    legacy.forEach(row -> rows.put(row.recipeId(), row));
    Map<Integer, String> folders = new HashMap<>();
    catalogs
        .findById(1)
        .ifPresent(c -> c.catalog().folders().forEach(f -> folders.put(f.folderId(), f.name())));
    for (ResolvedRecipeDefinition row : definitions.findAll()) {
      RecipeDefinition value = row.definition();
      var h = value.header();
      ResolvedRecipe old = rows.get(h.recipeId());
      if (old == null)
        old =
            new ResolvedRecipe(
                h.recipeId(),
                h.name(),
                null,
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
                null,
                "SINGLE_SOURCE",
                "CTB",
                value.observedAt());
      String yield =
          h.portionSize() == null
              ? null
              : h.portionSize().stripTrailingZeros().toPlainString()
                  + " "
                  + Objects.toString(h.unit(), "");
      if (yield != null
          && h.portionCount() != null
          && h.portionCount().compareTo(BigDecimal.ONE) != 0)
        yield = h.portionCount().stripTrailingZeros().toPlainString() + " × " + yield;
      rows.put(
          h.recipeId(),
          new ResolvedRecipe(
              h.recipeId(),
              h.name(),
              folders.get(h.folderId()),
              h.costPerPortion(),
              h.costPerPortionIncludingLabour(),
              h.totalCost(),
              yield,
              h.portionSize(),
              old.menuPrice(),
              old.actualCost(),
              old.menuState(),
              old.priceSource(),
              old.lastSoldPrice(),
              old.lastSoldCost(),
              old.lastSoldDate(),
              "SINGLE_SOURCE",
              "CTB",
              value.observedAt()));
    }
    return rows.values().stream()
        .sorted(Comparator.comparing(row -> Objects.toString(row.recipeName(), "")))
        .toList();
  }

  Optional<List<RecipeIngredient>> ingredientsFor(int recipeId) {
    var snapshot = definitions.findById(recipeId);
    if (snapshot.isPresent()) {
      var value = snapshot.get().definition();
      List<RecipeIngredient> lines = new ArrayList<>();
      value
          .stocks()
          .forEach(
              s ->
                  lines.add(
                      new RecipeIngredient(
                          recipeId,
                          recipeId,
                          value.header().name(),
                          false,
                          s.lineId(),
                          s.stockId(),
                          s.stockCode(),
                          s.description(),
                          s.quantity(),
                          s.unit(),
                          s.cost(),
                          s.costPerPack(),
                          s.supplierName())));
      value
          .usages()
          .forEach(
              u ->
                  lines.add(
                      new RecipeIngredient(
                          recipeId,
                          u.recipeId(),
                          u.name(),
                          true,
                          u.lineId(),
                          null,
                          null,
                          u.name(),
                          u.quantity(),
                          u.unit(),
                          u.cost(),
                          null,
                          null)));
      return java.util.Optional.of(List.copyOf(lines));
    }

    return Optional.empty();
  }
}
