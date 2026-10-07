package com.goldys.platform.semantic;

import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.LocalDate;

/** Read port for trust summaries and per-date provenance. */
public interface TrustQuery {

  /** Trust and freshness for a metric across a range. */
  TrustSummary trustFor(MetricId metric, TimeRange range);

  /** Full provenance for a metric's resolved value on a single date. */
  Provenance provenanceFor(MetricId metric, LocalDate date);
}
