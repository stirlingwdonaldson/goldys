package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved payments. Implementations read the resolved projection; consumers
 * (dashboards, reporting tools, exports) never derive these figures from canonical source rows.
 */
public interface PaymentMetricsQuery {

  /** Resolved payment mix for the inclusive range, ascending by date then payment type. */
  List<PaymentMix> dailyMix(LocalDate from, LocalDate to);
}
