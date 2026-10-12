package com.goldys.platform.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The venue's single time zone and a clock fixed to it.
 *
 * <p>Business dates — "today", the CTB scheduled cron, reservation trading dates, dashboard
 * template ranges — must be evaluated in the venue zone, not UTC (the backend Docker image sets no
 * {@code TZ}). Inject this component and call {@link #today()} (or {@link #clock()} with {@code
 * LocalDate.now(clock)}) instead of the bare {@link LocalDate#now()}, which is UTC.
 *
 * <p>The zone is {@code app.venue.timezone}, defaulting to {@code Australia/Melbourne}; override it
 * (and {@code opentable.timezone}, which defaults to it) if the venue ever changes. The bean is
 * declared in {@link VenueClockConfig}.
 */
public final class VenueClock {
  private final ZoneId zone;
  private final Clock clock;

  private VenueClock(ZoneId zone, Clock clock) {
    this.zone = Objects.requireNonNull(zone, "zone");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** A clock fixed to a given zone's system clock, for tests that only need the zone. */
  public static VenueClock system(ZoneId zone) {
    return new VenueClock(zone, Clock.system(zone));
  }

  /** A clock pinned to a fixed instant in a zone, for deterministic tests. */
  public static VenueClock fixed(ZoneId zone, Instant instant) {
    return new VenueClock(zone, Clock.fixed(instant, zone));
  }

  public ZoneId zone() {
    return zone;
  }

  public Clock clock() {
    return clock;
  }

  /** The venue-local date at the current (or fixed) instant. */
  public LocalDate today() {
    return LocalDate.now(clock);
  }
}
