package com.goldys.platform.semantic;

import com.goldys.platform.semantic.catalog.MetricId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full provenance for one resolved metric value on one date: what the answer is, why it is trusted,
 * what each source said, how it was chosen, and which raw records back it.
 *
 * @param metric the metric being reported
 * @param date the period the value applies to
 * @param resolvedValue the value surfaced to operators
 * @param trust the trust and freshness assessment
 * @param sources every source value considered
 * @param resolution how and why the value was resolved
 * @param rawRecordIds the raw ingestion records underpinning the value
 */
public record Provenance(
    MetricId metric,
    LocalDate date,
    BigDecimal resolvedValue,
    TrustSummary trust,
    List<SourceValue> sources,
    ResolutionDetail resolution,
    List<UUID> rawRecordIds) {}
