package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Operator-observed export selection, not a claim of source completeness. */
public record ReceiptImportScope(long siteNumber, String siteName, LocalDate from, LocalDate to) {
  public ReceiptImportScope {
    if (siteNumber <= 0
        || siteName == null
        || siteName.isBlank()
        || siteName.length() > 255
        || from == null
        || to == null
        || from.isAfter(to))
      throw new IllegalArgumentException(
          "A positive site, site name and ordered date window are required");
  }

  public void requireDate(LocalDate date) {
    if (date == null || date.isBefore(from) || date.isAfter(to))
      throw new IllegalArgumentException("An observation is outside the declared export window");
  }
}
