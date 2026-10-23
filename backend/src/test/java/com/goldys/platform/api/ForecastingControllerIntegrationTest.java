package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AuthService;
import com.goldys.platform.forecasting.ForecastModelClient;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.support.PostgresContainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "forecasting.timesfm.enabled=true")
@AutoConfigureMockMvc
@Import({
  PostgresContainerConfiguration.class,
  ForecastingControllerIntegrationTest.NoopExecutor.class
})
class ForecastingControllerIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
  private static final LocalDate SEP_01 = LocalDate.of(2026, 9, 1);

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService authService;

  @MockitoBean ForecastModelClient client;
  @MockitoBean MetricQueryService queries;

  @BeforeEach
  void setUp() {
    jdbc.update("truncate table forecast_run");
    jdbc.update("truncate table user_account cascade");
    jdbc.update("delete from permission where department = 'ALL' and seniority = 'STAFF'");
    when(queries.query(any())).thenReturn(targetSeries());
  }

  @Test
  void metricsReturnsForecastableListWhenAuthorized() throws Exception {
    Login owner = loginOwner();

    mvc.perform(get("/api/forecasting/metrics").session(owner.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.metric == 'sales.net')].name").value("Net sales"))
        .andExpect(jsonPath("$[?(@.metric == 'sales.net')].unit").value("AUD"))
        .andExpect(
            jsonPath("$[?(@.metric == 'sales.net')].sourceDomain").value("resolved_daily_sales"))
        .andExpect(
            jsonPath("$[?(@.metric == 'sales.net')].requiredPermission")
                .value("reconciliation.sales"))
        .andExpect(jsonPath("$[?(@.metric == 'product.top_sellers')]").isEmpty());
  }

  @Test
  void metricsDeniedWithoutForecastingRead() throws Exception {
    authService.signup("staff@example.com", "Staff", "password123");
    Login staff = login("staff@example.com", "password123");

    mvc.perform(get("/api/forecasting/metrics").session(staff.session()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void createReturnsQueuedRunAndGetReturnsIt() throws Exception {
    Login owner = loginOwner();

    MvcResult created =
        mvc.perform(
                post("/api/forecasting/runs")
                    .session(owner.session())
                    .cookie(owner.xsrf())
                    .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"target\":\"sales.net\",\"covariates\":[],"
                            + "\"historyFrom\":\"2026-09-01\",\"horizonDays\":7}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.targetMetric").value("sales.net"))
            .andReturn();

    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    mvc.perform(get("/api/forecasting/runs/{id}", id).session(owner.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  void createDeniedWhenMetricPermissionMissing() throws Exception {
    authService.signup("staff@example.com", "Staff", "password123");
    jdbc.update(
        "insert into permission (id, department, seniority, resource, can_read, can_write) "
            + "values (gen_random_uuid(), 'ALL', 'STAFF', 'forecasting', true, true)");
    jdbc.update(
        "insert into permission (id, department, seniority, resource, can_read, can_write) "
            + "values (gen_random_uuid(), 'ALL', 'STAFF', 'reconciliation.sales', true, true)");
    Login staff = login("staff@example.com", "password123");

    // The target is readable but the covariate (reservations.covers) is not: an explicit 403.
    mvc.perform(
            post("/api/forecasting/runs")
                .session(staff.session())
                .cookie(staff.xsrf())
                .header("X-XSRF-TOKEN", staff.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"target\":\"sales.net\",\"covariates\":[\"reservations.covers\"],"
                        + "\"historyFrom\":\"2026-09-01\",\"horizonDays\":7}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  /** Creates an ALL/OWNER account (the seeded role with {@code forecasting} + metric grants). */
  private Login loginOwner() throws Exception {
    authService.signup("owner@example.com", "Owner", "password123");
    jdbc.update("update user_account set seniority = 'OWNER' where email = 'owner@example.com'");
    return login("owner@example.com", "password123");
  }

  private Login login(String email, String password) throws Exception {
    MockHttpSession session = new MockHttpSession();
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .session(session)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("email", email)
                    .param("password", password))
            .andExpect(status().isOk())
            .andReturn();
    Cookie xsrf = result.getResponse().getCookie("XSRF-TOKEN");
    return new Login(session, xsrf);
  }

  private static TimeSeriesResult targetSeries() {
    List<MetricPoint> points = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      points.add(new MetricPoint(SEP_01.plusDays(i), new BigDecimal(100 + i)));
    }
    return new TimeSeriesResult(
        MetricId.SALES_GROSS,
        List.of(new MetricSeries(null, points)),
        List.of(),
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(SEP_01, LocalDate.of(2026, 10, 11), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            NOW,
            List.of(),
            "1"));
  }

  private record Login(MockHttpSession session, Cookie xsrf) {}

  /** Discards the async dispatch so a submitted run stays {@code QUEUED} for the test. */
  @TestConfiguration(proxyBeanMethods = false)
  static class NoopExecutor {
    @Bean(name = "applicationTaskExecutor")
    TaskExecutor applicationTaskExecutor() {
      return task -> {};
    }
  }
}
