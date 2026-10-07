package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProjectionRepositoryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository resolved;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
  }

  @Test
  void resolvedRowsRoundTripAndConflictCountWorks() {
    resolved.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            new BigDecimal("27650.66"),
            null,
            null,
            "agreed",
            "agreed",
            false,
            Instant.EPOCH));
    resolved.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 14), null, null, null, "conflict", null, true, Instant.EPOCH));

    assertThat(resolved.findTopByOrderByTradingDateDesc().get().tradingDate())
        .isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(resolved.countByHasConflictTrue()).isEqualTo(1);
    assertThat(
            resolved.findByTradingDateBetweenOrderByTradingDateAsc(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .hasSize(2);
  }

  @Test
  void exceptionRowsRoundTripByIdClass() {
    exceptions.save(
        new ReconciliationExceptionRow(
            "daily_sales",
            "2026-09-13",
            "daily_sales",
            LocalDate.of(2026, 9, 13),
            "conflict",
            Instant.EPOCH));

    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).status()).isEqualTo("conflict");
    assertThat(rows.get(0).detectedAt()).isEqualTo(Instant.EPOCH);
  }
}
