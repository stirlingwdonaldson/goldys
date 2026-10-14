package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's wastage-detail envelope ({@code
 * WastageRecord/GetWastageRecordDetailByWastageRecordId}) into canonical wastage inputs. Wastage
 * has no stock code in the source, so product identity is the normalized {@code stockDesc}. CTB
 * JSON may contain literal {@code \u0000} escapes; string fields are sanitised before they reach a
 * canonical column.
 */
@Component
public class CtbWastageParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public List<WastageInput> parse(byte[] json, UUID rawRecordId) {
    JsonNode data = data(json);
    if (data == null) {
      return List.of();
    }
    List<WastageInput> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new WastageInput(
              "CTB",
              requiredInt(n.get("wastageRecordId"), "wastageRecordId"),
              requiredInt(n.get("wastageRecordDetailId"), "wastageRecordDetailId"),
              requiredDate(n.get("recordDate")),
              ProductNameKey.normalize(sanitize(n.get("stockDesc"))),
              requiredDecimal(n.get("quantity"), "quantity"),
              decimal(n.get("cost")),
              sanitize(n.get("reason")),
              sanitize(n.get("supplierName")),
              sanitize(n.get("unitOfMeasurementName")),
              sanitize(n.get("unitSize")),
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
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Missing wastage record date");
    }
    try {
      return LocalDate.parse(n.asText().trim());
    } catch (DateTimeParseException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad wastage record date '" + n.asText() + "'", e);
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

  private static BigDecimal requiredDecimal(JsonNode n, String field) {
    if (n == null || n.isNull()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Missing decimal field " + field);
    }
    if (n.isNumber()) {
      return n.decimalValue();
    }
    String s = n.asText();
    if (s == null || s.isBlank()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Missing decimal field " + field);
    }
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad decimal field " + field + ": '" + s + "'", e);
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

  private static String sanitize(JsonNode n) {
    if (n == null || n.isNull()) {
      return null;
    }
    return n.asText().replace("\u0000", "");
  }
}
