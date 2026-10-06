package com.goldys.platform.semantic;

import java.time.LocalDate;

/** An inclusive date range for period-comparison queries. */
public record Period(LocalDate from, LocalDate to) {
  public Period {
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to is before from");
    }
  }
}
