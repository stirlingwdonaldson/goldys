package com.goldys.platform.semantic;

import com.goldys.platform.semantic.catalog.MetricId;
import java.time.LocalDate;
import java.util.List;

/** Read port for the resolution history of a metric over an inclusive date range. */
public interface ResolutionStateQuery {

  /** Resolution decisions for a metric between {@code from} and {@code to}, inclusive. */
  List<ResolutionState> states(MetricId metric, LocalDate from, LocalDate to);
}
