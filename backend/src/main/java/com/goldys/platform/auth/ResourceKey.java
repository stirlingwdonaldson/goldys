package com.goldys.platform.auth;

import java.util.regex.Pattern;

/**
 * A stable resource identifier such as {@code sales.amount} or {@code labor.wages}.
 *
 * <p>Constrained to a lower-case dotted alphabet so resources read predictably in permission rows
 * and error responses.
 */
public record ResourceKey(String value) {
  private static final Pattern PATTERN = Pattern.compile("[a-z][a-z0-9.-]{0,99}");

  public ResourceKey {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid resource key: " + value);
    }
  }
}
