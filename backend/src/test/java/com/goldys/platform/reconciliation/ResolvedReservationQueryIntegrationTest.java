package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.Period;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.semantic.ServicePeriodCovers;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
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
class ResolvedReservationQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedReservationDayRepository repository;
  @Autowired ResolvedReservationQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_reservation_day");
  }

  @Test
  void dailyCoversSumsAcrossServicePeriods() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "LUNCH", 30, 28, 120, 1, 1, 3));
    repository.save(day(d, "DINNER", 50, 48, 190, 2, 0, 6));

    assertThat(query.dailyCovers(d, d)).containsExactly(new CoversMetric(d, 310, "OPENTABLE", false));
  }

  @Test
  void summaryComputesDerivedRatios() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "LUNCH", 6, 4, 16, 1, 1, 0));
    repository.save(day(d, "DINNER", 4, 4, 16, 0, 0, 2));

    ReservationSummary summary = query.summary(d).orElseThrow();
    assertThat(summary.bookings()).isEqualTo(10);
    assertThat(summary.attended()).isEqualTo(8);
    assertThat(summary.covers()).isEqualTo(32);
    assertThat(summary.noShows()).isEqualTo(1);
    assertThat(summary.avgPartySize()).isEqualByComparingTo(new BigDecimal("4.0000"));
    assertThat(summary.noShowRate()).isEqualByComparingTo(new BigDecimal("0.1000"));
    assertThat(summary.bookingToCoverConversion()).isEqualByComparingTo(new BigDecimal("0.8000"));
  }

  @Test
  void zeroDenominatorsYieldNullOrEmpty() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "LUNCH", 0, 0, 0, 0, 0, 0));

    assertThat(query.noShowRate(d, d)).isEmpty();
    ReservationSummary summary = query.summary(d).orElseThrow();
    assertThat(summary.avgPartySize()).isNull();
    assertThat(summary.noShowRate()).isNull();
    assertThat(summary.bookingToCoverConversion()).isNull();
  }

  @Test
  void coversByServicePeriodReturnsOneRowPerPeriod() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "LUNCH", 30, 28, 120, 1, 1, 3));
    repository.save(day(d, "DINNER", 50, 48, 190, 2, 0, 6));

    assertThat(query.coversByServicePeriod(d, d))
        .containsExactly(
            new ServicePeriodCovers(d, "LUNCH", 120), new ServicePeriodCovers(d, "DINNER", 190));
  }

  @Test
  void comparePeriodsComputesDelta() {
    repository.save(day(LocalDate.of(2026, 9, 13), "LUNCH", 10, 10, 100, 0, 0, 0));
    repository.save(day(LocalDate.of(2026, 9, 14), "LUNCH", 11, 10, 120, 0, 1, 0));

    var comparison =
        query.comparePeriods(
            new Period(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13)),
            new Period(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)));

    assertThat(comparison.coversA()).isEqualTo(100);
    assertThat(comparison.coversB()).isEqualTo(120);
    assertThat(comparison.coversDeltaPercent()).isEqualByComparingTo(new BigDecimal("0.2000"));
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
