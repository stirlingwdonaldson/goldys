package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WeeklyReportTargetsTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final WeeklyReportTargets targets = new WeeklyReportTargets(jdbc, permissions);
  private final UserRole role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void deniesBeforeReadingOrWritingSettings() {
    doThrow(AccessDeniedException.forResource("weekly-report.targets"))
        .when(permissions)
        .require(role, new ResourceKey("weekly-report.targets"), PermissionAction.WRITE);
    assertThatThrownBy(() -> targets.save(role, "owner", null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void validatesDateFieldAndMoneyBeforeWriting() {
    LocalDate monday = LocalDate.parse("2026-10-26");
    for (var edit :
        java.util.List.of(
            new WeeklyReportTargets.Edit(
                monday.plusDays(1), Map.of("sales_target", BigDecimal.TEN)),
            new WeeklyReportTargets.Edit(monday, Map.of("unknown", BigDecimal.TEN)),
            new WeeklyReportTargets.Edit(monday, Map.of("food_target", BigDecimal.ONE.negate())),
            new WeeklyReportTargets.Edit(
                monday, Map.of("total_wastage_target_percent", BigDecimal.TEN)),
            new WeeklyReportTargets.Edit(
                monday,
                Map.of(
                    "food_variance_target_min",
                    new BigDecimal("0.03"),
                    "food_variance_target_max",
                    new BigDecimal("0.02"))))) {
      assertThatThrownBy(() -> targets.save(role, "owner", edit))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
