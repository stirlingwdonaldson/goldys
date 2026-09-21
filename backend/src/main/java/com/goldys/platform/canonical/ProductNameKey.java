package com.goldys.platform.canonical;

import java.util.Locale;

/** The key a product is matched on: a normalized name with the "New " alias folded in. */
public final class ProductNameKey {
  private ProductNameKey() {}

  public static String normalize(String name) {
    if (name == null) return "";
    String lower = name.toLowerCase(Locale.ROOT);
    String alnum = lower.replaceAll("[^a-z0-9]+", " ").trim();
    String collapsed = alnum.replaceAll("\\s+", " ");
    return collapsed.startsWith("new ") ? collapsed.substring(4) : collapsed;
  }
}
