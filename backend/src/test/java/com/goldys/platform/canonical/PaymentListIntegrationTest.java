package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.PaymentRow;
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
class PaymentListIntegrationTest {

  private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 20);
  private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 21);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalPaymentIngest ingest;
  @Autowired CanonicalPaymentQuery query;

  @Autowired com.goldys.platform.semantic.DataExplorerQuery explorer;

  @Test
  void explorerOrdersNativeNumbersAndKeepsMissingValuesLast() {
    for (int i = 0; i < 8; i++)
      ingest.record(payment(DAY_1, "EXP-" + i, "CASH", "Cash", Integer.toString(i == 7 ? 100 : i)));
    var sort = new com.goldys.platform.semantic.RecordSort("amount", "desc");
    var first = explorer.canonicalRows("payment", 0, 3, sort);
    assertThat(first.items().getFirst().columns().get("amount")).startsWith("100");
    var all = new java.util.ArrayList<String>();
    for (int page = 0; page < 3; page++)
      explorer.canonicalRows("payment", page, 3, sort).items().forEach(row -> all.add(row.id()));
    assertThat(all).hasSize(8).doesNotHaveDuplicates();
    jdbc.update("truncate table resolved_daily_sales");
    for (int i = 0; i < 8; i++)
      jdbc.update(
          "insert into resolved_daily_sales(trading_date,total_sales,net_sales,gst,resolution_type,has_conflict,resolved_at) values (?, ?, ?, 0, 'single_source', false, now())",
          DAY_1.plusDays(i),
          i == 7 ? null : i == 6 ? 100 : i,
          i);
    for (String direction : java.util.List.of("asc", "desc")) {
      var order = new com.goldys.platform.semantic.RecordSort("total_sales", direction);
      var rows = new java.util.ArrayList<com.goldys.platform.semantic.GenericRow>();
      for (int page = 0; page < 3; page++)
        rows.addAll(explorer.resolvedRows("resolved_daily_sales", page, 3, order).items());
      assertThat(rows).hasSize(8);
      assertThat(rows.getLast().columns()).doesNotContainKey("total_sales");
      assertThat(rows.getFirst().columns().get("total_sales"))
          .startsWith(direction.equals("asc") ? "0" : "100");
    }
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                explorer.canonicalRows(
                    "payment",
                    0,
                    3,
                    new com.goldys.platform.semantic.RecordSort(
                        "amount desc; drop table x", "asc")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_payment");
  }

  @Test
  void listsCurrentPaymentsNewestFirstAcrossFilterCombinations() {
    // A Tyro tender on SALE-1 that is later corrected to a higher amount (superseded).
    ingest.record(payment(DAY_1, "SALE-1", "TYRO", "Tyro", "90.00"));
    ingest.record(payment(DAY_1, "SALE-1", "TYRO", "Tyro", "100.00"));
    // A Cash tender on SALE-2, one day later.
    ingest.record(payment(DAY_2, "SALE-2", "CASH", "Cash", "40.00"));

    DataPage<PaymentRow> all = query.page(null, null, DAY_1, DAY_2, 0, 50);

    assertThat(all.total()).isEqualTo(2);
    assertThat(all.items()).hasSize(2);
    assertThat(all.items()).extracting(PaymentRow::saleNumber).containsExactly("SALE-2", "SALE-1");

    DataPage<PaymentRow> tyro = query.page("Tyro", null, DAY_1, DAY_2, 0, 50);
    assertThat(tyro.total()).isEqualTo(1);
    assertThat(tyro.items()).extracting(PaymentRow::saleNumber).containsExactly("SALE-1");

    DataPage<PaymentRow> sale1 = query.page(null, "SALE-1", DAY_1, DAY_2, 0, 50);
    assertThat(sale1.total()).isEqualTo(1);
    assertFullFieldSet(sale1.items().get(0));
  }

  @Test
  void ordersTheEntireFilteredDatasetBeforeThreeRowPages() {
    for (int i = 0; i < 10; i++) {
      ingest.record(
          payment(DAY_1, "SORT-" + i, "CASH", "Cash", Integer.toString(i == 9 ? 999 : i / 2)));
    }
    for (String direction : java.util.List.of("asc", "desc")) {
      var sort = new com.goldys.platform.semantic.RecordSort("amount", direction);
      var all = new java.util.ArrayList<PaymentRow>();
      for (int page = 0; page < 4; page++)
        all.addAll(query.page("Cash", null, DAY_1, DAY_2, page, 3, sort).items());
      assertThat(all).hasSize(10);
      assertThat(all).extracting(PaymentRow::saleNumber).doesNotHaveDuplicates();
      var values = all.stream().map(PaymentRow::amount).toList();
      assertThat(values)
          .isSortedAccordingTo(
              direction.equals("asc")
                  ? java.util.Comparator.naturalOrder()
                  : java.util.Comparator.reverseOrder());
      assertThat(values.get(direction.equals("asc") ? 0 : 9)).isEqualByComparingTo("0");
      assertThat(values.get(direction.equals("desc") ? 0 : 9)).isEqualByComparingTo("999");
      int page = 0;
      assertThat(query.page("Cash", null, DAY_1, DAY_2, page, 3, sort).items())
          .isEqualTo(all.subList(0, 3));
    }
  }

  private void assertFullFieldSet(PaymentRow r) {
    assertThat(r.tradingDate()).isEqualTo(DAY_1);
    assertThat(r.saleNumber()).isEqualTo("SALE-1");
    assertThat(r.paymentTypeName()).isEqualTo("Tyro");
    assertThat(r.paymentTypeCode()).isEqualTo("TYRO");
    assertThat(r.paymentSourceType()).isEqualTo("4");
    assertThat(r.lspayPaymentMode()).isEqualTo("EFTPOS");
    assertThat(r.clearingAccount()).isEqualTo("ACC-1");
    assertThat(r.amount()).isEqualByComparingTo("100.00");
    assertThat(r.tip()).isEqualByComparingTo("5.00");
    assertThat(r.tendered()).isEqualByComparingTo("105.00");
    assertThat(r.surcharge()).isEqualByComparingTo("0.50");
    assertThat(r.paymentCount()).isEqualTo(2);
    assertThat(r.tipCount()).isEqualTo(1);
    assertThat(r.reconciled()).isEqualTo("Reconciled");
    assertThat(r.registerCode()).isEqualTo("REG-1");
    assertThat(r.registerName()).isEqualTo("Bar 1");
    assertThat(r.staffName()).isEqualTo("Alice");
    assertThat(r.staffCode()).isEqualTo("STAFF-1");
    assertThat(r.siteId()).isEqualTo("96181");
    assertThat(r.customerName()).isEqualTo("Bob");
  }

  private PaymentInput payment(
      LocalDate date, String sale, String code, String name, String amount) {
    return new PaymentInput(
        "LIGHTSPEED",
        date,
        sale,
        code,
        name,
        "4",
        "EFTPOS",
        "ACC-1",
        new BigDecimal(amount),
        new BigDecimal("5.00"),
        new BigDecimal(amount).add(new BigDecimal("5.00")),
        new BigDecimal("0.50"),
        2,
        1,
        "Reconciled",
        "REG-1",
        "Bar 1",
        "Alice",
        "STAFF-1",
        "96181",
        "Bob",
        rawRecord());
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'LIGHTSPEED', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'LIGHTSPEED', 'FILE_EXPORT', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
