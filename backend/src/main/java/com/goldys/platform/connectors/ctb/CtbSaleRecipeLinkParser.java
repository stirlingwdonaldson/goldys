package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.SaleRecipeLinkInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's {@code searchDistinctSaleItemsForLinking} envelope (sale item → recipe links) into
 * canonical link inputs. String fields are sanitised of control characters.
 */
@Component
public class CtbSaleRecipeLinkParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<SaleRecipeLinkInput> parse(byte[] json, UUID rawRecordId) {
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
    List<SaleRecipeLinkInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new SaleRecipeLinkInput(
              "CTB",
              n.get("saleItemRecipeId").asInt(),
              sanitize(n.get("saleItemStockCode")),
              sanitize(n.get("saleItemStockDescription")),
              n.get("recipeId").asInt(),
              sanitize(n.get("recipeName")),
              sanitize(n.get("recipeFolderName")),
              bool(n.get("isRecipeActive")),
              null,
              rawRecordId));
    }
    return out;
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
