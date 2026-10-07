package com.goldys.platform.semantic;

import java.time.Duration;
import java.time.Instant;

/**
 * Trust and freshness summary for a metric over a range.
 *
 * @param state how the value was resolved
 * @param freshness freshness of the underlying source data
 * @param authoritativeSource the source the resolved value was taken from, if any
 * @param resolvedAt when the resolution decision was made
 * @param lastIngestionAt when any source last delivered data
 * @param threshold the freshness window beyond which data is considered stale
 */
public record TrustSummary(
    TrustState state,
    FreshnessState freshness,
    String authoritativeSource,
    Instant resolvedAt,
    Instant lastIngestionAt,
    Duration threshold) {}
