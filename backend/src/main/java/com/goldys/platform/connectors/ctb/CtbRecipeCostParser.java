package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.RecipeCostInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's {@code RecipeBook/GetSelectedRecipeFullRecords} envelope (the batched per-recipe
 * derived cost) into canonical recipe-cost inputs. CTB JSON may contain literal {@code \u0000}
 * escapes; string fields are sanitised so no control character reaches a canonical column.
 */
@Component
public class CtbRecipeCostParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<RecipeCostInput> parse(byte[] json, UUID rawRecordId) {
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
    List<RecipeCostInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      String portionSize = sanitize(n.get("portionSize"));
      out.add(
          new RecipeCostInput(
              "CTB",
              n.get("recipeId").asInt(),
              sanitize(n.get("recipeName")),
              decimal(n.get("costPerPortion")),
              decimal(n.get("costPerPortionIncludingLabour")),
              decimal(n.get("totalCost")),
              portionSize,
              portionSizeValue(portionSize),
              rawRecordId));
    }
    return out;
  }

  /** Parse the raw {@code portionSize} text to a numeric value when it is a plain number. */
  private static BigDecimal portionSizeValue(String portionSize) {
    if (portionSize == null || portionSize.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(portionSize.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal decimal(JsonNode n) {
    return n == null || n.isNull() ? null : n.decimalValue();
  }

  private static String sanitize(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    return n.asText().replace("\u0000", "");
  }
}
