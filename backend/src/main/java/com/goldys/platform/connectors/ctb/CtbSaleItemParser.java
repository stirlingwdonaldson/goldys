package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.ArrayList;
import java.util.List;

/** Parses CTB's {@code Sale/SearchSaleItemsByDateRange} envelope into per-product rows. */
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
              n.path("quantitySold").decimalValue(),
              n.path("amount").decimalValue()));
    }
    return out;
  }
}
