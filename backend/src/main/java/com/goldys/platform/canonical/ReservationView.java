package com.goldys.platform.canonical;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A read-only view of one current reservation fact, for modules outside the {@code canonical}
 * package. {@code tradingDate} is the reservation's local date in the venue zone.
 */
public record ReservationView(
    LocalDate tradingDate,
    String sourceSystem,
    Instant reservationAt,
    int partySize,
    String status) {}
