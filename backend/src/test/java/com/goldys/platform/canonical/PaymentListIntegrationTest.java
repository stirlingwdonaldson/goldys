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
