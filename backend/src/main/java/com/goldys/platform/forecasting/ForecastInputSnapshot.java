package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.MetricProvenance;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The exact checked model inputs, their definitions and qualifications at original issue time. */
public record ForecastInputSnapshot(
    ForecastModelRequest request,
    Map<String, MetricProvenance> provenance,
    List<String> notices,
    Instant capturedAt) {
  public ForecastInputSnapshot {
    provenance = Map.copyOf(provenance);
    notices = List.copyOf(notices);
  }
}
