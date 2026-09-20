package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parses CTB's {@code Revenue/SearchRevenues} envelope into revenue rows.
 *
 * <p>CTB stores dates as .NET ticks (100 ns since 0001-01-01); they are converted to a venue-local
 * date here.
 */
@Component
public class CtbRevenueParser {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ZoneId VENUE_ZONE = ZoneId.of("Australia/Melbourne");

  private static final long TICKS_PER_SECOND = 10_000_000L;
  private static final long EPOCH_TICKS = 621_355_968_000_000_000L; // 0001-01-01 -> 1970-01-01

  public List<CtbRevenue> parse(byte[] json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalArgumentException("Not JSON: " + e.getMessage(), e);
    }
    JsonNode data = root.get("data");
    if (data == null || !data.isArray()) {
      return List.of();
    }
    List<CtbRevenue> out = new ArrayList<>();
    for (JsonNode n : data) {
      out.add(
          new CtbRevenue(
              n.get("revenueId").asLong(),
              toLocalDate(n.get("revenueDate").asLong()),
              n.get("businessDepartmentName").asText(),
              decimal(n.get("kitchenRevenueTotal")),
              decimal(n.get("totalSales")),
              decimal(n.get("GSTTotal")),
              n.get("outletName").asText()));
    }
    return out;
  }

  private static LocalDate toLocalDate(long ticks) {
    Instant instant = Instant.ofEpochSecond((ticks - EPOCH_TICKS) / TICKS_PER_SECOND);
    return instant.atZone(VENUE_ZONE).toLocalDate();
  }

  private static BigDecimal decimal(JsonNode n) {
    return n == null || n.isNull() ? null : n.decimalValue();
  }
}
