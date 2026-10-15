package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.StockCountInput;
import com.goldys.platform.canonical.StocktakeInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.util.Nulls;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's stocktake envelope into canonical inputs: the {@code ctb-stocktakes} header list and
 * the paged {@code ctb-stocktake-detail} counted lines.
 *
 * <p>The detail payload carries no date and no stocktake id: the caller supplies {@code
 * stocktakeId} (from the raw record's {@code source_record_ref = "stocktake:<id>"}) and {@code
 * countedDate} (resolved from the header). CTB JSON may contain literal {@code \u0000} escapes;
 * string fields are sanitised so no control character reaches a canonical column.
 */
@Component
public class CtbStocktakeParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<StocktakeInput> parseStocktakes(byte[] json, UUID rawRecordId) {
    JsonNode data = data(json);
    if (data == null) {
      return List.of();
    }
    List<StocktakeInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new StocktakeInput(
              "CTB",
              requiredInt(n.get("stocktakeId"), "stocktakeId"),
              requiredDate(n.get("stocktakeDate")),
              sanitize(n.get("stocktakeDesc")),
              sanitize(n.get("outletName")),
              decimal(n.get("totalStockValue")),
              bool(n.get("isPartial")),
              bool(n.get("isLockStocktake")),
              rawRecordId));
    }
    return out;
  }

  public List<StockCountInput> parseStocktakeLines(
      byte[] json, UUID rawRecordId, int stocktakeId, LocalDate countedDate) {
    JsonNode data = data(json);
    if (data == null) {
      return List.of();
    }
    List<StockCountInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new StockCountInput(
              "CTB",
              stocktakeId,
              requiredInt(n.get("id"), "id"),
              countedDate,
              ProductNameKey.normalize(sanitize(n.get("stockDesc"))),
              Nulls.zeroIfNull(decimal(n.get("qtyCount"))),
              integer(n.get("stockId")),
              sanitize(n.get("stockCode")),
              sanitize(n.get("stockUnitOfMeasurementName")),
              decimal(n.get("stocktakeDetailCost")),
              decimal(n.get("subTotal")),
              sanitize(n.get("supplierName")),
              sanitize(n.get("stocktakeAreaName")),
              integer(n.get("recipeId")),
              rawRecordId));
    }
    // Collapse exact-duplicate lines so a repeated source row is not double-counted.
    return new ArrayList<>(new LinkedHashSet<>(out));
  }

  private static JsonNode data(byte[] json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Not JSON: " + e.getMessage(), e);
    }
    JsonNode data = root.get("data");
    return (data != null && data.isArray()) ? data : null;
  }

  private static LocalDate requiredDate(JsonNode n) {
    if (n == null || n.isNull() || n.asText().isBlank()) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Missing stocktake date");
    }
    try {
      return LocalDate.parse(n.asText().trim());
    } catch (DateTimeParseException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad stocktake date '" + n.asText() + "'", e);
    }
  }

  private static int requiredInt(JsonNode n, String field) {
    if (n == null || n.isNull()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Missing integer field " + field);
    }
    if (n.isIntegralNumber()) {
      return n.asInt();
    }
    String s = n.asText();
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad integer field " + field + ": '" + s + "'", e);
    }
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
