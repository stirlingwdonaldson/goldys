package com.goldys.platform.semantic.catalog;

import java.time.LocalDate;

/** An inclusive date range plus the calendar semantics to apply. */
public record TimeRange(LocalDate from, LocalDate to, Calendar calendar) {
  public TimeRange {
    if (from == null || to == null || calendar == null) {
      throw new IllegalArgumentException("range requires from, to and calendar");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to is before from");
    }
  }
}
