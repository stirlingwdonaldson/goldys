package com.goldys.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class VenueClockTest {

  private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

  @Test
  void todayIsVenueDateAcrossMidnightUtc() {
    // 22:30 UTC on 2026-10-12 is 09:30 on 2026-10-13 in Melbourne (AEDT, UTC+11).
    VenueClock clock = VenueClock.fixed(MELBOURNE, Instant.parse("2026-10-12T22:30:00Z"));

    assertEquals(LocalDate.of(2026, 10, 13), clock.today());
  }

  @Test
  void todayIsStillThePreviousDayBeforeMidnightUtc() {
    // 12:00 UTC is 23:00 the same day in Melbourne (UTC+11).
    VenueClock clock = VenueClock.fixed(MELBOURNE, Instant.parse("2026-10-12T12:00:00Z"));

    assertEquals(LocalDate.of(2026, 10, 12), clock.today());
  }

  @Test
  void exposesTheZone() {
    VenueClock clock = VenueClock.fixed(MELBOURNE, Instant.parse("2026-10-12T22:30:00Z"));

    assertEquals(MELBOURNE, clock.zone());
    assertEquals(MELBOURNE, clock.clock().getZone());
  }
}
