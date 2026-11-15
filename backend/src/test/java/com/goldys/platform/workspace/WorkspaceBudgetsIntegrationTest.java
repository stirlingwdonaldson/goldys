package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class WorkspaceBudgetsIntegrationTest {
  @Autowired WorkspaceBudgets budgets;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearBudget() {
    jdbc.update("truncate workspace_daily_budget");
  }

  @Test
  void midnightReservationUsesMelbourneDatesAndRollsBackBothDaysOnExhaustion() {
    Instant start = Instant.parse("2026-11-15T12:55:00Z");
    Instant deadline = start.plus(Duration.ofMinutes(15));
    budgets.reserve(start, deadline);
    assertThat(
            jdbc.queryForList(
                "select venue_day::text from workspace_daily_budget order by venue_day",
                String.class))
        .containsExactly("2026-11-15", "2026-11-16");
    jdbc.update("update workspace_daily_budget set reserved_aud=50 where venue_day='2026-11-16'");
    assertThatThrownBy(() -> budgets.reserve(start, deadline))
        .isInstanceOf(WorkspaceConflict.class);
    assertThat(
            jdbc.queryForObject(
                "select reserved_aud from workspace_daily_budget where venue_day='2026-11-15'",
                Integer.class))
        .isEqualTo(5);
  }

  @Test
  void daylightSavingChangeDoesNotCreateAnExtraDailyAllowance() {
    Instant start = Instant.parse("2026-10-03T15:55:00Z");
    budgets.reserve(start, start.plus(Duration.ofMinutes(15)));
    assertThat(
            jdbc.queryForList("select venue_day::text from workspace_daily_budget", String.class))
        .containsExactly("2026-10-04");
    assertThat(
            jdbc.queryForObject("select reserved_aud from workspace_daily_budget", Integer.class))
        .isEqualTo(5);
    assertThatThrownBy(() -> budgets.reserve(start, start.plus(Duration.ofMinutes(16))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void concurrentMidnightAdmissionCannotOverspendEitherDay() throws Exception {
    Instant start = Instant.parse("2026-11-15T12:55:00Z");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var attempts =
          java.util.stream.IntStream.range(0, 20)
              .mapToObj(
                  index ->
                      executor.submit(
                          () -> {
                            try {
                              budgets.reserve(start, start.plus(Duration.ofMinutes(15)));
                              return true;
                            } catch (WorkspaceConflict exhausted) {
                              return false;
                            }
                          }))
              .toList();
      int accepted = 0;
      for (var attempt : attempts) if (attempt.get()) accepted++;
      assertThat(accepted).isEqualTo(10);
    }
    assertThat(jdbc.queryForList("select reserved_aud from workspace_daily_budget", Integer.class))
        .containsExactlyInAnyOrder(50, 50);
  }
}
