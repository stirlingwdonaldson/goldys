package com.goldys.platform.connectors.ctb;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * The single authority for interpreting CTB's inconsistent value encodings.
 *
 * <p>CTB (ASP.NET MVC + ExtJS) serializes the same kind of fact several different ways across
 * endpoints, and a downstream canonical layer must not guess per field. This utility centralizes:
 *
 * <ul>
 *   <li><b>Dates</b> — Microsoft AJAX {@code /Date(millis)/}, .NET human strings ({@code "Tuesday,
 *       29 September 2026"}), and {@code dd/MM/yyyy} (with optional time). All dates are resolved
 *       to a venue-local {@link LocalDate} in {@code Australia/Melbourne}.
 *   <li><b>Empty string as null</b> — CTB uses {@code ""} for "no value" far more often than it
 *       emits a JSON {@code null}.
 *   <li><b>Sentinel ids</b> — CTB uses non-positive integers ({@code -1}, {@code 0}, {@code -4}) as
 *       "no reference"; real ids are positive.
 *   <li><b>Synthetic stock codes</b> — {@code PDF_<hex>} values are generated placeholders, not a
 *       real code.
 *   <li><b>Control characters</b> — some descriptions contain raw {@code \u0000}; PostgreSQL's
 *       {@code ::jsonb} rejects them, so they must be stripped before any canonical write.
 * </ul>
 */
public final class CtbValueNormalizer {
  private static final ZoneId VENUE_ZONE = ZoneId.of("Australia/Melbourne");

  private static final DateTimeFormatter DAY_MONTH_YEAR =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
  private static final DateTimeFormatter DMY =
      DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH);
  private static final DateTimeFormatter DMY_TIME =
      DateTimeFormatter.ofPattern("d/M/uuuu HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DMY_TIME_SEC =
      DateTimeFormatter.ofPattern("d/M/uuuu HH:mm:ss", Locale.ENGLISH);

  private static final String PDF_CODE = "(?i)PDF_[0-9a-f]+";

  private CtbValueNormalizer() {}

  /** Parse a CTB date string to a venue-local date, or {@code null} if unparseable/blank. */
  public static LocalDate parseDate(String raw) {
    String s = nullIfBlank(raw);
    if (s == null) {
      return null;
    }
    if (s.startsWith("/Date(") && s.endsWith(")/")) {
      return parseAjaxDate(s);
    }
    // "EEEE, d MMMM yyyy" — the weekday prefix is cosmetic and locale-specific, so drop it.
    int comma = s.indexOf(',');
    if (comma > 0) {
      LocalDate fromWeekday = parseLenient(DAY_MONTH_YEAR, s.substring(comma + 1).trim());
      if (fromWeekday != null) {
        return fromWeekday;
      }
    }
    LocalDate d = parseLenient(DMY_TIME, s);
    if (d != null) {
      return d;
    }
    d = parseLenient(DMY_TIME_SEC, s);
    if (d != null) {
      return d;
    }
    return parseLenient(DMY, s);
  }

  /** Decode an epoch-millis value to a venue-local date. */
  public static LocalDate parseEpochMillis(long millis) {
    return Instant.ofEpochMilli(millis).atZone(VENUE_ZONE).toLocalDate();
  }

  /** {@code ""} and whitespace-only are CTB's way of saying "no value". */
  public static String nullIfBlank(String s) {
    if (s == null) {
      return null;
    }
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** CTB uses non-positive integers as "no reference" across its foreign-key fields. */
  public static boolean isSentinelId(long value) {
    return value <= 0;
  }

  /** A {@code PDF_<hex>} stock code is a generated placeholder, not a real code. */
  public static String normalizeStockCode(String code) {
    String s = nullIfBlank(code);
    if (s == null) {
      return null;
    }
    return s.matches(PDF_CODE) ? null : s;
  }

  /** Strip raw control characters (notably {@code \u0000}) that break downstream JSON handling. */
  public static String clean(String s) {
    if (s == null) {
      return null;
    }
    return s.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
  }

  private static LocalDate parseAjaxDate(String s) {
    String inner = s.substring("/Date(".length(), s.length() - ")/".length());
    int sign = inner.indexOf('+');
    if (sign < 0) {
      sign = inner.indexOf('-', 1);
    }
    String millis = sign < 0 ? inner : inner.substring(0, sign);
    try {
      return parseEpochMillis(Long.parseLong(millis));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDate parseLenient(DateTimeFormatter formatter, String s) {
    try {
      return LocalDate.parse(s, formatter);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
