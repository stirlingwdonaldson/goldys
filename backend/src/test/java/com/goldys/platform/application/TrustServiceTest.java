package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
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

/** Derivation matrix for {@link TrustService}: trust state and freshness from resolution rows. */
class TrustServiceTest {

  private static final MetricId METRIC = MetricId.SALES_GROSS;
  private static final String SOURCE = "pos";
  private static final String LIGHTSPEED = "LIGHTSPEED";
  private static final String CTB = "CTB";
  private static final List<String> SALES_SOURCES = List.of(LIGHTSPEED, CTB);
  private static final Duration THRESHOLD = Duration.ofHours(2);
  private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

  private final MetricCatalog catalog = new MetricCatalog();

  @Test
  void allDatesAgreedIsVerified() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    TrustService service = service(range, List.of(state(day, "agreed", SOURCE)), healthy(SOURCE));

    TrustSummary summary = service.trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.VERIFIED);
    assertThat(summary.authoritativeSource()).isEqualTo(SOURCE);
    assertThat(summary.resolvedAt()).isEqualTo(AT);
  }

  @Test
  void ruleIsResolvedByRule() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);

    TrustSummary summary =
        service(range, List.of(state(day, "rule", SOURCE)), healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.RESOLVED_BY_RULE);
  }

  @Test
  void overrideIsManuallyOverridden() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);

    TrustSummary summary =
        service(range, List.of(state(day, "override", SOURCE)), healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.MANUALLY_OVERRIDDEN);
  }

  @Test
  void singleSourceIsSingleSource() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);

    TrustSummary summary =
        service(range, List.of(state(day, "single", SOURCE)), healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.SINGLE_SOURCE);
  }

  @Test
  void conflictIsConflicted() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);

    TrustSummary summary =
        service(range, List.of(state(day, "conflict", SOURCE)), healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.CONFLICTED);
  }

  @Test
  void mixedResolvedAndMissingDatesIsIncomplete() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 3);
    TimeRange range = range(from, to);

    TrustSummary summary =
        service(
                range,
                List.of(state(from, "agreed", SOURCE), state(from.plusDays(1), "agreed", SOURCE)),
                healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.INCOMPLETE);
  }

  @Test
  void emptyIsNotReceived() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);

    TrustSummary summary = service(range, List.of(), healthy(SOURCE)).trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.NOT_RECEIVED);
    assertThat(summary.freshness()).isEqualTo(FreshnessState.UNKNOWN);
    assertThat(summary.authoritativeSource()).isNull();
    assertThat(summary.resolvedAt()).isNull();
    assertThat(summary.lastIngestionAt()).isNull();
  }

  @Test
  void failedConnectorIsSourceFailure() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    List<ConnectorHealth> health =
        List.of(new ConnectorHealth(LIGHTSPEED, Instant.now(), "FAILED"));

    TrustSummary summary =
        service(range, List.of(state(day, "agreed", SOURCE)), health).trustFor(METRIC, range);

    assertThat(summary.freshness()).isEqualTo(FreshnessState.SOURCE_FAILURE);
  }

  @Test
  void anyFailedDomainSourceIsSourceFailure() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    List<ConnectorHealth> health =
        List.of(
            new ConnectorHealth(LIGHTSPEED, Instant.now(), "SUCCESS"),
            new ConnectorHealth(CTB, Instant.now(), "FAILED"));

    TrustSummary summary =
        service(range, List.of(state(day, "agreed", SOURCE)), health).trustFor(METRIC, range);

    assertThat(summary.freshness()).isEqualTo(FreshnessState.SOURCE_FAILURE);
  }

  @Test
  void lastIngestionOlderThanThresholdIsStale() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    Instant staleAt = Instant.now().minus(THRESHOLD).minusSeconds(60);
    List<ConnectorHealth> health = List.of(new ConnectorHealth(LIGHTSPEED, staleAt, "SUCCESS"));

    TrustSummary summary =
        service(range, List.of(state(day, "agreed", SOURCE)), health).trustFor(METRIC, range);

    assertThat(summary.freshness()).isEqualTo(FreshnessState.STALE);
    assertThat(summary.lastIngestionAt()).isEqualTo(staleAt);
  }

  @Test
  void agreedResolutionDerivesFreshnessFromDomainSources() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    Instant lightspeedAt = Instant.now().minus(Duration.ofMinutes(7));
    List<ConnectorHealth> health =
        List.of(new ConnectorHealth(LIGHTSPEED, lightspeedAt, "SUCCESS"));

    TrustSummary summary =
        service(range, List.of(state(day, "agreed", SOURCE)), health).trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.VERIFIED);
    assertThat(summary.freshness()).isEqualTo(FreshnessState.FRESH);
    assertThat(summary.lastIngestionAt()).isEqualTo(lightspeedAt);
  }

  @Test
  void freshnessUsesFreshestDomainSource() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    Instant staleAt = Instant.now().minus(THRESHOLD).minusSeconds(60);
    Instant freshAt = Instant.now().minusSeconds(10);
    List<ConnectorHealth> health =
        List.of(
            new ConnectorHealth(LIGHTSPEED, staleAt, "SUCCESS"),
            new ConnectorHealth(CTB, freshAt, "SUCCESS"));

    TrustSummary summary =
        service(range, List.of(state(day, "agreed", SOURCE)), health).trustFor(METRIC, range);

    assertThat(summary.freshness()).isEqualTo(FreshnessState.FRESH);
    assertThat(summary.lastIngestionAt()).isEqualTo(freshAt);
  }

  @Test
  void dimensionedRowsOnSameDateUseLeastTrusted() {
    LocalDate day = LocalDate.of(2026, 9, 15);
    TimeRange range = range(day);

    TrustSummary summary =
        service(
                range,
                List.of(state(day, "agreed", SOURCE), state(day, "rule", SOURCE)),
                healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.RESOLVED_BY_RULE);
  }

  @Test
  void dimensionedRowsDoNotMaskMissingDates() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 2);
    TimeRange range = range(from, to);

    TrustSummary summary =
        service(
                range,
                List.of(state(from, "agreed", SOURCE), state(from, "agreed", SOURCE)),
                healthy(SOURCE))
            .trustFor(METRIC, range);

    assertThat(summary.state()).isEqualTo(TrustState.INCOMPLETE);
  }

  @Test
  void derivedMetricAggregatesConstituentTrustWithoutResolutionState() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    TimeRange range = range(day);
    ResolutionStateQuery resolution = mock(ResolutionStateQuery.class);
    when(resolution.states(MetricId.SALES_GROSS, range.from(), range.to()))
        .thenReturn(List.of(state(day, "agreed", SOURCE)));
    when(resolution.states(MetricId.RESERVATIONS_COVERS, range.from(), range.to()))
        .thenReturn(List.of(state(day, "conflict", SOURCE)));
    ConnectorHealthQuery connectors = mock(ConnectorHealthQuery.class);
    when(connectors.health()).thenReturn(healthy(SOURCE));
    TrustService service =
        new TrustService(
            resolution,
            connectors,
            new FreshnessProperties(Map.of(), Map.of()),
            catalog,
            mock(CanonicalDailySalesQuery.class),
            mock(DailySalesOverrideService.class),
            mock(ResolutionRuleService.class));

    TrustSummary summary = service.trustFor(MetricId.SALES_AVERAGE_SPEND_PER_COVER, range);

    assertThat(summary.state()).isEqualTo(TrustState.CONFLICTED);
    assertThat(summary).isNotNull();
  }

  private TrustService service(
      TimeRange range, List<ResolutionState> states, List<ConnectorHealth> health) {
    ResolutionStateQuery resolution = mock(ResolutionStateQuery.class);
    when(resolution.states(METRIC, range.from(), range.to())).thenReturn(states);
    ConnectorHealthQuery connectors = mock(ConnectorHealthQuery.class);
    when(connectors.health()).thenReturn(health);
    FreshnessProperties freshness =
        new FreshnessProperties(
            Map.of("resolved_daily_sales", THRESHOLD),
            Map.of("resolved_daily_sales", SALES_SOURCES));
    CanonicalDailySalesQuery dailySales = mock(CanonicalDailySalesQuery.class);
    DailySalesOverrideService dailyOverrides = mock(DailySalesOverrideService.class);
    ResolutionRuleService rules = mock(ResolutionRuleService.class);
    return new TrustService(
        resolution, connectors, freshness, catalog, dailySales, dailyOverrides, rules);
  }

  private static ResolutionState state(LocalDate date, String type, String source) {
    return new ResolutionState(date, type, source, AT);
  }

  private static List<ConnectorHealth> healthy(String source) {
    return List.of(new ConnectorHealth(source, Instant.now(), "SUCCESS"));
  }

  private static TimeRange range(LocalDate day) {
    return new TimeRange(day, day, Calendar.CALENDAR);
  }

  private static TimeRange range(LocalDate from, LocalDate to) {
    return new TimeRange(from, to, Calendar.CALENDAR);
  }
}
