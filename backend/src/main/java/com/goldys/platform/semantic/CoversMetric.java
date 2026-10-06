package com.goldys.platform.semantic;

import java.time.LocalDate;

/** One date's resolved covers. */
public record CoversMetric(
    LocalDate date, long covers, String authoritativeSource, boolean hasConflict) {}
