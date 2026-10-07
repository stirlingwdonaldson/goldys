package com.goldys.platform.semantic.catalog;

/** Reusable period comparisons. BUDGET and FORECAST are declared but have no data source yet. */
public enum Comparison {
  PREVIOUS_DAY,
  PREVIOUS_WEEK,
  SAME_WEEKDAY_LAST_WEEK,
  SAME_PERIOD_LAST_YEAR,
  ROLLING_4_WEEKS,
  ROLLING_12_WEEKS,
  BUDGET,
  FORECAST
}
