package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ServicePeriodTest {

  private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
  private static final LocalTime CUTOFF = LocalTime.of(15, 0);

  @Test
  void beforeCutoffIsLunch() {
    // 2026-09-20T04:59:00Z is 14:59 AEST (UTC+10).
    assertThat(ServicePeriod.classify(Instant.parse("2026-09-20T04:59:00Z"), SYDNEY, CUTOFF))
        .isEqualTo(ServicePeriod.LUNCH);
  }

  @Test
  void atCutoffIsDinner() {
    // 2026-09-20T05:00:00Z is exactly 15:00 AEST; the cutoff itself is dinner (>= cutoff).
    assertThat(ServicePeriod.classify(Instant.parse("2026-09-20T05:00:00Z"), SYDNEY, CUTOFF))
        .isEqualTo(ServicePeriod.DINNER);
  }

  @Test
  void eveningIsDinner() {
    // 2026-09-20T13:00:00Z is 23:00 AEST.
    assertThat(ServicePeriod.classify(Instant.parse("2026-09-20T13:00:00Z"), SYDNEY, CUTOFF))
        .isEqualTo(ServicePeriod.DINNER);
  }
}
