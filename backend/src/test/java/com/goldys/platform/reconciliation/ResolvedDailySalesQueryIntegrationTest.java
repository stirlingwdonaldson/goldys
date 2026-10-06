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
class ResolvedDailySalesQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository repository;
  @Autowired ResolvedDailySalesQuery query;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table resolved_daily_sales");
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13), new BigDecimal("9694.80"), "agreed", "agreed", false,
            Instant.EPOCH));
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 14), null, "conflict", null, true, Instant.EPOCH));
  }

  @Test
  void latestReturnsTheMostRecentRow() {
    var latest = query.latest();
    assertThat(latest).isPresent();
    assertThat(latest.get().tradingDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(latest.get().totalSales()).isNull();
  }

  @Test
  void betweenReturnsRowsInRange() {
    var rows =
        query.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).tradingDate()).isEqualTo(LocalDate.of(2026, 9, 13));
  }

  @Test
  void countOpenConflictsCountsOnlyConflicts() {
    assertThat(query.countOpenConflicts()).isEqualTo(1);
  }
}
