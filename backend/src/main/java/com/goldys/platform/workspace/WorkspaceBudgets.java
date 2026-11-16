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

  /** Caller holds the job's thread lock and has checked the live fence. */
  public void reserveRequest(
      java.util.UUID job, java.util.UUID request, String kind, java.math.BigDecimal aud) {
    var expected =
        switch (kind) {
          case "MODEL" -> new java.math.BigDecimal("0.65");
          case "SEARCH" -> new java.math.BigDecimal("0.002");
          default -> throw new IllegalArgumentException("Unknown charge kind");
        };
    if (aud == null || aud.compareTo(expected) != 0)
      throw new IllegalArgumentException("Unreviewed charge bound");
    var prior =
        jdbc.queryForList(
            "select kind,bound_aud from workspace_request_reservation where job_id=? and request_id=?",
            job,
            request);
    if (!prior.isEmpty()) {
      if (!kind.equals(prior.getFirst().get("kind"))
          || aud.compareTo((java.math.BigDecimal) prior.getFirst().get("bound_aud")) != 0)
        throw new WorkspaceConflict("Charge request already has different content");
      return;
    }
    var total =
        jdbc.queryForObject(
            "select coalesce(sum(bound_aud),0) from workspace_request_reservation where job_id=?",
            java.math.BigDecimal.class,
            job);
    if (total.add(aud).compareTo(new java.math.BigDecimal("5")) > 0)
      throw new WorkspaceConflict("Investigation charge limit reached");
    jdbc.update(
        "insert into workspace_request_reservation(job_id,request_id,kind,bound_aud) values (?,?,?,?)",
        job,
        request,
        kind,
        aud);
  }

  /**
   * Release unused admission capacity only after a completed, fully accounted attempt. Recorded
   * bounds still cover failed requests; actual invoice reconciliation remains external.
   */
  public void reconcileCompleted(java.util.UUID job) {
    var total =
        jdbc.queryForObject(
            "select sum(bound_aud) from workspace_request_reservation where job_id=?",
            java.math.BigDecimal.class,
            job);
    if (total == null) return; // Older workers have no request ledger: retain the full reservation.
    if (jdbc.update(
            "update workspace_job set budget_reconciled=true where id=? and state='COMPLETED' and not budget_reconciled",
            job)
        != 1) return;
    var deadline =
        jdbc.queryForObject(
                "select deadline from workspace_job where id=?", java.sql.Timestamp.class, job)
            .toInstant();
    var last = deadline.atZone(VENUE_ZONE).toLocalDate();
    for (var day = deadline.minus(Duration.ofMinutes(15)).atZone(VENUE_ZONE).toLocalDate();
        !day.isAfter(last);
        day = day.plusDays(1)) {
      if (jdbc.update(
              "update workspace_daily_budget set reserved_aud=reserved_aud-? where venue_day=?",
              new java.math.BigDecimal("5").subtract(total),
              day)
          != 1) throw new IllegalStateException("Missing daily admission reservation");
    }
  }
}
