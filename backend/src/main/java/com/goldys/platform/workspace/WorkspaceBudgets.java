package com.goldys.platform.workspace;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conservative admission reservations; unknown provider charges are never refunded. */
@Service
public class WorkspaceBudgets {
  private static final ZoneId VENUE_ZONE = ZoneId.of("Australia/Melbourne");
  private final JdbcTemplate jdbc;

  public WorkspaceBudgets(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void reserve(Instant start, Instant deadline) {
    if (!deadline.isAfter(start)
        || Duration.between(start, deadline).compareTo(Duration.ofMinutes(15)) > 0)
      throw new IllegalArgumentException("Invalid investigation budget window");
    // Reserve in date order to serialize concurrent admission without opposite lock ordering.
    // shortcut: reserve the full ceiling on each touched day; replace only with verified
    // per-request accounting that includes unknown and in-flight provider charges.
    var lastDay = deadline.atZone(VENUE_ZONE).toLocalDate();
    for (var day = start.atZone(VENUE_ZONE).toLocalDate();
        !day.isAfter(lastDay);
        day = day.plusDays(1)) {
      jdbc.update(
          "insert into workspace_daily_budget(venue_day,reserved_aud) values (?,0) on conflict do nothing",
          day);
      if (jdbc.update(
              "update workspace_daily_budget set reserved_aud=reserved_aud+5 where venue_day=? and reserved_aud+5<=50",
              day)
          != 1) throw new WorkspaceConflict("Daily investigation budget exhausted");
    }
  }
}
