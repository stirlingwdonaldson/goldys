package com.goldys.platform.canonical;

import java.time.LocalDate;

/**
 * Published after a canonical reservation fact is recorded, so the reservation projector can update
 * the affected trading date's resolved projection in the same transaction.
 */
public record ReservationRecorded(LocalDate tradingDate) {}
