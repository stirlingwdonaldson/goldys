package com.goldys.platform.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A validated department code such as {@code FOH} or {@code BOH}.
 *
 * <p>Values are normalized (trimmed, uppercased) and constrained to the upper-case code alphabet so
 * permission rows and profile rows can never disagree on spelling. Adding a department is data,
 * never a code change.
 */
public record DepartmentCode(String value) {
  private static final Pattern PATTERN = Pattern.compile("[A-Z][A-Z0-9_-]{0,99}");

  public DepartmentCode {
    value = require("department", value);
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
