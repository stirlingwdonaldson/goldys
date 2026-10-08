package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.SourceValue;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end trust/provenance derivation over real Postgres (Testcontainers).
 *
 * <p>Each test seeds canonical daily sales in one resolution state and asserts that {@link
 * TrustService} — wired through the real {@code ResolutionStateQuery}, canonical query, rule and
 * override services — derives the expected {@link TrustState} and assembles the expected {@link
 * Provenance}. No derivation dependency is mocked: the only thing stubbed at the boundary is the
 * raw ingestion ledger row each canonical fact points at.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class TrustServiceIntegrationTest {

  private static final MetricId METRIC = MetricId.SALES_GROSS;
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final String LIGHTSPEED = "LIGHTSPEED";
  private static final String CTB = "CTB";

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired DailySalesOverrideService overrides;
  @Autowired ResolutionRuleService rules;
  @Autowired TrustService trust;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table canonical_daily_sales, daily_sales_override, resolution_rule, "
            + "resolved_daily_sales, reconciliation_exception");
  }

  @Test
  void agreeingSourcesDeriveVerifiedTrustAndAssembleProvenance() {
    LocalDate date = LocalDate.of(2026, 2, 1);
    UUID light = record(LIGHTSPEED, date, "100.00");
    UUID ctb = record(CTB, date, "100.00");

    TrustSummary summary = trust.trustFor(METRIC, range(date));

    assertThat(summary.state()).isEqualTo(TrustState.VERIFIED);
    assertThat(summary.authoritativeSource()).isEqualTo("agreed");

    Provenance provenance = trust.provenanceFor(METRIC, date);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("100.00");
    assertThat(provenance.sources())
        .extracting(SourceValue::sourceSystem)
        .containsExactlyInAnyOrder(LIGHTSPEED, CTB);
    assertThat(valueOf(provenance, LIGHTSPEED)).isEqualByComparingTo("100.00");
    assertThat(valueOf(provenance, CTB)).isEqualByComparingTo("100.00");
    assertThat(provenance.rawRecordIds()).containsExactlyInAnyOrder(light, ctb);
    assertThat(provenance.resolution().kind()).isEqualTo("agreed");
    assertThat(provenance.resolution().source()).isEqualTo("agreed");
    assertThat(provenance.resolution().reason()).isEqualTo("sources agree within tolerance");
    assertThat(provenance.trust().state()).isEqualTo(TrustState.VERIFIED);
  }

  @Test
  void singleSourceDerivesSingleSourceTrustAndResolvedValue() {
    LocalDate date = LocalDate.of(2026, 2, 2);
    UUID light = record(LIGHTSPEED, date, "100.00");

    TrustSummary summary = trust.trustFor(METRIC, range(date));

    assertThat(summary.state()).isEqualTo(TrustState.SINGLE_SOURCE);
    assertThat(summary.authoritativeSource()).isEqualTo(LIGHTSPEED);

    Provenance provenance = trust.provenanceFor(METRIC, date);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("100.00");
    assertThat(provenance.sources())
        .extracting(SourceValue::sourceSystem)
        .containsExactly(LIGHTSPEED);
    assertThat(provenance.rawRecordIds()).containsExactly(light);
    assertThat(provenance.resolution().kind()).isEqualTo("single");
    assertThat(provenance.resolution().reason())
        .isEqualTo("single source (no data from the other source)");
  }

  @Test
  void conflictingSourcesDeriveConflictedTrustAndNoResolvedValue() {
    LocalDate date = LocalDate.of(2026, 2, 3);
    UUID light = record(LIGHTSPEED, date, "100.00");
    UUID ctb = record(CTB, date, "200.00");

    TrustSummary summary = trust.trustFor(METRIC, range(date));

    assertThat(summary.state()).isEqualTo(TrustState.CONFLICTED);
    assertThat(summary.authoritativeSource()).isNull();

    Provenance provenance = trust.provenanceFor(METRIC, date);
    assertThat(provenance.resolvedValue()).isNull();
    assertThat(provenance.sources())
        .extracting(SourceValue::sourceSystem)
        .containsExactlyInAnyOrder(LIGHTSPEED, CTB);
    assertThat(valueOf(provenance, LIGHTSPEED)).isEqualByComparingTo("100.00");
    assertThat(valueOf(provenance, CTB)).isEqualByComparingTo("200.00");
    assertThat(provenance.rawRecordIds()).containsExactlyInAnyOrder(light, ctb);
    assertThat(provenance.resolution().kind()).isEqualTo("conflict");
    assertThat(provenance.resolution().reason()).isEqualTo("conflicting sources unresolved");
  }

  @Test
  void priorityRuleDerivesResolvedByRuleTrustAndRuleProvenance() {
    LocalDate date = LocalDate.of(2026, 2, 4);
    record(LIGHTSPEED, date, "100.00");
    record(CTB, date, "200.00");
    rules.save(
        OWNER,
        "bob@example.com",
        new ResolutionRuleService.RuleInput(
            "daily_sales", "daily_sales", "priority", null, List.of(LIGHTSPEED)));

    TrustSummary summary = trust.trustFor(METRIC, range(date));

    assertThat(summary.state()).isEqualTo(TrustState.RESOLVED_BY_RULE);
    assertThat(summary.authoritativeSource()).isEqualTo(LIGHTSPEED);

    Provenance provenance = trust.provenanceFor(METRIC, date);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("100.00");
    assertThat(provenance.resolution().kind()).isEqualTo("rule");
    assertThat(provenance.resolution().source()).isEqualTo(LIGHTSPEED);
    assertThat(provenance.resolution().reason()).isEqualTo("priority");
    assertThat(provenance.resolution().actor()).isEqualTo("bob@example.com");
  }

  @Test
  void manualOverrideDerivesManuallyOverriddenTrustAndOverrideProvenance() {
    LocalDate date = LocalDate.of(2026, 2, 5);
    record(LIGHTSPEED, date, "100.00");
    record(CTB, date, "200.00");
    overrides.save(
        OWNER, "alice@example.com", date, LIGHTSPEED, "CTB export missing late transactions");

    TrustSummary summary = trust.trustFor(METRIC, range(date));

    assertThat(summary.state()).isEqualTo(TrustState.MANUALLY_OVERRIDDEN);
    assertThat(summary.authoritativeSource()).isEqualTo(LIGHTSPEED);

    Provenance provenance = trust.provenanceFor(METRIC, date);
    assertThat(provenance.resolvedValue()).isEqualByComparingTo("100.00");
    assertThat(provenance.resolution().kind()).isEqualTo("override");
    assertThat(provenance.resolution().source()).isEqualTo(LIGHTSPEED);
    assertThat(provenance.resolution().reason()).isEqualTo("CTB export missing late transactions");
    assertThat(provenance.resolution().actor()).isEqualTo("alice@example.com");
  }

  private UUID record(String source, LocalDate date, String total) {
    UUID rawRecordId = rawRecord(source);
    ingest.record(
        new DailySalesInput(source, date, bd(total), bd("0.00"), bd("0.00"), rawRecordId));
    return rawRecordId;
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id) — insert real rows.
  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static TimeRange range(LocalDate date) {
    return new TimeRange(date, date, Calendar.CALENDAR);
  }

  private static BigDecimal valueOf(Provenance provenance, String source) {
    return provenance.sources().stream()
        .filter(s -> s.sourceSystem().equals(source))
        .map(SourceValue::value)
        .findFirst()
        .orElseThrow();
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
