package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldys.platform.semantic.RecipeCatalog;
import com.goldys.platform.semantic.RecipeFolder;
import com.goldys.platform.semantic.RecipeOutletAllocation;
import java.time.Instant;
import java.util.*;

/** Builds a catalog only when every discovered folder response has been captured. */
final class CtbRecipeCatalogParser {
  private final Map<Integer, RecipeFolder> folders = new LinkedHashMap<>();
  private final Set<RecipeOutletAllocation> allocations = new LinkedHashSet<>();
  private final Set<Integer> fetched = new HashSet<>();

  void accept(int requestedFolder, byte[] bytes) {
    JsonNode roots = CtbRecipeDefinitionParser.json(bytes);
    if (!roots.isArray()
        || roots.size() != 1
        || !roots.get(0).path("id").asText().equals("folder;" + requestedFolder))
      throw CtbRecipeDefinitionParser.schema("Folder response identity mismatch");
    RecipeFolder known = folders.get(requestedFolder);
    visit(roots.get(0), known == null ? null : known.parentId());
    fetched.add(requestedFolder);
  }

  private void visit(JsonNode node, Integer parent) {
    String id = node.path("id").asText();
    if (id.equals("recipe;loadingindicator")) return;
    String[] parts = id.split(";");
    if (parts.length != 2 || !(parts[0].equals("folder") || parts[0].equals("recipe")))
      throw CtbRecipeDefinitionParser.schema("Unknown recipe tree node");
    int number;
    try {
      number = Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      throw CtbRecipeDefinitionParser.schema("Invalid recipe tree id");
    }
    if (number < 0) throw CtbRecipeDefinitionParser.schema("Negative recipe tree id");
    JsonNode outlets = node.get("outlets");
    if (outlets == null || !outlets.isArray())
      throw CtbRecipeDefinitionParser.schema("Missing outlet allocations");
    for (JsonNode outlet : outlets) {
      Boolean deallocated = CtbRecipeDefinitionParser.bool(outlet, "isDeallocated");
      if (deallocated == null) throw CtbRecipeDefinitionParser.schema("Missing allocation state");
      allocations.add(
          new RecipeOutletAllocation(
              parts[0],
              number,
              CtbRecipeDefinitionParser.requiredInt(outlet, "outletId"),
              deallocated));
    }
    if (parts[0].equals("folder")) {
      // An expanded response labels its root "Recipes". The parent's child node carries the
      // actual folder name, so retain that first-seen name and parent relationship.
      RecipeFolder old =
          folders.putIfAbsent(
              number,
              new RecipeFolder(number, parent, CtbRecipeDefinitionParser.string(node, "text")));
      if (old != null && !Objects.equals(old.parentId(), parent))
        throw CtbRecipeDefinitionParser.schema("Recipe folder has conflicting parents");
      JsonNode children = node.get("children");
      if (children == null || !children.isArray())
        throw CtbRecipeDefinitionParser.schema("Missing folder children");
      for (JsonNode child : children) visit(child, number);
    }
  }

  List<Integer> pending() {
    if (folders.isEmpty()) return List.of(0);
    return folders.keySet().stream().filter(id -> !fetched.contains(id)).toList();
  }

  RecipeCatalog finish(Instant observedAt) {
    if (folders.isEmpty() || !pending().isEmpty())
      throw CtbRecipeDefinitionParser.schema("Incomplete recipe catalog");
    return new RecipeCatalog(List.copyOf(folders.values()), List.copyOf(allocations), observedAt);
  }
}
