package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

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
class ResolvedLabourQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedLabourDayRepository repository;
  @Autowired ResolvedLabourQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_labour_day");
  }

  @Test
  void hoursAndCostSumAcrossDepartments() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "FOH", "8.00", "7.50", "200.00"));
    repository.save(day(d, "BOH", "6.00", "6.00", "150.00"));

    assertThat(query.scheduledHours(d, d)).isEqualByComparingTo(new BigDecimal("14.00"));
    assertThat(query.actualHours(d, d)).isEqualByComparingTo(new BigDecimal("13.50"));
    assertThat(query.labourCost(d, d)).isEqualByComparingTo(new BigDecimal("350.00"));
  }

  @Test
  void labourCostIsNullWhenAnyDayCostIsUnknown() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "FOH", "8.00", "7.50", "200.00"));
    repository.save(day(d, "BOH", "6.00", "6.00", null));

    assertThat(query.labourCost(d, d)).isNull();
  }

  @Test
  void varianceIsScheduledMinusActual() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "FOH", "8.00", "7.50", "200.00"));

    assertThat(query.scheduledVsActualVariance(d, d)).isEqualByComparingTo(new BigDecimal("0.50"));
  }

  @Test
  void dailyLabourReturnsRowsOrderedByDepartment() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    repository.save(day(d, "BOH", "6.00", "6.00", "150.00"));
    repository.save(day(d, "FOH", "8.00", "7.50", "200.00"));

    assertThat(query.dailyLabour(d, d)).hasSize(2);
  }

  private static ResolvedLabourDay day(
      LocalDate date, String department, String scheduled, String actual, String cost) {
    return new ResolvedLabourDay(
        date,
        department,
        new BigDecimal(scheduled),
        new BigDecimal(actual),
        null,
        cost == null ? null : new BigDecimal(cost),
        "single",
        "DEPUTY",
        false,
        Instant.EPOCH);
  }
}
