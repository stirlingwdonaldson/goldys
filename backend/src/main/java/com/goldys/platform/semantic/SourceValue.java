package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A value contributed by one source system.
 *
 * @param sourceSystem the source that reported the value
 * @param value the reported value
 * @param recordedAt when the source recorded it
 */
public record SourceValue(String sourceSystem, BigDecimal value, Instant recordedAt) {}
