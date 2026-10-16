package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalProductSalesIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesService service;
  @Autowired CanonicalProductSalesQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table product_sales_contributor, canonical_product_sales");
  }

  @Test
  void twoSourcesForTheSameProductAndDateShareTheLogicalId() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    var ls =
        service.record(
            new ProductSalesInput(
                "LIGHTSPEED",
                date,
                "pint carlton draught",
                bd("1232"),
                bd("17340.98"),
                null,
                rawRecord()));
    var ctb =
        service.record(
            new ProductSalesInput(
                "CTB",
                date,
                "pint carlton draught",
                bd("1232"),
                bd("17340.98"),
                null,
                rawRecord()));

    assertThat(ls.logicalEntityId()).isEqualTo(ctb.logicalEntityId());
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    ProductSalesInput input =
        new ProductSalesInput(
            "CTB", date, "chicken parma", bd("391"), bd("11418.06"), null, rawRecord());

    var first = service.record(input);
    var retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
  }

  @Test
  void sourceIdentitySurvivesRenameAndRetainsEveryContributingPage() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    UUID a = rawRecord(), b = rawRecord();
    var first =
        service.record(qualified(date, "old name", "A", 1, a, java.util.List.of(a, b), "10"));
    assertThat(query.tradingDatesForRawRecord(b)).containsExactly(date);
    UUID correction = rawRecord();
    var renamed =
        service.record(
            qualified(date, "new name", "A", 1, correction, java.util.List.of(correction), "11"));
    assertThat(renamed.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(query.currentProductSalesForDateAndProduct(date, "old name")).isEmpty();
    service.record(qualified(date, "old name", "A", 1, a, java.util.List.of(a, b), "10"));
    assertThat(query.currentProductSalesForDateAndProduct(date, "new name")).hasSize(1);
    assertThat(jdbc.queryForObject("select count(*) from canonical_product_sales", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void sameNamesWithDifferentCodesOrSitesRemainSeparateAndLegacyFactsAreNotMutated() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    service.record(
        new ProductSalesInput("CTB", date, "same", bd("99"), bd("99"), "A", rawRecord()));
    UUID a = rawRecord(), b = rawRecord(), c = rawRecord();
    service.recordAll(
        java.util.List.of(
            qualified(date, "same", "A", 1, a, java.util.List.of(a), "1"),
            qualified(date, "same", "B", 1, b, java.util.List.of(b), "2"),
            qualified(date, "same", "A", 2, c, java.util.List.of(c), "3")));
    assertThat(query.currentProductSalesForDateAndProduct(date, "same")).hasSize(3);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_product_sales where superseded_at is null",
                Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "select has_conflict from resolved_product_sales where product_name_key='same' and trading_date=?",
                Boolean.class,
                date))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select amount from resolved_product_sales where product_name_key='same' and trading_date=?",
                BigDecimal.class,
                date))
        .isNull();
  }

  @Test
  void identicalIntermediateReceiptCannotRestoreAnEarlierRevision() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    UUID a = rawRecord(), b = rawRecord(), c = rawRecord();
    service.record(qualified(date, "name", "A", 1, a, java.util.List.of(a), "10"));
    service.record(qualified(date, "name", "A", 1, b, java.util.List.of(b), "10"));
    service.record(qualified(date, "name", "A", 1, c, java.util.List.of(c), "12"));
    service.record(qualified(date, "name", "A", 1, b, java.util.List.of(b), "10"));
    assertThat(query.currentProductSalesForDate(date))
        .singleElement()
        .satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("12"));
    assertThat(jdbc.queryForObject("select count(*) from canonical_product_sales", Integer.class))
        .isEqualTo(2);
  }

  private ProductSalesInput qualified(
      LocalDate date,
      String name,
      String code,
      int site,
      UUID raw,
      java.util.List<UUID> pages,
      String amount) {
    return new ProductSalesInput(
        "CTB",
        date,
        name,
        bd("1"),
        bd(amount),
        code,
        raw,
        ProductSalesInput.sourceIdentity(code, site),
        pages);
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
