package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.application.ConnectorApplicationService.ConnectorStatus;
import com.goldys.platform.application.ConnectorApplicationService.Failure;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Maps an untrusted metric to the connector(s) responsible, gated on {@code connectors} READ. */
class DataQualityServiceTest {

  private static final UserRole OPERATOR =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("FOH"), new SeniorityCode("STAFF"));
  private static final MetricId METRIC = MetricId.SALES_GROSS;
  private static final String DOMAIN = "resolved_daily_sales";
  private static final List<String> SOURCES = List.of("LIGHTSPEED", "CTB");
  private static final Duration THRESHOLD = Duration.ofHours(2);
  private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

  private final TrustQuery trust = mock(TrustQuery.class);
  private final ConnectorHealthQuery health = mock(ConnectorHealthQuery.class);
  private final ConnectorApplicationService connectors = mock(ConnectorApplicationService.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final FreshnessProperties freshness =
      new FreshnessProperties(Map.of(DOMAIN, THRESHOLD), Map.of(DOMAIN, SOURCES));
  private final DataQualityService service =
      new DataQualityService(trust, health, freshness, catalog, connectors);

  @Test
  void incompleteMetricMapsToItsFailedSource() {
    TimeRange range = range(DATE);
    when(trust.trustFor(METRIC, range))
        .thenReturn(
            new TrustSummary(
                TrustState.INCOMPLETE, FreshnessState.SOURCE_FAILURE, null, null, null, THRESHOLD));
    when(health.health())
        .thenReturn(
            List.of(
                new ConnectorHealth("LIGHTSPEED", Instant.now(), "SUCCESS"),
                new ConnectorHealth("CTB", Instant.now(), "FAILED")));
    when(connectors.connectors(OPERATOR))
        .thenReturn(
            List.of(
                new ConnectorStatus(
                    "LIGHTSPEED", "lightspeed-insights", null, "success", 0, null, false),
                new ConnectorStatus(
                    "CTB",
                    "ctb-revenue",
                    null,
                    "failed",
                    1,
                    new Failure("AUTH_FAILED", "OAuth rejected", null, null),
                    false)));

    var responsible = service.responsibleConnectors(OPERATOR, METRIC, range);

    assertThat(responsible).hasSize(1);
    assertThat(responsible.get(0).source()).isEqualTo("CTB");
    assertThat(responsible.get(0).failure().type()).isEqualTo("AUTH_FAILED");
  }

  @Test
  void staleSourceIsResponsible() {
    TimeRange range = range(DATE);
    when(trust.trustFor(METRIC, range))
        .thenReturn(
            new TrustSummary(
                TrustState.VERIFIED, FreshnessState.STALE, null, null, null, THRESHOLD));
    Instant staleAt = Instant.now().minus(THRESHOLD).minusSeconds(60);
    when(health.health()).thenReturn(List.of(new ConnectorHealth("CTB", staleAt, "SUCCESS")));
    when(connectors.connectors(OPERATOR))
        .thenReturn(
            List.of(
                new ConnectorStatus(
                    "CTB", "ctb-revenue", staleAt.toString(), "success", 0, null, false)));

    var responsible = service.responsibleConnectors(OPERATOR, METRIC, range);

    assertThat(responsible).extracting(ConnectorStatus::source).containsExactly("CTB");
  }

  @Test
  void healthyMetricReturnsNoResponsibleSource() {
    TimeRange range = range(DATE);
    when(trust.trustFor(METRIC, range))
        .thenReturn(
            new TrustSummary(
                TrustState.VERIFIED,
                FreshnessState.FRESH,
                "LIGHTSPEED",
                null,
                Instant.now(),
                THRESHOLD));
    when(connectors.connectors(OPERATOR)).thenReturn(List.of());

    var responsible = service.responsibleConnectors(OPERATOR, METRIC, range);

    assertThat(responsible).isEmpty();
  }

  @Test
  void nonOperatorIsDenied() {
    TimeRange range = range(DATE);
    when(connectors.connectors(STAFF)).thenThrow(AccessDeniedException.forResource("connectors"));

    assertThatThrownBy(() -> service.responsibleConnectors(STAFF, METRIC, range))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static TimeRange range(LocalDate day) {
    return new TimeRange(day, day, Calendar.CALENDAR);
  }
}
