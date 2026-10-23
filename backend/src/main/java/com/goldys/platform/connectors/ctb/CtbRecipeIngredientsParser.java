package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.RecipeIngredientInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's {@code RecipeBook/GetIngredientsIncludingSubRecipeIngredients} envelope (one request
 * per recipe) into canonical recipe-ingredient inputs.
 *
 * <p>{@code parentRecipeId} is the recipe the payload was fetched for (the raw record's {@code
 * source_record_ref = "recipe:{id}"}), not read from the lines: a line's own {@code recipeId} is
 * the recipe that line belongs to (the parent for main lines, a sub-recipe for {@code isSubRecipe}
 * lines). CTB JSON may contain literal {@code \u0000} escapes; string fields are sanitised so no
 * control character reaches a canonical column.
 */
@Component
public class CtbRecipeIngredientsParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<RecipeIngredientInput> parse(byte[] json, UUID rawRecordId, int parentRecipeId) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Not JSON: " + e.getMessage(), e);
    }
    JsonNode data = root.get("data");
    if (data == null || !data.isArray()) {
      return List.of();
    }
    List<RecipeIngredientInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new RecipeIngredientInput(
              "CTB",
              parentRecipeId,
              integer(n.get("recipeId")),
              sanitize(n.get("recipeName")),
              bool(n.get("isSubRecipeIngredient")),
              n.get("recipeDetailId").asInt(),
              stockId(n.get("stockId")),
              sanitize(n.get("stockCode")),
              sanitize(n.get("stockDesc")),
              sanitize(n.get("ingredientDescription")),
              sanitize(n.get("supplierName")),
              sanitize(n.get("supplierStockCode")),
              decimal(n.get("portionWeight")),
              sanitize(n.get("unitOfMeasurementName")),
              sanitize(
                  n.has("stockUOMShortname")
                      ? n.get("stockUOMShortname")
                      : n.get("stockUomShortname")),
              decimal(n.get("costPerQty")),
              decimal(n.get("costPerUnit")),
              decimal(n.get("stockUsable")),
              decimal(n.get("stockWastage")),
              bool(n.get("isBrokenLink")),
              rawRecordId));
    }
    // Collapse exact-duplicate lines (recipe 617 repeats four lines verbatim with the same detail
    // id AND stock id). A LinkedHashSet keeps first-seen order and drops byte-identical repeats.
    return new ArrayList<>(new LinkedHashSet<>(out));
  }

  /** A non-positive stock id is CTB's "no reference" sentinel, stored as null. */
  private static Integer stockId(JsonNode n) {
    Integer v = integer(n);
    return v == null || v <= 0 ? null : v;
  }

  private static Integer integer(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    if (n.isIntegralNumber()) {
      return n.asInt();
    }
    String s = n.asText();
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Defensive BigDecimal parse: null on missing or non-numeric text, never throws. */
  private static BigDecimal decimal(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    if (n.isNumber()) {
      return n.decimalValue();
    }
    String s = n.asText();
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Boolean bool(JsonNode n) {
    return n == null || n.isNull() ? null : n.asBoolean();
  }

  private static String sanitize(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    return n.asText().replace("\u0000", "");
  }
}
