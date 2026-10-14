package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parses the OpenTable GuestCenter reviews export into {@link OpenTableReviewDetail} rows. The
 * header is read from the CSV and the required columns are validated, so a report-shape change
 * fails loudly rather than silently producing wrong rows.
 *
 * <p>The parsed rows are not canonicalized yet (reviews canonicalization is a later slice); this
 * parser validates the CSV shape and hands the typed rows to that slice.
 */
@Component
public class OpenTableReviewsCsvParser {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ZoneId zone;

  public OpenTableReviewsCsvParser(
      @Value("${opentable.timezone:Australia/Melbourne}") String zone) {
    this.zone = ZoneId.of(zone);
  }

  public List<OpenTableReviewDetail> parse(byte[] csv) {
    List<CSVRecord> records = readAll(stripBom(csv));
    if (records.size() < 2) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "OpenTable reviews CSV is empty");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Review ID", "Source", "Review Date");

    List<OpenTableReviewDetail> out = new ArrayList<>();
    int width = columns.size();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (r.size() != width) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "OpenTable reviews CSV row has wrong column count");
      }
      out.add(row(r, columns));
    }
    return out;
  }

  private OpenTableReviewDetail row(CSVRecord r, Map<String, Integer> c) {
    return new OpenTableReviewDetail(
        requireValue(c, r, "Review ID"),
        requireValue(c, r, "Source"),
        instant(requireValue(c, r, "Review Date"), "Review Date"),
        instant(get(c, r, "Visit Date"), "Visit Date"),
        blankToNull(get(c, r, "Guest Name")),
        blankToNull(get(c, r, "Guest ID")),
        integer(get(c, r, "Overall Rating"), "Overall Rating"),
        integer(get(c, r, "Food"), "Food"),
        integer(get(c, r, "Service"), "Service"),
        integer(get(c, r, "Ambience"), "Ambience"),
        integer(get(c, r, "Value"), "Value"),
        blankToNull(get(c, r, "Noise")),
        booleanValue(get(c, r, "Would Recommend"), "Would Recommend"),
        blankToNull(get(c, r, "Comment")),
        blankToNull(get(c, r, "Private Note from Guest")),
        commaList(get(c, r, "Tags Added by Diner")),
        commaList(get(c, r, "Keywords")));
  }

  private Instant instant(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(v, TIMESTAMP).atZone(zone).toInstant();
    } catch (RuntimeException e) {
      throw schemaMismatch("Bad timestamp in column '" + name + "'", e);
    }
  }

  private Integer integer(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    try {
      return Integer.valueOf(v);
    } catch (NumberFormatException e) {
      throw schemaMismatch("Bad integer in column '" + name + "'", e);
    }
  }

  /** Parses a {@code Yes}/{@code No} value; blank maps to {@code null}. */
  private Boolean booleanValue(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    return switch (v.toLowerCase(Locale.ROOT)) {
      case "yes" -> Boolean.TRUE;
      case "no" -> Boolean.FALSE;
      default -> throw schemaMismatch("Bad boolean in column '" + name + "'", null);
    };
  }

  /** Splits a comma-separated list; blank maps to an empty list. */
  private static List<String> commaList(String value) {
    String v = blankToNull(value);
    if (v == null) {
      return List.of();
    }
    return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static ConnectorFetchException schemaMismatch(String message, Throwable cause) {
    return cause == null
        ? new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message)
        : new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message, cause);
  }

  private static byte[] stripBom(byte[] csv) {
    if (csv.length >= 3
        && (csv[0] & 0xFF) == 0xEF
        && (csv[1] & 0xFF) == 0xBB
        && (csv[2] & 0xFF) == 0xBF) {
      return Arrays.copyOfRange(csv, 3, csv.length);
    }
    return csv;
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Malformed OpenTable reviews CSV", e);
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
            "CONNECTOR_SCHEMA_MISMATCH", "Missing column '" + name + "' in OpenTable reviews CSV");
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
          "CONNECTOR_SCHEMA_MISMATCH", "Missing value for '" + name + "' in OpenTable reviews CSV");
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
