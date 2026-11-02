package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.*;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LightspeedHeadlineSalesIntegrationTest {
  @Autowired LightspeedIngestService service;
  @Autowired CanonicalDailySalesIngest canonical;
  @Autowired IngestionService ingestion;
  @Autowired RawLedgerQuery ledger;
  @Autowired SalesMetricsQuery metrics;
  @Autowired JdbcTemplate jdbc;
  private static final Path FIX = Path.of("src/test/resources/fixtures/lightspeed");

  @BeforeEach
  void clean() {
    jdbc.update("truncate canonical_daily_sales,resolved_daily_sales,daily_sales_override");
  }

  @Test
  void sourceTotalsReachResolvedMetricsAndSupersedeWithoutDuplicates() throws Exception {
    UUID previous =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "headline-test-prior",
            FetchMethod.MANUAL,
            "text/plain",
            new byte[] {1},
            null,
            "headline-test-prior");
    canonical.record(
        new DailySalesInput(
            "LIGHTSPEED",
            LocalDate.parse("2026-10-28"),
            new BigDecimal("20024.58"),
            new BigDecimal("1810.97"),
            new BigDecimal("18213.61"),
            previous));
    byte[] body = envelope(Files.readString(FIX.resolve("daily-sales-closed-date.csv")));
    service.ingest(body);
    UUID raw = latestRaw();
    assertThat(ledger.payloadBytes(raw)).containsExactly(body);
    assertStages(raw, "PARSED:SUCCESS", "CANONICALIZED:SUCCESS");
    var days = metrics.dailySales(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"));
    assertThat(days).hasSize(7);
    assertThat(days.stream().map(d -> d.grossSales()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("156695.57");
    var oct28 =
        days.stream()
            .filter(d -> d.tradingDate().equals(LocalDate.parse("2026-10-28")))
            .findFirst()
            .orElseThrow();
    assertThat(oct28.grossSales()).isEqualByComparingTo("20086.04");
    assertThat(oct28.netSales()).isEqualByComparingTo("18269.42");
    assertThat(oct28.gst()).isEqualByComparingTo("1816.62");
    assertThat(days.stream().filter(d -> d.tradingDate().equals(LocalDate.parse("2026-10-31"))))
        .singleElement()
        .satisfies(d -> assertThat(d.grossSales()).isEqualByComparingTo("41873.52"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_daily_sales where superseded_at is not null",
                Long.class))
        .isEqualTo(1);
    service.ingest(body);
    assertThat(jdbc.queryForObject("select count(*) from canonical_daily_sales", Long.class))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_daily_sales where superseded_at is null",
                Long.class))
        .isEqualTo(7);
  }

  @Test
  void legacyReportCannotOverwriteCorrectedFacts() throws Exception {
    service.ingest(envelope(Files.readString(FIX.resolve("daily-sales-closed-date.csv"))));
    byte[] legacy = envelope(Files.readString(FIX.resolve("insights_sales_sample.csv")));
    assertThatThrownBy(() -> service.ingest(legacy))
        .hasFieldOrPropertyWithValue("failureType", "UNSAFE_DAILY_SALES_BASIS");
    UUID raw = latestRaw();
    assertThat(ledger.payloadBytes(raw)).containsExactly(legacy);
    assertStages(raw, "PARSED:FAILED");
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                raw))
        .isEqualTo("PARTIAL");
    assertThat(
            jdbc.queryForObject(
                "select sum(total_sales) from canonical_daily_sales where superseded_at is null",
                BigDecimal.class))
        .isEqualByComparingTo("156695.57");
    assertThat(jdbc.queryForObject("select count(*) from canonical_daily_sales", Long.class))
        .isEqualTo(7);
  }

  @Test
  void validatesEntireReportBeforeWritesAndDoesNotEraseOmittedDays() throws Exception {
    String csv = Files.readString(FIX.resolve("daily-sales-closed-date.csv"));
    assertThatThrownBy(() -> service.ingest(envelope(csv + "2026-11-02,96182,10,11,1,0\n")))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
    assertStages(latestRaw(), "PARSED:FAILED");
    assertThat(jdbc.queryForObject("select count(*) from canonical_daily_sales", Long.class))
        .isZero();
    service.ingest(envelope(csv));
    service.ingest(envelope(csv.lines().findFirst().orElseThrow() + "\n"));
    UUID raw = latestRaw();
    assertStages(raw, "PARSED:EMPTY", "CANONICALIZED:EMPTY");
    assertThat(jdbc.queryForObject("select count(*) from canonical_daily_sales", Long.class))
        .isEqualTo(7);
    assertThat(
            jdbc.queryForList(
                "select failure_type from ingestion_failure where ingestion_run_id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                raw))
        .containsExactly("DAILY_SALES_EMPTY_UNVERIFIED");
  }

  @Test
  void correctedSourceDoesNotBypassAnExistingCtbOverride() throws Exception {
    UUID raw =
        ingestion.ingestPush(
            "CTB",
            "headline-ctb",
            FetchMethod.MANUAL,
            "text/plain",
            new byte[] {2},
            null,
            "headline-ctb");
    canonical.record(
        new DailySalesInput(
            "CTB",
            LocalDate.parse("2026-10-28"),
            new BigDecimal("19000"),
            new BigDecimal("1700"),
            new BigDecimal("17300"),
            raw));
    jdbc.update(
        "insert into daily_sales_override(id,trading_date,authoritative_source,actor_email,recorded_at) values (?,?,'CTB','test@example.invalid',now())",
        UUID.randomUUID(),
        LocalDate.parse("2026-10-28"));
    service.ingest(envelope(Files.readString(FIX.resolve("daily-sales-closed-date.csv"))));
    var day =
        metrics.dailySales(LocalDate.parse("2026-10-28"), LocalDate.parse("2026-10-28")).getFirst();
    assertThat(day.authoritativeSource()).isEqualTo("CTB");
    assertThat(day.grossSales()).isEqualByComparingTo("19000");
    assertThat(
            jdbc.queryForObject(
                "select total_sales from canonical_daily_sales where source_system='LIGHTSPEED' and trading_date='2026-10-28' and superseded_at is null",
                BigDecimal.class))
        .isEqualByComparingTo("20086.04");
  }

  private byte[] envelope(String csv) throws Exception {
    return new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
  }

  private UUID latestRaw() {
    return jdbc.queryForObject(
        "select id from raw_record where fetcher_identity='lightspeed-insights' order by fetched_at desc limit 1",
        UUID.class);
  }

  private void assertStages(UUID raw, String... expected) {
    assertThat(
            jdbc.queryForList(
                "select stage || ':' || outcome from ingestion_stage where ingestion_run_id=(select ingestion_run_id from raw_record where id=?) order by occurred_at",
                String.class,
                raw))
        .containsExactly(expected);
  }
}
