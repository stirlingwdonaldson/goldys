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
class ResolvedInventoryQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedInventoryDayRepository repository;
  @Autowired ResolvedInventoryQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_inventory_day");
  }

  @Test
  void purchasesSumsAcrossDays() {
    repository.save(day(LocalDate.of(2026, 9, 20), "70.00"));
    repository.save(day(LocalDate.of(2026, 9, 21), "30.00"));

    assertThat(query.purchases(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21)))
        .isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void wastageIsNullWhenNoData() {
    repository.save(day(LocalDate.of(2026, 9, 20), "70.00"));

    assertThat(query.wastage(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20))).isNull();
  }

  private static ResolvedInventoryDay day(LocalDate date, String purchases) {
    return new ResolvedInventoryDay(
        date, new BigDecimal(purchases), null, null, "single", "CTB", false, Instant.EPOCH);
  }
}
