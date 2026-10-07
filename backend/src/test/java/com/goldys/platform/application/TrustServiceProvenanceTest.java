package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.reconciliation.ResolutionRuleView;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Provenance assembly for {@link TrustService}: override/rule detail surfaces reason and actor. */
class TrustServiceProvenanceTest {

  private static final MetricId METRIC = MetricId.SALES_GROSS;
  private static final String LIGHTSPEED = "LIGHTSPEED";
  private static final String CTB = "CTB";
  private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
  private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

  private final MetricCatalog catalog = new MetricCatalog();

  @Test
  void overrideResolutionCarriesReasonAndActor() {
    Instant overrideAt = Instant.parse("2026-09-01T11:30:00Z");
    TrustService service =
        service(
            new ResolutionState(DATE, "override", LIGHTSPEED, AT),
            Optional.of(
                new DailySalesOverrideService.OverrideDetail(
                    LIGHTSPEED,
                    "CTB export missing late transactions",
                    "alice@example.com",
                    overrideAt)),
            Optional.empty());

    Provenance provenance = service.provenanceFor(METRIC, DATE);

    assertThat(provenance.resolution().kind()).isEqualTo("override");
    assertThat(provenance.resolution().source()).isEqualTo(LIGHTSPEED);
    assertThat(provenance.resolution().reason()).isEqualTo("CTB export missing late transactions");
    assertThat(provenance.resolution().actor()).isEqualTo("alice@example.com");
    assertThat(provenance.resolution().at()).isEqualTo(overrideAt);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("27650.66");
  }

  @Test
  void ruleResolutionCarriesStrategyAndActor() {
    Instant ruleAt = Instant.parse("2026-09-01T09:00:00Z");
    TrustService service =
        service(
            new ResolutionState(DATE, "rule", LIGHTSPEED, AT),
            Optional.empty(),
            Optional.of(
                new ResolutionRuleView(
                    UUID.randomUUID(),
                    "daily_sales",
                    "daily_sales",
                    "priority",
                    List.of(LIGHTSPEED, CTB),
                    null,
                    ruleAt,
                    "bob@example.com")));

    Provenance provenance = service.provenanceFor(METRIC, DATE);

    assertThat(provenance.resolution().kind()).isEqualTo("rule");
    assertThat(provenance.resolution().source()).isEqualTo(LIGHTSPEED);
    assertThat(provenance.resolution().reason()).isEqualTo("priority");
    assertThat(provenance.resolution().actor()).isEqualTo("bob@example.com");
    assertThat(provenance.resolution().at()).isEqualTo(ruleAt);
  }

  private TrustService service(
      ResolutionState state,
      Optional<DailySalesOverrideService.OverrideDetail> override,
      Optional<ResolutionRuleView> rule) {
    ResolutionStateQuery resolution = mock(ResolutionStateQuery.class);
    when(resolution.states(METRIC, DATE, DATE)).thenReturn(List.of(state));

    ConnectorHealthQuery connectors = mock(ConnectorHealthQuery.class);
    when(connectors.health()).thenReturn(List.of(new ConnectorHealth(LIGHTSPEED, AT, "SUCCESS")));

    CanonicalDailySalesQuery dailySales = mock(CanonicalDailySalesQuery.class);
    when(dailySales.currentDailySalesForDate(DATE))
        .thenReturn(
            List.of(
                new DailySalesView(LIGHTSPEED, DATE, new BigDecimal("27650.66"), null, null, AT),
                new DailySalesView(CTB, DATE, new BigDecimal("20990.83"), null, null, AT)));
    when(dailySales.rawRecordIdsForDate(DATE)).thenReturn(List.of(UUID.randomUUID()));

    DailySalesOverrideService dailyOverrides = mock(DailySalesOverrideService.class);
    when(dailyOverrides.latestFor(DATE)).thenReturn(override);

    ResolutionRuleService rules = mock(ResolutionRuleService.class);
    when(rules.currentView("daily_sales", "daily_sales")).thenReturn(rule);

    FreshnessProperties freshness =
        new FreshnessProperties(
            Map.of("resolved_daily_sales", Duration.ofHours(2)),
            Map.of("resolved_daily_sales", List.of(LIGHTSPEED, CTB)));

    return new TrustService(
        resolution, connectors, freshness, catalog, dailySales, dailyOverrides, rules);
  }
}
