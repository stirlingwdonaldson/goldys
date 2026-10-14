package com.goldys.platform.util;

import java.math.BigDecimal;

/** Null-coalescing helpers shared across connectors, canonical and reconciliation code. */
public final class Nulls {
  private Nulls() {}

  public static BigDecimal zeroIfNull(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  public static int zeroIfNull(Integer v) {
    return v == null ? 0 : v;
  }

  public static String valueIfBlank(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }
}
