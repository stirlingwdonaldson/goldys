package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.semantic.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Parses the three independent responses only after their exact bytes are safely in raw. */
@Component
public class CtbRecipeDefinitionParser {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  public RecipeDefinition parse(
      int recipeId, byte[] headerBytes, byte[] stockBytes, byte[] usageBytes, Instant observedAt) {
    JsonNode h = json(headerBytes);
    if (!h.isObject() || requiredInt(h, "recipeId") != recipeId)
      throw schema("Recipe header identity mismatch");
    List<RecipeStock> stocks = new ArrayList<>();
    for (JsonNode n : array(stockBytes)) {
      if (requiredInt(n, "recipeId") != recipeId) throw schema("Stock owner mismatch");
      stocks.add(stock(n));
    }
    List<RecipeUsage> usages = new ArrayList<>();
    for (JsonNode n : array(usageBytes)) {
      if (requiredInt(n, "recipeId") != recipeId) throw schema("Sub-recipe owner mismatch");
      usages.add(usage(n));
    }
    stocks = new ArrayList<>(new LinkedHashSet<>(stocks));
    requireUniqueIds(stocks.stream().map(line -> line.lineId() + ":" + line.stockId()).toList());
    usages = new ArrayList<>(new LinkedHashSet<>(usages));
    requireUniqueIds(usages.stream().map(RecipeUsage::lineId).toList());
    return new RecipeDefinition(header(h), stocks, usages, observedAt);
  }

  /** Exact duplicate rows are collapsed before identity validation, never silently merged. */
  private static void requireUniqueIds(List<?> ids) {
    if (new HashSet<>(ids).size() != ids.size()) throw schema("Repeated recipe line identity");
  }

  static JsonNode json(byte[] bytes) {
    try {
      JsonNode n = JSON.readTree(bytes);
      if (n == null
          || (n.path("message").has("IsSuccess")
              && !n.path("message").path("IsSuccess").asBoolean())
          || (n.has("IsSuccess") && !n.path("IsSuccess").asBoolean()))
        throw schema("CTB returned an unsuccessful recipe response");
      return n;
    } catch (java.io.IOException e) {
      throw schema("Recipe response is not JSON");
    }
  }

  static JsonNode array(byte[] bytes) {
    JsonNode data = json(bytes).get("data");
    if (data == null || !data.isArray())
      throw schema("Recipe lines require an explicit data array");
    return data;
  }

  private static RecipeHeader header(JsonNode n) {
    return new RecipeHeader(
        requiredInt(n, "recipeId"),
        string(n, "recipeName"),
        integer(n, "recipeFolderId"),
        bool(n, "isActive"),
        bool(n, "isLocked"),
        decimal(n, "noOfPortion"),
        decimal(n, "portionSize"),
        integer(n, "recipeUnitOfMeasurementId"),
        string(n, "recipeUnitOfMeasurementName"),
        decimal(n, "totalCost"),
        decimal(n, "costPerPortion"),
        decimal(n, "portionIncludingLabour"),
        decimal(n, "labourCost"),
        decimal(n, "menuSellingPrice"),
        decimal(n, "kitchenRevenue"),
        decimal(n, "lessGST"),
        decimal(n, "foodCost"),
        decimal(n, "grossProfit"),
        string(n, "recipeDesc"),
        string(n, "menuDescription"),
        string(n, "recipeHandyHints"),
        decimal(n, "timeTaken"),
        bool(n, "isProductionItem"),
        bool(n, "isCountableOnStocktake"),
        string(n, "cookingVideoURL"),
        string(n, "recipeDate"),
        string(n, "createdByName"),
        integer(n, "difficultyRating"),
        string(n, "recipeBarcode"),
        integer(n, "stockCategoryId"),
        decimal(n, "customFoodCostPercentageLimit"),
        decimal(n, "customFoodCostPercentageLimitWithLabour"));
  }

  private static RecipeStock stock(JsonNode n) {
    return new RecipeStock(
        requiredInt(n, "recipeDetailId"),
        integer(n, "stockId"),
        string(n, "stockCode"),
        string(n, "ingredientDescription"),
        string(n, "supplierName"),
        string(n, "supplierStockCode"),
        decimal(n, "portionWeight"),
        integer(n, "unitOfMeasurementId"),
        string(n, "unitOfMeasurementName"),
        decimal(n, "stockUnit"),
        integer(n, "stockUnitOfMeasurementId"),
        string(n, "stockUOMShortname"),
        decimal(n, "costPerUnit"),
        decimal(n, "costPerQty"),
        decimal(n, "stockUsable"),
        decimal(n, "stockWastage"),
        bool(n, "isBrokenLink"));
  }

  private static RecipeUsage usage(JsonNode n) {
    return new RecipeUsage(
        requiredInt(n, "recipeAsIngredientId"),
        requiredInt(n, "recipeUsedId"),
        string(n, "recipeName"),
        decimal(n, "portionWeight"),
        integer(n, "unitOfMeasurementId"),
        string(n, "uomName"),
        decimal(n, "originalPortionSize"),
        integer(n, "originalUOMId"),
        string(n, "originalUOM"),
        decimal(n, "portionWeightConverted"),
        decimal(n, "costPerPortion"),
        decimal(n, "costPerQty"),
        decimal(n, "costPerQtyIncludingLabour"),
        bool(n, "isBrokenLink"));
  }

  static int requiredInt(JsonNode n, String key) {
    Integer value = integer(n, key);
    if (value == null || value <= 0) throw schema("Missing or invalid recipe identity: " + key);
    return value;
  }

  static Integer integer(JsonNode n, String key) {
    JsonNode v = n.get(key);
    if (v == null || v.isNull()) return null;
    if (!v.isIntegralNumber() || !v.canConvertToInt()) throw schema("Invalid integer: " + key);
    return v.intValue();
  }

  static BigDecimal decimal(JsonNode n, String key) {
    JsonNode v = n.get(key);
    if (v == null || v.isNull()) return null;
    if (!v.isNumber()) throw schema("Invalid decimal: " + key);
    return v.decimalValue();
  }

  static String string(JsonNode n, String key) {
    JsonNode v = n.get(key);
    if (v == null || v.isNull()) return null;
    if (!v.isTextual()) throw schema("Invalid text: " + key);
    return v.textValue().replace("\u0000", "");
  }

  static Boolean bool(JsonNode n, String key) {
    JsonNode v = n.get(key);
    if (v == null || v.isNull()) return null;
    if (!v.isBoolean()) throw schema("Invalid boolean: " + key);
    return v.booleanValue();
  }

  static ConnectorFetchException schema(String message) {
    return new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message);
  }
}
