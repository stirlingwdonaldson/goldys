package com.goldys.platform.reconciliation;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Classifies a reservation's service period from its time of day in the venue zone. Pure and
 * shared by the reservation projector; the lunch/dinner boundary is the caller's choice so the
 * cutoff stays configurable without touching this class.
 */
public final class ServicePeriod {
  public static final String LUNCH = "LUNCH";
  public static final String DINNER = "DINNER";

  private ServicePeriod() {}

  public static String classify(Instant reservationAt, ZoneId zone, LocalTime lunchCutoff) {
    LocalTime local = reservationAt.atZone(zone).toLocalTime();
    return local.isBefore(lunchCutoff) ? LUNCH : DINNER;
  }
}
