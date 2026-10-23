package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parses CTB's {@code Sale/SearchSaleItemsByDateRange} envelope into per-product rows. */
@Component
public class CtbSaleItemParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<CtbSaleItem> parse(byte[] json) {
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
    List<CtbSaleItem> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new CtbSaleItem(
              n.path("stockCode").asText(),
              n.path("stockDescription").asText(),
              decimal(n, "quantitySold"),
              decimal(n, "amount"),
              decimal(n, "costPerUnit"),
              decimal(n, "foodCost"),
              decimal(n, "totalFoodCost"),
              decimal(n, "profit"),
              decimal(n, "discountedAmount"),
              decimal(n, "taxAmount"),
              decimal(n, "unitPrice"),
              text(n, "uniqueGroupId"),
              n.path("siteNumber").isIntegralNumber() ? n.path("siteNumber").intValue() : null,
              rowId(n)));
    }
    return out;
  }

  private static String rowId(JsonNode row) {
    JsonNode id = row.get("uniqueId");
    if (id == null || id.isNull()) return null;
    if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 0)
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Invalid sale item row identity");
    return id.asText();
  }

  boolean isCompleteReport(byte[] json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      return root.path("data").isArray()
          && root.path("totalCount").isIntegralNumber()
          && root.path("totalCount").intValue() == root.path("data").size();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * True when a stored payload is a sale-items envelope (its {@code data} rows carry {@code
   * stockCode}/{@code stockDescription}) rather than a revenue envelope. Used by replay to tell
   * product-sales evidence from unrelated CTB revenue pages, which share the {@code ctb-revenue}
   * fetcher identity.
   */
  public boolean isSaleItemEnvelope(byte[] json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception e) {
      return false;
    }
    JsonNode data = root.get("data");
    if (data == null || !data.isArray() || data.isEmpty()) {
      return false;
    }
    for (JsonNode n : data) {
      if (n.has("stockCode") || n.has("stockDescription")) {
        return true;
      }
      if (n.has("revenueId")) {
        return false;
      }
    }
    return false;
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.decimalValue();
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }
}
