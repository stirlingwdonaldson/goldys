package com.goldys.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Compile-and-shape test for the trust/provenance type surface. It references every type in the
 * {@code semantic} package so that a missing or misshaped type fails compilation; there is no
 * behaviour under test yet.
 */
class TrustTypeSurfaceTest {

  private static final Instant AT = Instant.parse("2026-10-08T09:00:00Z");
  private static final LocalDate DATE = LocalDate.of(2026, 10, 7);

  @Test
  void trustSummaryCarriesStateFreshnessAndThreshold() {
    TrustSummary trust =
        new TrustSummary(
            TrustState.VERIFIED,
            FreshnessState.FRESH,
            "lightspeed",
            AT,
            AT,
            Duration.ofMinutes(30));

    assertThat(trust.state()).isEqualTo(TrustState.VERIFIED);
    assertThat(trust.freshness()).isEqualTo(FreshnessState.FRESH);
    assertThat(trust.authoritativeSource()).isEqualTo("lightspeed");
    assertThat(trust.resolvedAt()).isEqualTo(AT);
    assertThat(trust.lastIngestionAt()).isEqualTo(AT);
    assertThat(trust.threshold()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void provenanceComposesSourceValuesResolutionAndTrust() {
    TrustSummary trust =
        new TrustSummary(
            TrustState.CONFLICTED, FreshnessState.STALE, null, AT, AT, Duration.ofHours(6));
    List<SourceValue> sources =
        List.of(
            new SourceValue("lightspeed", new BigDecimal("1234.50"), AT),
            new SourceValue("ctb", new BigDecimal("1200.00"), AT));
    ResolutionDetail resolution =
        new ResolutionDetail("OVERRIDE", "lightspeed", "manager reconciliation", "alice", AT);
    List<UUID> rawRecordIds = List.of(UUID.randomUUID());
    Provenance provenance =
        new Provenance(
            MetricId.SALES_GROSS,
            DATE,
            new BigDecimal("1234.50"),
            trust,
            sources,
            resolution,
            rawRecordIds);

    assertThat(provenance.metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(provenance.date()).isEqualTo(DATE);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("1234.50");
    assertThat(provenance.trust()).isEqualTo(trust);
    assertThat(provenance.sources()).hasSize(2);
    assertThat(provenance.resolution()).isEqualTo(resolution);
    assertThat(provenance.rawRecordIds()).hasSize(1);
  }

  @Test
  void resolutionStateCarriesItsCoordinates() {
    ResolutionState state = new ResolutionState(DATE, "RULE", "lightspeed", AT);

    assertThat(state.date()).isEqualTo(DATE);
    assertThat(state.resolutionType()).isEqualTo("RULE");
    assertThat(state.authoritativeSource()).isEqualTo("lightspeed");
    assertThat(state.resolvedAt()).isEqualTo(AT);
  }

  @Test
  void missingDataStatusExposesVenueFriendlyStates() {
    assertThat(MissingDataStatus.values())
        .containsExactly(
            MissingDataStatus.ZERO,
            MissingDataStatus.UNKNOWN,
            MissingDataStatus.NOT_RECEIVED,
            MissingDataStatus.UNRESOLVED,
            MissingDataStatus.NOT_APPLICABLE,
            MissingDataStatus.NOT_PERMITTED);
  }

  @Test
  void trustAndResolutionQueriesExposeTheirSignatures() {
    TrustQuery trustQuery =
        new TrustQuery() {
          @Override
          public TrustSummary trustFor(MetricId metric, TimeRange range) {
            return null;
          }

          @Override
          public Provenance provenanceFor(MetricId metric, LocalDate date) {
            return null;
          }
        };
    ResolutionStateQuery resolutionQuery =
        (metric, from, to) -> List.of(new ResolutionState(from, "RULE", "lightspeed", AT));

    TimeRange range = new TimeRange(DATE, DATE, Calendar.CALENDAR);

    assertThat(trustQuery.trustFor(MetricId.SALES_GROSS, range)).isNull();
    assertThat(trustQuery.provenanceFor(MetricId.SALES_GROSS, DATE)).isNull();
    assertThat(resolutionQuery.states(MetricId.SALES_GROSS, DATE, DATE)).hasSize(1);

    assertThat(TrustState.values())
        .contains(TrustState.INCOMPLETE, TrustState.NOT_RECEIVED, TrustState.MANUALLY_OVERRIDDEN);
    assertThat(FreshnessState.values())
        .contains(FreshnessState.SOURCE_FAILURE, FreshnessState.UNKNOWN);
  }
}
