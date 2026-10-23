package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.SaleItemRow;
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
class SaleItemListIntegrationTest {

  private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 20);
  private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 21);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalSaleItemIngest ingest;
  @Autowired CanonicalSaleItemQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_sale_item");
  }

  @Test
  void listsCurrentSaleItemsNewestFirstAcrossFilterCombinations() {
    // A beverage line on SALE-1 that is later corrected to a higher amount (superseded).
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-1", "BEV", "90.00"));
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-1", "BEV", "100.00"));
    // A food line on the same DAY_1, so same-date ordering (ascending receipt-line id) is pinned.
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-3", "FOOD", "25.00"));
    // A beverage line on SALE-2, one day later.
    ingest.record(saleItem(DAY_2, "SALE-2", "LINE-2", "BEV", "40.00"));

    DataPage<SaleItemRow> all = query.page(null, null, DAY_1, DAY_2, 0, 50);

    assertThat(all.total()).isEqualTo(3);
    assertThat(all.items()).hasSize(3);
    assertThat(all.items())
        .extracting(SaleItemRow::receiptLineId)
        .containsExactly("LINE-2", "LINE-1", "LINE-3");

    DataPage<SaleItemRow> bev = query.page("BEV", null, DAY_1, DAY_2, 0, 50);
    assertThat(bev.total()).isEqualTo(2);
    assertThat(bev.items())
        .extracting(SaleItemRow::receiptLineId)
        .containsExactly("LINE-2", "LINE-1");

    DataPage<SaleItemRow> sale1 = query.page(null, "SALE-1", DAY_1, DAY_2, 0, 50);
    assertThat(sale1.total()).isEqualTo(2);
    assertThat(sale1.items())
        .extracting(SaleItemRow::receiptLineId)
        .containsExactly("LINE-1", "LINE-3");
    assertFullFieldSet(sale1.items().get(0));
  }

  @Test
  void currentSaleItemIdentitiesReturnsDistinctNameCategoryPairs() {
    // Two sale lines share the same item name but different categories, and the first is later
    // corrected (superseded). The identity list should collapse to the two distinct (name,
    // category) pairs with no superseded row or duplicate line.
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-1", "BEV", "90.00"));
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-1", "BEV", "100.00")); // supersedes LINE-1
    ingest.record(saleItem(DAY_1, "SALE-1", "LINE-3", "FOOD", "25.00"));

    assertThat(query.currentSaleItemIdentities())
        .containsExactlyInAnyOrder(
            new SaleItemIdentityView("Espresso", "BEV"),
            new SaleItemIdentityView("Espresso", "FOOD"));
  }

  @Test
  void adjustmentCorrectionSupersedesEvidenceAndUpdatesTheResolvedView() {
    var old = saleItem(DAY_1, "SALE-EVIDENCE", "EVIDENCE-1", "FOOD", "100.00");
    ingest.record(old);
    var adjustment =
        new com.goldys.platform.semantic.SaleAdjustment(
            new BigDecimal("90.91"),
            new BigDecimal("110"),
            new BigDecimal("100"),
            new BigDecimal("-10"),
            new BigDecimal("-9.09"),
            "Discount",
            "Manager",
            "Order Level",
            null);
    var next =
        new SaleItemInput(
            old.sourceSystem(),
            old.receiptLineId(),
            old.tradingDate(),
            old.saleNumber(),
            old.itemName(),
            old.productNumber(),
            old.sku(),
            old.categoryName(),
            old.quantitySold(),
            old.amount(),
            old.soldPriceIncTax(),
            old.totalTax(),
            old.costIncTax(),
            old.orderType(),
            old.saleType(),
            old.staffName(),
            old.registerName(),
            old.tableNumber(),
            rawRecord(),
            adjustment);
    ingest.record(next);
    ingest.record(next);
    assertThat(
            query.page(null, "SALE-EVIDENCE", DAY_1, DAY_1, 0, 50).items().getFirst().adjustment())
        .isEqualTo(adjustment);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_sale_item where source_record_ref='EVIDENCE-1'",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select adjustment->>'reason' from resolved_sale_line where receipt_line_id='EVIDENCE-1'",
                String.class))
        .isEqualTo("Manager");
  }

  private void assertFullFieldSet(SaleItemRow r) {
    assertThat(r.tradingDate()).isEqualTo(DAY_1);
    assertThat(r.saleNumber()).isEqualTo("SALE-1");
    assertThat(r.receiptLineId()).isEqualTo("LINE-1");
    assertThat(r.itemName()).isEqualTo("Espresso");
    assertThat(r.productNumber()).isEqualTo("PROD-1");
    assertThat(r.sku()).isEqualTo("SKU-1");
    assertThat(r.categoryName()).isEqualTo("BEV");
    assertThat(r.quantitySold()).isEqualByComparingTo("2");
    assertThat(r.amount()).isEqualByComparingTo("100.00");
    assertThat(r.soldPriceIncTax()).isEqualByComparingTo("50.00");
    assertThat(r.totalTax()).isEqualByComparingTo("4.55");
    assertThat(r.costIncTax()).isEqualByComparingTo("20.00");
    assertThat(r.orderType()).isEqualTo("DINE_IN");
    assertThat(r.saleType()).isEqualTo("SALE");
    assertThat(r.staffName()).isEqualTo("Alice");
    assertThat(r.registerName()).isEqualTo("Bar 1");
    assertThat(r.tableNumber()).isEqualTo("T12");
  }

  private SaleItemInput saleItem(
      LocalDate date, String sale, String line, String category, String amount) {
    return new SaleItemInput(
        "LIGHTSPEED",
        line,
        date,
        sale,
        "Espresso",
        "PROD-1",
        "SKU-1",
        category,
        new BigDecimal("2"),
        new BigDecimal(amount),
        new BigDecimal("50.00"),
        new BigDecimal("4.55"),
        new BigDecimal("20.00"),
        "DINE_IN",
        "SALE",
        "Alice",
        "Bar 1",
        "T12",
        rawRecord(),
        null);
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
