package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ReservationReadModelRepositoryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedReservationDayRepository resolved;
  @Autowired ReservationOverrideRepository overrides;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_reservation_day");
    jdbc.update("truncate table reservation_override");
  }

  @Test
  void resolvedRowsRoundTripByCompositeKey() {
    resolved.save(day(LocalDate.of(2026, 9, 13), "LUNCH", 30, 28, 120, 1, 1, 3));
    resolved.save(day(LocalDate.of(2026, 9, 13), "DINNER", 50, 48, 190, 2, 0, 6));

    assertThat(
            resolved.findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .hasSize(2);
    assertThat(resolved.countByHasConflictTrue()).isZero();
  }

  @Test
  void overrideRoundTripsAndFindsCurrent() {
    overrides.save(
        ReservationOverride.create(
            LocalDate.of(2026, 9, 13), "LUNCH", 125L, "manual correction", "owner@example.com",
            Instant.EPOCH));

    assertThat(overrides.findCurrent(LocalDate.of(2026, 9, 13), "LUNCH"))
        .get()
        .extracting(ReservationOverride::overriddenCovers)
        .isEqualTo(125L);
    assertThat(overrides.findCurrent(LocalDate.of(2026, 9, 13), "DINNER")).isEmpty();
  }

  private static ResolvedReservationDay day(
      LocalDate date,
      String period,
      long bookings,
      long attended,
      long covers,
      long cancelled,
      long noShows,
      long walkIns) {
    return new ResolvedReservationDay(
        date, period, bookings, attended, covers, cancelled, noShows, walkIns, "single", "OPENTABLE",
        false, Instant.EPOCH);
  }
}
