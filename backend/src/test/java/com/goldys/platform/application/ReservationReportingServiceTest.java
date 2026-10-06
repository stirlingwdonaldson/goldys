package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReservationReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);

  @Test
  void summaryRequiresReadPermission() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("reservations.metrics"))
        .when(permissions)
        .require(any(), any(), any());
    ReservationReportingService service =
        new ReservationReportingService(mock(ReservationMetricsQuery.class), permissions);

    assertThatThrownBy(() -> service.summary(OWNER, SEP_20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summaryReturnsTheResolvedSummary() {
    ReservationMetricsQuery metrics = mock(ReservationMetricsQuery.class);
    ReservationSummary expected =
        new ReservationSummary(SEP_20, 10, 8, 32, 1, 1, 2, new BigDecimal("4.0000"), null, null);
    when(metrics.summary(SEP_20)).thenReturn(Optional.of(expected));
    PermissionService permissions = mock(PermissionService.class);
    ReservationReportingService service = new ReservationReportingService(metrics, permissions);

    assertThat(service.summary(OWNER, SEP_20)).contains(expected);
    verify(permissions)
        .require(OWNER, new ResourceKey("reservations.metrics"), PermissionAction.READ);
  }

  @Test
  void dailyCoversReturnsTheResolvedList() {
    ReservationMetricsQuery metrics = mock(ReservationMetricsQuery.class);
    when(metrics.dailyCovers(SEP_20, SEP_20))
        .thenReturn(List.of(new CoversMetric(SEP_20, 310, "OPENTABLE", false)));
    PermissionService permissions = mock(PermissionService.class);
    ReservationReportingService service = new ReservationReportingService(metrics, permissions);

    assertThat(service.dailyCovers(OWNER, SEP_20, SEP_20)).hasSize(1);
    verify(permissions)
        .require(OWNER, new ResourceKey("reservations.metrics"), PermissionAction.READ);
  }
}
