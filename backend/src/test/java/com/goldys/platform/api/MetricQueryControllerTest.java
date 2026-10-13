package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MetricQueryController.class)
@Import({SecurityConfig.class, MetricCatalog.class})
class MetricQueryControllerTest {

  private static final String BODY =
      """
      {"metric":"sales.gross","range":{"from":"2026-10-01","to":"2026-10-07","calendar":"CALENDAR"},"grain":"DAY","dimensions":[],"comparison":null}
      """;

  @Autowired MockMvc mvc;

  @MockitoBean MetricQueryService queries;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void ownerQueryReturnsTheMetricResult() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(queries.query(any())).thenReturn(timeSeriesResult());

    mvc.perform(
            post("/api/metrics/query")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metric").value("sales.gross"))
        .andExpect(jsonPath("$.series[0].points[0].bucketStart").value("2026-10-01"))
        .andExpect(jsonPath("$.series[0].points[0].value").value(10865.72));

    verify(permissions)
        .require(any(), eq(new ResourceKey("reconciliation.sales")), eq(PermissionAction.READ));
  }

  @Test
  void deniedQueryReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(
            post("/api/metrics/query")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void bodyIsParsedAndDelegatedToTheSemanticService() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(queries.query(any())).thenReturn(timeSeriesResult());

    mvc.perform(
            post("/api/metrics/query")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk());

    ArgumentCaptor<MetricQuery> captor = ArgumentCaptor.forClass(MetricQuery.class);
    verify(queries).query(captor.capture());
    MetricQuery parsed = captor.getValue();
    assertThat(parsed.metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(parsed.grain()).isEqualTo(TimeGrain.DAY);
    assertThat(parsed.range().from()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(parsed.range().to()).isEqualTo(LocalDate.of(2026, 10, 7));
    assertThat(parsed.dimensions()).isEmpty();
    assertThat(parsed.comparison()).isNull();
  }

  @Test
  void missingRangeReturnsBadRequestNotServerError() throws Exception {
    mvc.perform(
            post("/api/metrics/query")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metric\":\"sales.gross\",\"grain\":\"DAY\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void malformedJsonReturnsBadRequest() throws Exception {
    mvc.perform(
            post("/api/metrics/query")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Malformed request body"));
  }

  private static MetricResult timeSeriesResult() {
    MetricProvenance provenance =
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            Instant.parse("2026-10-07T09:00:00Z"),
            List.of(),
            "1");
    return new TimeSeriesResult(
        MetricId.SALES_GROSS,
        List.of(
            new MetricSeries(
                null,
                List.of(new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("10865.72"))))),
        List.of(),
        provenance);
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
