package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parses the GuestCenter reservation CSV. The header is read from the CSV and the required columns
 * are validated, so a report-shape change fails loudly rather than silently producing wrong rows.
 */
@Component
public class OpenTableCsvParser {

  private final ZoneId zone;

  public OpenTableCsvParser(@Value("${opentable.timezone:Australia/Sydney}") String zone) {
    this.zone = ZoneId.of(zone);
  }

  public List<OpenTableReservation> parse(byte[] csv) {
    List<CSVRecord> records = readAll(csv);
    if (records.size() < 2) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "OpenTable CSV is empty");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(
        columns,
        "Reservation ID",
        "Date",
        "Time",
        "Party Size",
        "Status",
        "Table",
        "Source",
        "Guest Name");

    List<OpenTableReservation> out = new ArrayList<>();
    int width = columns.size();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (r.size() != width) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "OpenTable CSV row has wrong column count");
      }
      String id = requireValue(columns, r, "Reservation ID");
      String status = normalizeStatus(requireValue(columns, r, "Status"));
      out.add(
          new OpenTableReservation(
              id,
              at(r, columns),
              partySize(r, columns),
              status,
              blankToNull(get(columns, r, "Table")),
              blankToNull(get(columns, r, "Source")),
              blankToNull(get(columns, r, "Guest Name"))));
    }
    return out;
  }

  private Instant at(CSVRecord r, Map<String, Integer> columns) {
    String date = get(columns, r, "Date");
    String time = get(columns, r, "Time");
    try {
      LocalDateTime dt =
          LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()));
      return dt.atZone(zone).toInstant();
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Bad date/time '" + date + " " + time + "' in OpenTable CSV",
          e);
    }
  }

  private static int partySize(CSVRecord r, Map<String, Integer> columns) {
    String value = get(columns, r, "Party Size");
    try {
      return Integer.parseInt(value.trim());
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad party size '" + value + "' in OpenTable CSV", e);
    }
  }

  private static String normalizeStatus(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "booked" -> "BOOKED";
      case "seated" -> "SEATED";
      case "completed" -> "COMPLETED";
      case "cancelled", "canceled" -> "CANCELLED";
      case "no show", "no-show" -> "NO_SHOW";
      case "walk-in", "walk in" -> "WALK_IN";
      default -> raw.trim().toUpperCase(Locale.ROOT);
    };
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Malformed OpenTable CSV", e);
    }
  }

  private static Map<String, Integer> columnIndex(CSVRecord header) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      out.put(header.get(i).trim(), i);
    }
    return out;
  }

  private static void require(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      if (!columns.containsKey(name)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in OpenTable CSV");
      }
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }

  private static String requireValue(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = get(columns, r, name);
    if (value == null || value.isBlank()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Missing value for '" + name + "' in OpenTable CSV");
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
