package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.DeletedSaleRow;
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
class DeletedSaleListIntegrationTest {

  private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 20);
  private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 21);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDeletedSaleIngest ingest;
  @Autowired CanonicalDeletedSaleQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_deleted_sale");
  }

  @Test
  void listsCurrentDeletedSalesNewestFirstWithSaleNumberFilter() {
    // SALE-1 deleted on DAY_1, later corrected to a higher total (superseded).
    ingest.record(deletedSale(DAY_1, "SALE-1", "80.00"));
    ingest.record(deletedSale(DAY_1, "SALE-1", "100.00"));
    // SALE-3 deleted the same DAY_1, so same-date ordering (ascending saleNumber) is pinned.
    ingest.record(deletedSale(DAY_1, "SALE-3", "25.00"));
    // SALE-2 deleted one day later.
    ingest.record(deletedSale(DAY_2, "SALE-2", "40.00"));

    DataPage<DeletedSaleRow> all = query.page(null, DAY_1, DAY_2, 0, 50);

    assertThat(all.total()).isEqualTo(3);
    assertThat(all.items()).hasSize(3);
    assertThat(all.items())
        .extracting(DeletedSaleRow::saleNumber)
        .containsExactly("SALE-2", "SALE-1", "SALE-3");

    DataPage<DeletedSaleRow> sale1 = query.page("SALE-1", DAY_1, DAY_2, 0, 50);
    assertThat(sale1.total()).isEqualTo(1);
    assertThat(sale1.items()).extracting(DeletedSaleRow::saleNumber).containsExactly("SALE-1");
    assertFullFieldSet(sale1.items().get(0));
  }

  private void assertFullFieldSet(DeletedSaleRow r) {
    assertThat(r.tradingDate()).isEqualTo(DAY_1);
    assertThat(r.saleNumber()).isEqualTo("SALE-1");
    assertThat(r.orderType()).isEqualTo("DINE_IN");
    assertThat(r.note()).isEqualTo("Duplicate");
    assertThat(r.totalIncTax()).isEqualByComparingTo("100.00");
    assertThat(r.totalExTax()).isEqualByComparingTo("90.91");
    assertThat(r.totalTax()).isEqualByComparingTo("9.09");
    assertThat(r.totalCost()).isEqualByComparingTo("30.00");
    assertThat(r.openedRegisterName()).isEqualTo("Main Bar");
    assertThat(r.deletedRegisterName()).isEqualTo("Front Bar");
    assertThat(r.staffName()).isEqualTo("Alice");
    assertThat(r.deletedByStaffName()).isEqualTo("Carol");
    assertThat(r.tableNumber()).isEqualTo("T12");
    assertThat(r.siteId()).isEqualTo("96181");
    assertThat(r.customerName()).isEqualTo("Bob");
  }

  private DeletedSaleInput deletedSale(LocalDate date, String sale, String totalIncTax) {
    return new DeletedSaleInput(
        "LIGHTSPEED",
        date,
        sale,
        "DINE_IN",
        "Duplicate",
        new BigDecimal(totalIncTax),
        new BigDecimal("90.91"),
        new BigDecimal("9.09"),
        new BigDecimal("30.00"),
        "REG-OPEN",
        "Main Bar",
        "REG-DEL",
        "Front Bar",
        "Alice",
        "STAFF-1",
        "Carol",
        "STAFF-2",
        "T12",
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
