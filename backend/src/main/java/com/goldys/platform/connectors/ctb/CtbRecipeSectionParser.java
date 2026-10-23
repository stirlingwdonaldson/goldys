package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldys.platform.semantic.*;
import java.util.*;

/** Field paths identify the exact scalar in the retained response, not inferred business facts. */
public class CtbRecipeSectionParser {
  public RecipeSection parse(int id, String section, byte[] bytes, RecipeEvidence evidence) {
    JsonNode root = CtbRecipeDefinitionParser.json(bytes);
    if (section.equals("HEADER") && CtbRecipeDefinitionParser.requiredInt(root, "recipeId") != id)
      throw CtbRecipeDefinitionParser.schema("Recipe section identity mismatch");
    if (!section.equals("HEADER") && !section.startsWith("FOLDER_") && !root.has("data"))
      throw CtbRecipeDefinitionParser.schema("Missing recipe section data");
    JsonNode data =
        section.equals("HEADER") || section.startsWith("FOLDER_") ? root : root.get("data");
    if (section.equals("EQUIPMENT") && (!data.isArray() || !data.isEmpty()))
      throw CtbRecipeDefinitionParser.schema("Populated equipment schema is not yet verified");
    if (Set.of(
                "LIST",
                "STOCKS",
                "USAGES",
                "DIETARY",
                "ALLERGENS",
                "EQUIPMENT",
                "EQUIPMENT_OPTIONS",
                "INGREDIENT_METHODS")
            .contains(section)
        && !data.isArray()) throw CtbRecipeDefinitionParser.schema("Expected section rows");
    if ((section.equals("DIETARY") || section.equals("ALLERGENS")) && data.isArray())
      for (JsonNode n : data) {
        CtbRecipeDefinitionParser.requiredInt(
            n, section.equals("DIETARY") ? "dietaryRequirementId" : "allergenId");
        if (!n.path("isChecked").isBoolean())
          throw CtbRecipeDefinitionParser.schema("Missing selected state");
      }
    if (section.equals("INGREDIENT_METHODS") && !data.isEmpty())
      throw CtbRecipeDefinitionParser.schema(
          "Populated ingredient-method schema is not yet verified");
    if (section.equals("NUTRITION") && !data.isObject())
      throw CtbRecipeDefinitionParser.schema("Expected nutrition fields");
    if (data.isArray() && root.path("totalCount").asInt(data.size()) > data.size())
      throw CtbRecipeDefinitionParser.schema("Recipe section response is truncated");
    if (section.equals("LIST")) validateList(data);
    List<RecipeField> fields = new ArrayList<>();
    flatten(data, section.equals("HEADER") || section.startsWith("FOLDER_") ? "" : "data", fields);
    return new RecipeSection(id, section, true, null, fields, evidence);
  }

  private void validateList(JsonNode rows) {
    Set<Integer> ids = new HashSet<>();
    for (var row : rows)
      if (!ids.add(CtbRecipeDefinitionParser.requiredInt(row, "recipeId")))
        throw CtbRecipeDefinitionParser.schema("Repeated recipe list identity");
  }

  private void flatten(JsonNode n, String path, List<RecipeField> out) {
    if (path.length() > 255)
      throw CtbRecipeDefinitionParser.schema("Recipe field path exceeds supported length");
    if (out.size() > 20000)
      throw CtbRecipeDefinitionParser.schema("Recipe section exceeds field limit");
    if (n.isObject()) {
      if (n.isEmpty()) out.add(new RecipeField(path, "{}", "EMPTY_OBJECT"));
      n.fields()
          .forEachRemaining(
              e -> {
                // Account/session and approval-user details are not recipe business fields.
                if (!Set.of(
                        "userApprovalInfo",
                        "userRequestForApprovalInfo",
                        "isHavingApprovalPermission",
                        "isRequireApprovalPermission")
                    .contains(e.getKey()))
                  flatten(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out);
              });
    } else if (n.isArray()) {
      if (n.isEmpty()) out.add(new RecipeField(path, "[]", "EMPTY_ARRAY"));
      for (int i = 0; i < n.size(); i++) flatten(n.get(i), path + "[" + i + "]", out);
    } else
      out.add(
          new RecipeField(
              path,
              n.isNull()
                  ? null
                  : n.isTextual() ? RecipePlainText.extract(n.textValue()) : n.asText(),
              n.isNull() ? "NULL" : n.isBoolean() ? "BOOLEAN" : n.isNumber() ? "NUMBER" : "TEXT"));
  }
}
