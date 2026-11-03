package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Effective-week settings, independent of operational source facts. Edits retain an audit trail.
 */
@Service
public class WeeklyReportTargets {
  private final JdbcTemplate jdbc;
  private final PermissionService permissions;

  public WeeklyReportTargets(JdbcTemplate jdbc, PermissionService permissions) {
    this.jdbc = jdbc;
    this.permissions = permissions;
  }

  public record Target(String id, BigDecimal value, LocalDate effectiveFrom, String source) {}

  public record Edit(LocalDate effectiveFrom, Map<String, BigDecimal> values) {}

  public static final Set<String> IDS =
      Set.of(
          "food_target",
          "beverage_target",
          "sales_target",
          "beverage_variance_target_min",
          "beverage_variance_target_max",
          "food_variance_target_min",
          "food_variance_target_max",
          "beverage_wastage_target_percent",
          "food_wastage_target_percent",
          "total_wastage_target_percent",
          "food_cogs_target_percent",
          "beverage_cogs_target_percent");

  @Transactional(readOnly = true)
  public List<Target> get(UserRole role, LocalDate week) {
    permissions.require(role, new ResourceKey("weekly-report.targets"), PermissionAction.READ);
    validateWeek(week);
    return jdbc.query(
        """
        select distinct on (field_id) field_id, value, effective_from, source
        from weekly_report_target where effective_from <= ? order by field_id, effective_from desc, id desc
        """,
        (rs, n) ->
            new Target(
                rs.getString("field_id"),
                rs.getBigDecimal("value"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getString("source")),
        week);
  }

  @Transactional
  public List<Target> save(UserRole role, String actor, Edit edit) {
    permissions.require(role, new ResourceKey("weekly-report.targets"), PermissionAction.WRITE);
    if (edit == null) throw new IllegalArgumentException("Targets are required");
    validateWeek(edit.effectiveFrom());
    if (edit.values() == null
        || edit.values().isEmpty()
        || !IDS.containsAll(edit.values().keySet()))
      throw new IllegalArgumentException("Choose recognised target fields");
    for (var entry : edit.values().entrySet()) {
      BigDecimal value = entry.getValue();
      boolean money =
          Set.of("food_target", "beverage_target", "sales_target").contains(entry.getKey());
      if (value == null
          || value.scale() > 4
          || value.abs().compareTo(new BigDecimal(money ? "9999999999" : "1")) > 0
          || (value.signum() < 0 && !entry.getKey().endsWith("_min")))
        throw new IllegalArgumentException("Invalid target value for " + entry.getKey());
    }
    Map<String, BigDecimal> merged = new HashMap<>();
    for (Target target : get(role, edit.effectiveFrom())) merged.put(target.id(), target.value());
    merged.putAll(edit.values());
    for (String prefix : List.of("food_variance_target", "beverage_variance_target")) {
      BigDecimal min = merged.get(prefix + "_min"), max = merged.get(prefix + "_max");
      if (min != null && max != null && min.compareTo(max) > 0)
        throw new IllegalArgumentException("Minimum target must not exceed maximum");
    }
    for (var entry : edit.values().entrySet())
      jdbc.update(
          "insert into weekly_report_target (field_id, effective_from, value, source, actor) values (?, ?, ?, ?, ?)",
          entry.getKey(),
          edit.effectiveFrom(),
          entry.getValue(),
          "Edited in weekly report",
          actor);
    return get(role, edit.effectiveFrom());
  }

  private static void validateWeek(LocalDate week) {
    if (week == null
        || week.getDayOfWeek() != DayOfWeek.MONDAY
        || week.getYear() < 2000
        || week.getYear() > 2100)
      throw new IllegalArgumentException("Select a Monday between 2000 and 2100");
  }
}
