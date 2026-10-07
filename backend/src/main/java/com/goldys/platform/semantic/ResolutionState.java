package com.goldys.platform.semantic;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A single resolution decision over one period.
 *
 * @param date the period the decision applies to
 * @param resolutionType how the value was resolved (e.g. rule or override)
 * @param authoritativeSource the source whose value won
 * @param resolvedAt when the decision was made
 */
public record ResolutionState(
    LocalDate date, String resolutionType, String authoritativeSource, Instant resolvedAt) {}
