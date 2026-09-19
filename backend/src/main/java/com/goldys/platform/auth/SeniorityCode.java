package com.goldys.platform.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A validated seniority code such as {@code STAFF}, {@code MANAGER}, or {@code OWNER}.
 *
 * <p>Normalized the same way as {@link DepartmentCode}. There is deliberately no ordinal comparison
 * between seniorities: broader access comes from explicit permission rows, never from {@code OWNER}
 * ranking above {@code STAFF} in code.
 */
public record SeniorityCode(String value) {
  private static final Pattern PATTERN = Pattern.compile("[A-Z][A-Z0-9_-]{0,99}");

  public SeniorityCode {
    value = require("seniority", value);
  }

  private static String require(String label, String raw) {
    if (raw == null) {
      throw new IllegalArgumentException(label + " must not be null");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (!PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid " + label + ": " + raw);
    }
    return normalized;
  }
}
