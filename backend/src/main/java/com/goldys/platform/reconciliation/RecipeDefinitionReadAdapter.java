package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeDefinition;
import com.goldys.platform.semantic.RecipeIngredient;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/** Adapts complete resolved observations to the established list/ingredient contracts. */
@Service
class RecipeDefinitionReadAdapter {
  @org.springframework.beans.factory.annotation.Value("${ctb.recipes.max-age:PT36H}")
  private java.time.Duration maxAge = java.time.Duration.ofHours(36);

  private final ResolvedRecipeDefinitionRepository definitions;
  private final ResolvedRecipeCatalogRepository catalogs;

  @org.springframework.beans.factory.annotation.Autowired
  private ResolvedRecipeSectionRepository sections;

  RecipeDefinitionReadAdapter(
      ResolvedRecipeDefinitionRepository definitions, ResolvedRecipeCatalogRepository catalogs) {
    this.definitions = definitions;
    this.catalogs = catalogs;
  }

  List<ResolvedRecipe> overlay(List<ResolvedRecipe> legacy) {
    Map<Integer, ResolvedRecipe> rows = new LinkedHashMap<>();
    legacy.forEach(row -> rows.put(row.recipeId(), row));
    Map<Integer, java.time.Instant> nameTimes = new HashMap<>();
    legacy.forEach(
        row ->
            row.costEvidence().stream()
                .map(com.goldys.platform.semantic.RecipeEvidence::capturedAt)
                .max(java.time.Instant::compareTo)
                .ifPresent(time -> nameTimes.put(row.recipeId(), time)));
    Map<Integer, String> folders = new HashMap<>();
    catalogs
        .findById(1)
        .ifPresent(c -> c.catalog().folders().forEach(f -> folders.put(f.folderId(), f.name())));
    for (ResolvedRecipeDefinition row : definitions.findAll()) {
      RecipeDefinition value = row.definition();
      var h = value.header();
      nameTimes.merge(h.recipeId(), value.observedAt(), (a, b) -> a.isAfter(b) ? a : b);
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
      ResolvedRecipe previous = old;
      boolean preferLegacy = legacyIsNewer(previous, value);
      ResolvedRecipe selected =
          new ResolvedRecipe(
              h.recipeId(),
              h.name(),
              folders.get(h.folderId()),
              preferLegacy ? previous.costPerPortion() : h.costPerPortion(),
              preferLegacy
                  ? previous.costPerPortionInclLabour()
                  : h.costPerPortionIncludingLabour(),
              preferLegacy ? previous.totalCost() : h.totalCost(),
              yield,
              h.portionSize(),
              previous.menuPrice(),
              previous.actualCost(),
              previous.menuState(),
              previous.priceSource(),
              previous.lastSoldPrice(),
              previous.lastSoldCost(),
              previous.lastSoldDate(),
              "SINGLE_SOURCE",
              "CTB",
              value.observedAt());
      selected.evidence(previous.costEvidence(), previous.priceEvidence());
      rows.put(h.recipeId(), selected);
    }
    addCatalogueRows(rows, nameTimes);
    return rows.values().stream()
        .sorted(Comparator.comparing(row -> Objects.toString(row.recipeName(), "")))
        .toList();
  }

  private void addCatalogueRows(
      Map<Integer, ResolvedRecipe> rows, Map<Integer, java.time.Instant> nameTimes) {
    if (sections != null)
      sections.findByRecipeId(0).stream()
          .map(ResolvedRecipeSection::observation)
          .filter(v -> v.successful() && v.section().equals("LIST"))
          .forEach(
              v -> {
                Map<String, String> names = new HashMap<>();
                for (var f : v.fields())
                  if (f.path().endsWith(".recipeName"))
                    names.put(f.path().substring(0, f.path().lastIndexOf('.')), f.value());
                for (var f : v.fields())
                  if (f.path().endsWith(".recipeId")) {
                    int id = Integer.parseInt(f.value());
                    String prefix = f.path().substring(0, f.path().lastIndexOf('.'));
                    rows.putIfAbsent(
                        id,
                        new ResolvedRecipe(
                            id,
                            names.get(prefix),
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
                            "LIST_ONLY",
                            "CTB",
                            v.evidence().capturedAt()));
                    var nameTime = nameTimes.get(id);
                    if (nameTime == null || v.evidence().capturedAt().isAfter(nameTime))
                      rows.put(id, rows.get(id).withName(names.get(prefix)));
                  }
              });
  }

  com.goldys.platform.semantic.RecipeCosting status(com.goldys.platform.semantic.RecipeCosting r) {
    var snapshot = definitions.findById(r.recipeId());
    java.time.Instant captured = r.capturedAt();
    String source = r.costPerPortion() == null ? "CTB catalogue only" : "CTB legacy import";
    String coverage = r.coverage();
    if (snapshot.isPresent()) {
      var value = snapshot.get().definition();
      if (captured == null || !captured.isAfter(value.observedAt())) {
        captured = value.observedAt();
        source = "CTB complete refresh";
      }
      coverage = "COMPLETE";
    } else if (r.costPerPortion() != null) coverage = "LEGACY";
    return new com.goldys.platform.semantic.RecipeCosting(
        r.recipeId(),
        r.name(),
        r.folder(),
        r.portionSize(),
        r.costPerPortion(),
        r.actualCost(),
        r.costGap(),
        r.menuPrice(),
        r.margin(),
        r.gpPercent(),
        r.menuState(),
        r.priceSource(),
        r.lastSoldPrice(),
        r.lastSoldCost(),
        r.lastSoldDate(),
        captured,
        source,
        coverage,
        captured != null && captured.isBefore(java.time.Instant.now().minus(maxAge)));
  }

  static boolean legacyIsNewer(ResolvedRecipe old, RecipeDefinition value) {
    return old != null
        && old.costEvidence().stream().anyMatch(e -> e.capturedAt().isAfter(value.observedAt()));
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
                          s.supplierName(),
                          s.stockUnit(),
                          s.usablePercent(),
                          s.wastagePercent(),
                          s.brokenLink())));
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
                          null,
                          null,
                          null,
                          null,
                          u.brokenLink())));
      return java.util.Optional.of(List.copyOf(lines));
    }

    return Optional.empty();
  }
}
