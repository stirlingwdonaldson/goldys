package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One date's resolved reservation summary. The three ratios are derived (not persisted) and are
 * {@code null} when their denominator is zero or unknown.
 */
public record ReservationSummary(
    LocalDate date,
    long bookings,
    long attended,
    long covers,
    long cancelled,
    long noShows,
    long walkIns,
    BigDecimal avgPartySize,
    BigDecimal noShowRate,
    BigDecimal bookingToCoverConversion) {}
