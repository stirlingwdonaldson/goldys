package com.goldys.platform.connectors.tanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldys.platform.canonical.LabourInput;
import com.goldys.platform.canonical.LabourMeasure;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Maps retained Tanda observations; pay-period envelopes are never counted as shifts. */
final class TandaLabourMapper {
  record Page(String dataset, JsonNode rows, UUID rawId) {}

  private record Observation(JsonNode row, UUID rawId) {}

  List<LabourInput> map(List<Page> pages, LocalDate from, LocalDate to, ZoneId zone) {
    Map<Long, String> teams = teams(pages);
    Map<String, Observation> observations = observations(pages);
    List<LabourInput> inputs = new ArrayList<>();
    for (var entry : observations.entrySet()) {
      JsonNode row = entry.getValue().row();
      boolean roster = entry.getKey().startsWith("tanda:schedule:");
      Instant start = instant(row, "start"), finish = instant(row, "finish");
      LocalDate date;
      try {
        date =
            roster
                ? Objects.requireNonNull(start).atZone(zone).toLocalDate()
                : LocalDate.parse(row.path("date").asText());
      } catch (RuntimeException e) {
        throw invalid("Observation date missing or invalid");
      }
      if (date.isBefore(from) || date.isAfter(to)) continue;
      String department = teams.get(id(row, "department_id"));
      if (department == null) throw invalid("Observation references an unknown team");
      BigDecimal hours = hours(row, start, finish, roster);
      BigDecimal cost = hours == null ? null : decimal(row, "cost");
      inputs.add(
          new LabourInput(
              "TANDA",
              entry.getKey(),
              row.path("user_id").isNumber() ? "tanda:" + row.path("user_id").asText() : null,
              department,
              date,
              roster ? hours : null,
              roster ? null : hours,
              roster ? cost : null,
              roster ? null : cost,
              start,
              finish,
              entry.getValue().rawId(),
              roster ? LabourMeasure.ROSTER : LabourMeasure.WORKED));
    }
    return List.copyOf(inputs);
  }

  private Map<Long, String> teams(List<Page> pages) {
    Map<Long, String> teams = new HashMap<>();
    Set<Long> locations = new HashSet<>();
    for (Page page : pages)
      if (page.dataset().equals("locations"))
        for (JsonNode row : page.rows()) locations.add(id(row, "id"));
    // This installation serves one pub. Multi-location accounts need an explicit venue filter.
    if (locations.size() != 1)
      throw invalid("Expected one Tanda venue; configure venue scope before reporting");
    for (Page page : pages)
      if (page.dataset().equals("departments")) {
        for (JsonNode row : page.rows()) {
          if (!locations.contains(id(row, "location_id")))
            throw invalid("Team is outside the selected venue");
          if (!row.path("name").isTextual() || row.path("name").asText().isBlank())
            throw invalid("Team name missing");
          teams.put(id(row, "id"), row.path("name").asText());
        }
      }
    return teams;
  }

  private Map<String, Observation> observations(List<Page> pages) {
    Map<String, Observation> observations = new LinkedHashMap<>();
    for (Page page : pages) {
      if (page.dataset().equals("schedules"))
        for (JsonNode row : page.rows()) add(observations, "schedule", row, page.rawId());
      if (page.dataset().equals("timesheets"))
        for (JsonNode timesheet : page.rows()) {
          if (!timesheet.path("shifts").isArray()) throw invalid("Timesheet shifts missing");
          for (JsonNode row : timesheet.path("shifts"))
            add(observations, "shift", row, page.rawId());
        }
    }
    return observations;
  }

  private void add(Map<String, Observation> observations, String type, JsonNode row, UUID rawId) {
    String key = "tanda:" + type + ":" + id(row, "id");
    Observation old = observations.get(key);
    if (old == null || row.path("updated_at").asLong() >= old.row().path("updated_at").asLong())
      observations.put(key, new Observation(row, rawId));
  }

  private BigDecimal hours(JsonNode row, Instant start, Instant finish, boolean roster) {
    if (start == null || finish == null) return null;
    long duration = finish.getEpochSecond() - start.getEpochSecond();
    if (duration < 0 || duration > 48 * 3600)
      throw invalid("Shift duration outside supported bounds");
    BigDecimal unpaid = unpaidBreakMinutes(row, roster);
    BigDecimal seconds =
        BigDecimal.valueOf(duration).subtract(unpaid.multiply(BigDecimal.valueOf(60)));
    if (seconds.signum() < 0) throw invalid("Unpaid breaks exceed shift duration");
    return seconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
  }

  private BigDecimal unpaidBreakMinutes(JsonNode row, boolean roster) {
    BigDecimal unpaid = BigDecimal.ZERO;
    JsonNode breaks = row.path("breaks");
    if (!breaks.isArray()) throw invalid("Break information missing");
    if (!breaks.isEmpty()) {
      for (JsonNode item : breaks) {
        if (!item.path("paid").isBoolean()) throw invalid("Break payment basis missing");
        if (!item.path("paid").asBoolean()) {
          BigDecimal length = decimal(item, "length");
          if (length == null) throw invalid("Break duration missing");
          unpaid = unpaid.add(length);
        }
      }
    } else {
      BigDecimal length = decimal(row, roster ? "automatic_break_length" : "break_length");
      if (length != null) unpaid = length;
    }
    return unpaid;
  }

  private static long id(JsonNode row, String field) {
    if (!row.path(field).canConvertToLong() || row.path(field).asLong() <= 0)
      throw invalid("Required source identity missing");
    return row.path(field).asLong();
  }

  private static Instant instant(JsonNode row, String field) {
    if (row.path(field).isMissingNode() || row.path(field).isNull()) return null;
    if (!row.path(field).isIntegralNumber()) throw invalid("Invalid shift timestamp");
    return Instant.ofEpochSecond(row.path(field).asLong());
  }

  private static BigDecimal decimal(JsonNode row, String field) {
    JsonNode value = row.path(field);
    if (value.isNull() || value.isMissingNode()) return null;
    if (!value.isNumber() || value.decimalValue().signum() < 0)
      throw invalid("Invalid hours or cost");
    return value.decimalValue();
  }

  private static ConnectorFetchException invalid(String message) {
    return new ConnectorFetchException(
        "TANDA_VALIDATION_FAILED", message + "; raw evidence retained");
  }
}
