package com.goldys.platform.semantic.catalog;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive date range plus the calendar semantics to apply. */
public record TimeRange(LocalDate from, LocalDate to, Calendar calendar) {
  public TimeRange {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(calendar, "calendar");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to is before from");
    }
  }
}
