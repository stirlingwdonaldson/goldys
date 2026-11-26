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

  @Autowired com.goldys.platform.semantic.SaleItemListQuery sortedQuery;

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

  @Test
  void ordersTheEntireFilteredDatasetBeforeThreeRowPages() {
    for (int i = 0; i < 10; i++) {
      ingest.record(
          saleItem(
              DAY_1, "SORT-" + i, "SORT-LINE-" + i, "BEV", Integer.toString(i == 9 ? 999 : i / 2)));
    }
    for (String direction : java.util.List.of("asc", "desc")) {
      var sort = new com.goldys.platform.semantic.RecordSort("amount", direction);
      var all = new java.util.ArrayList<SaleItemRow>();
      for (int page = 0; page < 4; page++)
        all.addAll(sortedQuery.page("BEV", null, DAY_1, DAY_2, page, 3, sort).items());
      assertThat(all).hasSize(10);
      assertThat(all).extracting(SaleItemRow::receiptLineId).doesNotHaveDuplicates();
      var values = all.stream().map(SaleItemRow::amount).toList();
      assertThat(values)
          .isSortedAccordingTo(
              direction.equals("asc")
                  ? java.util.Comparator.naturalOrder()
                  : java.util.Comparator.reverseOrder());
      assertThat(values.get(direction.equals("asc") ? 0 : 9)).isEqualByComparingTo("0");
      assertThat(values.get(direction.equals("desc") ? 0 : 9)).isEqualByComparingTo("999");
      int page = 0;
      assertThat(sortedQuery.page("BEV", null, DAY_1, DAY_2, page, 3, sort).items())
          .isEqualTo(all.subList(0, 3));
    }
  }

  @Autowired com.goldys.platform.semantic.MerchandiseHistoryQuery history;

  @Test
  void merchandiseHistoryFindsOldLinesIndependentlyOfProductMetrics() {
    LocalDate old = LocalDate.of(2020, 11, 24);
    ingest.record(saleItem(old, "OLD", "OLD-1", "BEV", "90"));
    ingest.record(saleItem(old, "OLD", "OLD-1", "BEV", "100"));
    ingest.record(saleItem(DAY_1, "NEW", "NEW-1", "FOOD", "20"));
    var coverage =
        history.coverage().stream()
            .filter(c -> c.dataset().equals("Lightspeed merchandise lines"))
            .findFirst()
            .orElseThrow();
    assertThat(coverage.firstDate()).isEqualTo(old);
    assertThat(coverage.lastDate()).isEqualTo(DAY_1);
    assertThat(coverage.observedDays()).isEqualTo(2);
    assertThat(coverage.rows()).isEqualTo(2);
    var products = history.products(old, old, 20);
    assertThat(products).hasSize(1);
    assertThat(products.getFirst().name()).isEqualTo("Espresso");
    assertThat(products.getFirst().amount()).isEqualByComparingTo("100");
    assertThat(products.getFirst().quantity()).isEqualByComparingTo("2");
    assertThat(history.products(old.minusDays(1), old.minusDays(1), 20)).isEmpty();
    assertThat(history.products(old, DAY_1, 1)).hasSize(1);
  }

  @Test
  void emptyHistoryKeepsDateBoundsMissing() {
    var coverage =
        history.coverage().stream()
            .filter(c -> c.dataset().equals("Lightspeed merchandise lines"))
            .findFirst()
            .orElseThrow();
    assertThat(coverage.firstDate()).isNull();
    assertThat(coverage.lastDate()).isNull();
    assertThat(coverage.rows()).isZero();
  }

  @Autowired com.goldys.platform.reporting.ToolDispatcher dispatcher;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

  @Test
  void namedSearchFindsRoastOutsideTopHundredThroughTheAuthorizedReportingTool() throws Exception {
    LocalDate date = LocalDate.of(2025, 12, 7);
    for (int i = 0; i < 101; i++) {
      ingest.record(namedSaleItem(date, "SALE", "HIGH-" + i, "FOOD", "1000", "High seller " + i));
    }
    ingest.record(namedSaleItem(date, "SALE", "ROAST", "FOOD", "1", "Sunday Roast"));
    ingest.record(namedSaleItem(date, "SALE", "ROAST", "FOOD", "3", "Sunday Roast"));
    assertThat(history.products(date, date, 100))
        .extracting(p -> p.name())
        .doesNotContain("Sunday Roast");
    var input =
        json.readValue(
            """
        {"mode":"SEARCH","startDate":"2025-12-01","endDate":"2025-12-31",
         "productName":"ROAST","limit":20,"page":0}
        """,
            com.goldys.platform.reporting.GetMerchandiseHistoryInput.class);
    var owner =
        new com.goldys.platform.auth.UserRole(
            new com.goldys.platform.auth.DepartmentCode("ALL"),
            new com.goldys.platform.auth.SeniorityCode("OWNER"));
    var result =
        dispatcher.dispatch(
            com.goldys.platform.reporting.ToolId.GET_MERCHANDISE_HISTORY, input, owner);
    var rows = ((com.goldys.platform.widget.TableWidgetSpec) result.widget()).rows();
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst())
        .containsEntry("name", "Sunday Roast")
        .containsEntry("observedDays", 1L)
        .containsEntry("firstDate", date)
        .containsEntry("lastDate", date)
        .containsEntry("lines", 1L);
    assertThat((BigDecimal) rows.getFirst().get("quantity")).isEqualByComparingTo("2");
    assertThat((BigDecimal) rows.getFirst().get("amount")).isEqualByComparingTo("3");
    assertThat(String.join(" ", result.notices()))
        .contains(
            "Matching source product groups: 1",
            "hasMore: false",
            "does not make observed quantities unavailable");
  }

  @Test
  void parmaSearchKeepsVariantsAndModifiersSeparateAndPaginatesAllMatches() {
    LocalDate date = LocalDate.of(2025, 7, 25);
    ingest.record(namedSaleItem(date, "SALE", "CHICKEN", "FOOD", "30", "Chicken Parma"));
    ingest.record(namedSaleItem(date, "SALE", "EGGPLANT", "FOOD", "20", "Eggplant Parma"));
    ingest.record(namedSaleItem(date, "SALE", "MODIFIER", "Uncategorised", "0", "No Parma Sauce"));
    var first = history.products(date, date, "parma", 0, 2);
    assertThat(first.matchingGroups()).isEqualTo(3);
    assertThat(first.hasMore()).isTrue();
    assertThat(first.items())
        .extracting(p -> p.name())
        .containsExactly("Chicken Parma", "Eggplant Parma");
    var last = history.products(date, date, "parma", 1, 2);
    assertThat(last.matchingGroups()).isEqualTo(3);
    assertThat(last.hasMore()).isFalse();
    assertThat(last.items()).extracting(p -> p.name()).containsExactly("No Parma Sauce");
    var absent = history.products(date.minusDays(1), date.minusDays(1), "parma", 0, 20);
    assertThat(absent.items()).isEmpty();
    assertThat(absent.matchingGroups()).isZero();
    assertThat(history.products(date, date, "%' OR 1=1 --", 0, 20).items()).isEmpty();
    assertThat(history.products(date, date, "%", 0, 20).items()).isEmpty();
    assertThat(history.products(date, date, "_", 0, 20).items()).isEmpty();
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
    return namedSaleItem(date, sale, line, category, amount, "Espresso");
  }

  private SaleItemInput namedSaleItem(
      LocalDate date, String sale, String line, String category, String amount, String name) {
    return new SaleItemInput(
        "LIGHTSPEED",
        line,
        date,
        sale,
        name,
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
