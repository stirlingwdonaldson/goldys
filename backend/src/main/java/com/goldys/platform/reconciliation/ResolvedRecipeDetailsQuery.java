package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ResolvedRecipeDetailsQuery {
  private final ResolvedRecipeDefinitionRepository definitions;
  private final ResolvedRecipeCatalogRepository catalogs;
  private final ResolvedRecipeSectionRepository sections;
  private final ResolvedRecipeRepository legacy;
  private final ResolvedRecipeIngredientRepository legacyIngredients;

  @org.springframework.beans.factory.annotation.Value("${ctb.recipes.max-age:PT36H}")
  private Duration maxAge;

  ResolvedRecipeDetailsQuery(
      ResolvedRecipeDefinitionRepository definitions,
      ResolvedRecipeCatalogRepository catalogs,
      ResolvedRecipeSectionRepository sections,
      ResolvedRecipeRepository legacy,
      ResolvedRecipeIngredientRepository legacyIngredients) {
    this.definitions = definitions;
    this.catalogs = catalogs;
    this.sections = sections;
    this.legacy = legacy;
    this.legacyIngredients = legacyIngredients;
  }

  @Transactional(readOnly = true)
  public Optional<RecipeDetails> details(int recipeId) {
    var definition = definitions.findById(recipeId).map(ResolvedRecipeDefinition::definition);
    var old = legacy.findById(recipeId);
    List<RecipeSection> observations =
        new ArrayList<>(
            sections.findByRecipeId(recipeId).stream()
                .map(ResolvedRecipeSection::observation)
                .toList());
    List<RecipeSection> selected =
        observations.stream()
            .filter(RecipeSection::successful)
            .sorted(java.util.Comparator.comparing(RecipeSection::section))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Map<String, RecipeSection> attempts = new LinkedHashMap<>();
    observations.forEach(
        v ->
            attempts.merge(
                v.section(),
                v,
                (a, b) -> a.evidence().capturedAt().isAfter(b.evidence().capturedAt()) ? a : b));
    var global =
        sections.findByRecipeId(0).stream()
            .map(ResolvedRecipeSection::observation)
            .filter(RecipeSection::successful)
            .toList();
    String name =
        definition
            .map(v -> v.header().name())
            .orElseGet(() -> old.map(ResolvedRecipe::recipeName).orElse(null));
    var listing = catalogueEntry(recipeId, global);
    if (listing.isPresent()) {
      selected.add(listing.get());
      java.time.Instant headerAt =
          definition.map(RecipeDefinition::observedAt).orElse(java.time.Instant.MIN);
      if (name == null || listing.get().evidence().capturedAt().isAfter(headerAt))
        name = catalogueName(recipeId, global);
    }
    if (name == null && definition.isEmpty() && old.isEmpty() && observations.isEmpty())
      return Optional.empty();
    List<RecipeReference> parents = new ArrayList<>(), children = new ArrayList<>();
    parents.addAll(parents(recipeId));
    definition.ifPresent(
        v ->
            v.usages()
                .forEach(
                    u ->
                        children.add(
                            new RecipeReference(
                                u.recipeId(),
                                u.name(),
                                definitions.existsById(u.recipeId())
                                    || legacy.existsById(u.recipeId())))));
    String choice =
        definition.isPresent()
            ? old.isPresent()
                    && RecipeDefinitionReadAdapter.legacyIsNewer(old.get(), definition.get())
                ? "Newer equivalent legacy CTB cost observation; composition remains the complete refresh"
                : "Latest complete CTB composition and cost observation"
            : old.isPresent()
                ? "Legacy CTB fallback; complete composition not received"
                : "Recipe listed; complete details not received";
    List<RecipeEvidence> core = calculationSources(recipeId);

    selected.addAll(legacySections(recipeId, old, definition.isPresent()));
    selected.addAll(global.stream().filter(v -> v.section().equals("EQUIPMENT_OPTIONS")).toList());
    selected.addAll(
        folderEvidence(definition.map(v -> v.header().folderId()).orElse(null), global));
    return Optional.of(
        new RecipeDetails(
            recipeId,
            name,
            choice,
            selected,
            List.copyOf(attempts.values()),
            parents,
            children,
            core,
            null,
            false,
            maxAge.getSeconds()));
  }

  private Optional<RecipeSection> catalogueEntry(int id, List<RecipeSection> global) {
    for (var section : global)
      if (section.section().equals("LIST")) {
        String prefix =
            section.fields().stream()
                .filter(
                    f -> f.path().endsWith(".recipeId") && Integer.toString(id).equals(f.value()))
                .map(f -> f.path().substring(0, f.path().lastIndexOf('.')))
                .findFirst()
                .orElse(null);
        if (prefix != null)
          return Optional.of(
              new RecipeSection(
                  id,
                  "CATALOGUE_ENTRY",
                  true,
                  "Recipe-list capture; it does not establish detail or cost freshness",
                  section.fields().stream().filter(f -> f.path().startsWith(prefix + ".")).toList(),
                  section.evidence()));
      }
    return Optional.empty();
  }

  private String catalogueName(int recipeId, List<RecipeSection> global) {
    for (var section : global)
      if (section.section().equals("LIST")) {
        String prefix =
            section.fields().stream()
                .filter(
                    f ->
                        f.path().endsWith(".recipeId")
                            && Integer.toString(recipeId).equals(f.value()))
                .map(f -> f.path().substring(0, f.path().lastIndexOf('.')))
                .findFirst()
                .orElse(null);
        if (prefix != null)
          return section.fields().stream()
              .filter(f -> f.path().equals(prefix + ".recipeName"))
              .map(RecipeField::value)
              .findFirst()
              .orElse(null);
      }
    return null;
  }

  private List<RecipeReference> parents(int recipeId) {
    List<RecipeReference> parents = new ArrayList<>();
    for (var row : definitions.findParents(recipeId))
      if (row.definition().usages().stream().anyMatch(u -> u.recipeId() == recipeId))
        parents.add(
            new RecipeReference(
                row.definition().header().recipeId(), row.definition().header().name(), true));
    return parents;
  }

  private List<RecipeEvidence> calculationSources(int recipeId) {
    Set<Integer> reachable = new LinkedHashSet<>();
    Deque<Integer> pending = new ArrayDeque<>();
    pending.add(recipeId);
    List<RecipeEvidence> core = new ArrayList<>();
    while (!pending.isEmpty() && reachable.size() < 10000) {
      int id = pending.removeFirst();
      if (!reachable.add(id)) continue;
      definitions
          .findById(id)
          .ifPresent(
              row -> {
                var v = row.definition();
                v.usages().forEach(u -> pending.add(u.recipeId()));
                sections.findByRecipeId(id).stream()
                    .map(ResolvedRecipeSection::observation)
                    .filter(
                        x ->
                            x.successful() && v.rawRecordIds().contains(x.evidence().rawRecordId()))
                    .map(RecipeSection::evidence)
                    .forEach(core::add);
              });
    }
    return core;
  }

  private List<RecipeSection> legacySections(
      int recipeId, Optional<ResolvedRecipe> old, boolean hasDefinition) {
    List<RecipeSection> selected = new ArrayList<>();
    old.ifPresent(
        v -> {
          for (var e : v.costEvidence())
            selected.add(
                new RecipeSection(
                    recipeId,
                    "LEGACY_COST",
                    true,
                    "Original legacy import; compare its capture time with the complete refresh",
                    List.of(
                        new RecipeField(
                            "costPerPortion", Objects.toString(v.costPerPortion(), null), "NUMBER"),
                        new RecipeField(
                            "totalCost", Objects.toString(v.totalCost(), null), "NUMBER")),
                    e));
          for (var e : v.priceEvidence())
            selected.add(
                new RecipeSection(
                    recipeId,
                    "POS_PRICE_INPUT",
                    true,
                    "Sold-price/cost input, matched by the existing name association and modal-value calculation; not a verified current menu price",
                    List.of(
                        new RecipeField(
                            "menuPrice", Objects.toString(v.menuPrice(), null), "NUMBER"),
                        new RecipeField(
                            "actualCost", Objects.toString(v.actualCost(), null), "NUMBER"),
                        new RecipeField(
                            "lastSoldPrice", Objects.toString(v.lastSoldPrice(), null), "NUMBER"),
                        new RecipeField(
                            "lastSoldDate", Objects.toString(v.lastSoldDate(), null), "TEXT")),
                    e));
        });
    if (!hasDefinition)
      for (var line : legacyIngredients.findByParentRecipeId(recipeId))
        if (line.sourceEvidence() != null)
          selected.add(
              new RecipeSection(
                  recipeId,
                  "LEGACY_INGREDIENT_" + line.recipeDetailId() + "_" + line.stockId(),
                  true,
                  "Legacy flattened ingredient fallback; not combined with explicit composition",
                  List.of(
                      new RecipeField("stockDescription", line.stockDescription(), "TEXT"),
                      new RecipeField(
                          "portionWeight", Objects.toString(line.portionWeight(), null), "NUMBER"),
                      new RecipeField("unitOfMeasurement", line.unitOfMeasurement(), "TEXT"),
                      new RecipeField(
                          "costPerQty", Objects.toString(line.costPerQty(), null), "NUMBER")),
                  line.sourceEvidence()));
    return selected;
  }

  private List<RecipeSection> folderEvidence(Integer folderId, List<RecipeSection> global) {
    List<RecipeSection> selected = new ArrayList<>();
    var catalog = catalogs.findById(1).map(ResolvedRecipeCatalog::catalog);
    if (catalog.isPresent()) {
      Set<Integer> folders = new HashSet<>();
      Integer next = folderId;
      while (next != null && folders.add(next)) {
        int id = next;
        var match = catalog.get().folders().stream().filter(f -> f.folderId() == id).findFirst();
        next = match.isPresent() ? match.get().parentId() : null;
      }
      for (var section : global)
        if (section.section().startsWith("FOLDER_")) {
          int folder = Integer.parseInt(section.section().substring(7));
          if (folders.contains(folder)) selected.add(section);
        }
    }
    return selected;
  }
}
