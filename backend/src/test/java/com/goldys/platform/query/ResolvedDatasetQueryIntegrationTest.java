package com.goldys.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.AggregateFunction;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.FilterNode;
import com.goldys.platform.semantic.query.FilterOp;
import com.goldys.platform.semantic.query.GroupByItem;
import com.goldys.platform.semantic.query.OrderByItem;
import com.goldys.platform.semantic.query.Predicate;
import com.goldys.platform.semantic.query.QueryErrorCode;
import com.goldys.platform.semantic.query.QueryException;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.SelectItem;
import com.goldys.platform.semantic.query.SortDirection;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch A query coverage: the four resolved day/category projections registered in {@link
 * DatasetRegistry} are exercised end-to-end through the generic relational query engine — field
 * discovery, filtering, grouping, aggregation, sorting, pagination, invalid-field rejection and
 * permission enforcement — with each aggregate cross-checked against independent SQL over the
 * resolved projection.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedDatasetQueryIntegrationTest {

  private static final List<String> DATASETS =
      List.of(
          "resolved_product_sales",
          "resolved_payment_day",
          "resolved_deleted_sale_day",
          "resolved_sale_item_day");

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));

  @Autowired JdbcTemplate jdbc;
  @Autowired RelationalQueryService queries;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table resolved_product_sales, resolved_payment_day, resolved_deleted_sale_day, resolved_sale_item_day");
  }

  // ---- discovery + authorization ----

  @Test
  void datasetsAreDiscoverableByOwnerOnlyAndAddNoRelationships() {
    List<String> ids = queries.datasets(OWNER).stream().map(DatasetDescriptor::id).toList();
    assertThat(ids).containsAll(DATASETS);
    assertThat(queries.datasets(STAFF)).isEmpty();
    // No new join paths were invented for aggregate projections; the single invoice join remains.
    assertThat(queries.relationships(OWNER)).hasSize(1);
  }

  @Test
  void fieldDiscoveryExposesGrainWithoutTransactionIdentity() {
    assertThat(queries.fields(OWNER, "resolved_product_sales").stream().map(FieldDescriptor::name))
        .contains("trading_date", "product_name_key", "quantity_sold", "amount");
    // An aggregate day/category projection exposes no per-transaction identity, so a day/category
    // row can never be mistaken for an individual sale item or payment.
    assertThat(queries.fields(OWNER, "resolved_sale_item_day").stream().map(FieldDescriptor::name))
        .doesNotContain("sale_number", "id", "receipt_line_id");
    assertThat(queries.fields(OWNER, "resolved_payment_day").stream().map(FieldDescriptor::name))
        .doesNotContain("sale_number", "id");
  }

  // ---- independent-SQL cross-checks over the resolved projections ----

  @Test
  void productSalesGroupedTotalsMatchIndependentSql() {
    productSale("2026-10-01", "beer", "10", "80.00", "CTB");
    productSale("2026-10-01", "wine", "5", "150.00", "CTB");
    productSale("2026-10-02", "beer", "8", "64.00", "LIGHTSPEED");

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(sel("product_name_key"), sum("quantity_sold"), sum("amount")),
                null,
                List.of(by("product_name_key")),
                List.of(order("product_name_key", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT product_name_key, SUM(quantity_sold) AS qty, SUM(amount) AS amt "
                + "FROM resolved_product_sales GROUP BY product_name_key ORDER BY product_name_key");

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0)).isEqualTo("beer");
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("qty"));
    assertThat((BigDecimal) generic.rows().get(0).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("amt"));
    assertThat(generic.rows().get(1).get(0)).isEqualTo("wine");
    assertThat((BigDecimal) generic.rows().get(1).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(1).get("amt"));
  }

  @Test
  void paymentDayGroupedTotalsMatchIndependentSql() {
    payment("2026-10-01", "card", "500.00", "50.00", 20);
    payment("2026-10-01", "cash", "300.00", "20.00", 15);
    payment("2026-10-02", "card", "700.00", "70.00", 30);

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_payment_day",
                List.of(sel("payment_type_name"), sum("amount"), sum("tip"), sum("payment_count")),
                null,
                List.of(by("payment_type_name")),
                List.of(order("payment_type_name", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT payment_type_name, SUM(amount) AS amt, SUM(tip) AS tip, SUM(payment_count) AS cnt "
                + "FROM resolved_payment_day GROUP BY payment_type_name ORDER BY payment_type_name");

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0)).isEqualTo("card");
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("amt"));
    assertThat((BigDecimal) generic.rows().get(0).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("tip"));
    // SUM over a bigint count column returns numeric; compare numerically.
    assertThat((BigDecimal) generic.rows().get(0).get(3))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("cnt"));
  }

  @Test
  void deletedSaleDayTotalsMatchIndependentSql() {
    deletedSale("2026-10-01", 2, "44.00", "4.00");
    deletedSale("2026-10-02", 1, "22.00", "2.00");

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_deleted_sale_day",
                List.of(sum("deleted_count"), sum("total_inc_tax"), sum("total_tax")),
                null,
                List.of(),
                List.of(),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT SUM(deleted_count) AS cnt, SUM(total_inc_tax) AS inc, SUM(total_tax) AS tax "
                + "FROM resolved_deleted_sale_day");

    assertThat(generic.rows()).hasSize(1);
    assertThat((BigDecimal) generic.rows().get(0).get(0))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("cnt"));
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("inc"));
    assertThat((BigDecimal) generic.rows().get(0).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("tax"));
  }

  @Test
  void saleItemDayGroupedTotalsMatchIndependentSql() {
    saleItem("2026-10-01", "Food", "30", "900.00");
    saleItem("2026-10-01", "Drinks", "20", "400.00");
    saleItem("2026-10-02", "Food", "25", "750.00");

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_sale_item_day",
                List.of(sel("category_name"), sum("quantity"), sum("amount")),
                null,
                List.of(by("category_name")),
                List.of(order("category_name", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT category_name, SUM(quantity) AS qty, SUM(amount) AS amt "
                + "FROM resolved_sale_item_day GROUP BY category_name ORDER BY category_name");

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0)).isEqualTo("Drinks");
    assertThat((BigDecimal) generic.rows().get(0).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("amt"));
    assertThat(generic.rows().get(1).get(0)).isEqualTo("Food");
    assertThat((BigDecimal) generic.rows().get(1).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(1).get("qty"));
  }

  // ---- filtering, sorting, pagination, aggregation ----

  @Test
  void filteringByDimensionReturnsOnlyMatchingRows() {
    productSale("2026-10-01", "beer", "10", "80.00", "CTB");
    productSale("2026-10-01", "wine", "5", "150.00", "CTB");
    productSale("2026-10-02", "beer", "8", "64.00", "LIGHTSPEED");

    FilterNode filter =
        FilterNode.leaf(
            new Predicate(
                "product_name_key", FilterOp.EQ, mapper.getNodeFactory().textNode("beer")));

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(sel("trading_date"), sel("amount")),
                filter,
                List.of(),
                List.of(order("trading_date", SortDirection.ASC)),
                100,
                0));

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0).toString()).isEqualTo("2026-10-01");
    assertThat((BigDecimal) generic.rows().get(0).get(1)).isEqualByComparingTo("80.0000");
  }

  @Test
  void sortingAndPaginationRespectOrderLimitAndOffset() {
    saleItem("2026-10-01", "Food", "30", "900.00");
    saleItem("2026-10-01", "Drinks", "20", "400.00");
    saleItem("2026-10-02", "Food", "25", "750.00");

    QueryResult page =
        queries.run(
            OWNER,
            plan(
                "resolved_sale_item_day",
                List.of(sel("category_name"), sel("amount")),
                null,
                List.of(),
                List.of(order("amount", SortDirection.DESC)),
                1,
                1));

    assertThat(page.rows()).hasSize(1);
    assertThat(page.rows().get(0).get(0)).isEqualTo("Food");
    assertThat((BigDecimal) page.rows().get(0).get(1)).isEqualByComparingTo("750.0000");
  }

  @Test
  void aggregationSupportsCountAndAverage() {
    productSale("2026-10-01", "beer", "10", "80.00", "CTB");
    productSale("2026-10-01", "wine", "5", "150.00", "CTB");
    productSale("2026-10-02", "beer", "8", "64.00", "LIGHTSPEED");

    QueryResult count =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(count("product_name_key")),
                null,
                List.of(),
                List.of(),
                100,
                0));
    assertThat(count.rows().get(0).get(0)).isEqualTo(3L);

    QueryResult average =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(avg("amount")),
                null,
                List.of(),
                List.of(),
                100,
                0));
    assertThat(((BigDecimal) average.rows().get(0).get(0)).doubleValue())
        .isCloseTo(98.0, within(0.001));
  }

  @Test
  void unresolvedProductSalesAreSkippedInAggregationAndFindableByNullFilter() {
    productSale("2026-10-01", "beer", "10", "80.00", "CTB");
    // An unresolved source fact: quantity_sold / amount are NULL, never zero.
    unresolvedProductSale("2026-10-02", "wine");

    QueryResult total =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(sum("amount")),
                null,
                List.of(),
                List.of(),
                100,
                0));
    List<Map<String, Object>> reference =
        jdbc.queryForList("SELECT SUM(amount) AS a FROM resolved_product_sales");
    // SUM skips the unresolved NULL measure, matching independent SQL (80.00, not 80.00 + 0).
    assertThat((BigDecimal) total.rows().get(0).get(0))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("a"));

    FilterNode filter = FilterNode.leaf(new Predicate("quantity_sold", FilterOp.IS_NULL, null));
    QueryResult unresolved =
        queries.run(
            OWNER,
            plan(
                "resolved_product_sales",
                List.of(sel("product_name_key")),
                filter,
                List.of(),
                List.of(),
                100,
                0));
    assertThat(unresolved.rows()).hasSize(1);
    assertThat(unresolved.rows().get(0).get(0)).isEqualTo("wine");
  }

  // ---- negative guards ----

  @Test
  void groupingByMeasureIsRejected() {
    assertThatThrownBy(
            () ->
                queries.run(
                    OWNER,
                    plan(
                        "resolved_product_sales",
                        List.of(sum("amount")),
                        null,
                        List.of(by("amount")),
                        List.of(),
                        100,
                        0)))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.TYPE_MISMATCH);
  }

  @Test
  void unknownFieldIsRejectedForEachDataset() {
    for (String dataset : DATASETS) {
      assertThatThrownBy(
              () ->
                  queries.run(
                      OWNER,
                      plan(
                          dataset,
                          List.of(sel("no_such_field")),
                          null,
                          List.of(),
                          List.of(),
                          10,
                          0)))
          .isInstanceOf(QueryException.class)
          .hasFieldOrPropertyWithValue("code", QueryErrorCode.UNKNOWN_FIELD);
    }
  }

  @Test
  void staffIsDeniedForEachDataset() {
    for (String dataset : DATASETS) {
      assertThatThrownBy(
              () ->
                  queries.run(
                      STAFF,
                      plan(
                          dataset,
                          List.of(sel("trading_date")),
                          null,
                          List.of(),
                          List.of(),
                          10,
                          0)))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> queries.fields(STAFF, dataset))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  // ---- helpers ----

  private QueryPlan plan(
      String dataset,
      List<SelectItem> select,
      FilterNode filter,
      List<GroupByItem> groupBy,
      List<OrderByItem> orderBy,
      int limit,
      int offset) {
    return new QueryPlan(1, dataset, List.of(), select, filter, groupBy, orderBy, limit, offset);
  }

  private static SelectItem sel(String field) {
    return new SelectItem(field, null, false);
  }

  private static SelectItem sum(String field) {
    return new SelectItem(field, AggregateFunction.SUM, false);
  }

  private static SelectItem avg(String field) {
    return new SelectItem(field, AggregateFunction.AVG, false);
  }

  private static SelectItem count(String field) {
    return new SelectItem(field, AggregateFunction.COUNT, false);
  }

  private static GroupByItem by(String field) {
    return new GroupByItem(field, null);
  }

  private static OrderByItem order(String field, SortDirection direction) {
    return new OrderByItem(field, direction);
  }

  private void productSale(String date, String product, String qty, String amount, String source) {
    jdbc.update(
        "insert into resolved_product_sales (trading_date, product_name_key, quantity_sold, amount, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, 'rule', ?, false, now())",
        Date.valueOf(date),
        product,
        new BigDecimal(qty),
        new BigDecimal(amount),
        source);
  }

  private void unresolvedProductSale(String date, String product) {
    jdbc.update(
        "insert into resolved_product_sales (trading_date, product_name_key, quantity_sold, amount, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, NULL, NULL, 'unresolved', 'CTB', false, now())",
        Date.valueOf(date),
        product);
  }

  private void payment(String date, String type, String amount, String tip, long count) {
    jdbc.update(
        "insert into resolved_payment_day (trading_date, payment_type_name, amount, tip, payment_count, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, ?, 'single', ?, false, now())",
        Date.valueOf(date),
        type,
        new BigDecimal(amount),
        new BigDecimal(tip),
        count,
        "LIGHTSPEED");
  }

  private void deletedSale(String date, long count, String incTax, String tax) {
    jdbc.update(
        "insert into resolved_deleted_sale_day (trading_date, deleted_count, total_inc_tax, total_tax, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, 'single', ?, false, now())",
        Date.valueOf(date),
        count,
        new BigDecimal(incTax),
        new BigDecimal(tax),
        "LIGHTSPEED");
  }

  private void saleItem(String date, String category, String qty, String amount) {
    jdbc.update(
        "insert into resolved_sale_item_day (trading_date, category_name, quantity, amount, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, 'single', ?, false, now())",
        Date.valueOf(date),
        category,
        new BigDecimal(qty),
        new BigDecimal(amount),
        "LIGHTSPEED");
  }
}
