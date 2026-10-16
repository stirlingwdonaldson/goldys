package com.goldys.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch B query coverage: the four resolved domain projections registered in {@link
 * DatasetRegistry} (reservations, recipes, recipe ingredients, inventory) exercised through the
 * generic relational query engine, with each aggregate cross-checked against independent SQL over
 * the resolved projection. No cross-domain joins exist for these datasets, so nothing here can
 * multiply amounts.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedDomainBatchBQueryIntegrationTest {

  private static final List<String> DATASETS =
      List.of(
          "resolved_reservation_day",
          "resolved_recipe",
          "resolved_recipe_ingredient",
          "resolved_inventory_day");

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
        "truncate table resolved_reservation_day, resolved_recipe, resolved_recipe_ingredient, resolved_inventory_day");
  }

  // ---- discovery + authorization ----

  @Test
  void datasetsAreDiscoverableByOwnerOnlyAndAddNoRelationships() {
    List<String> ids = queries.datasets(OWNER).stream().map(DatasetDescriptor::id).toList();
    assertThat(ids).containsAll(DATASETS);
    assertThat(queries.datasets(STAFF)).isEmpty();
    // No new join paths; the single invoice -> invoice_line relationship remains.
    assertThat(queries.relationships(OWNER)).hasSize(1);
  }

  @Test
  void relationshipsAreNotDisclosedToStaff() {
    assertThat(queries.relationships(STAFF)).isEmpty();
  }

  @Test
  void fieldDiscoveryExposesExpectedFields() {
    assertThat(
            queries.fields(OWNER, "resolved_reservation_day").stream().map(FieldDescriptor::name))
        .contains(
            "trading_date",
            "service_period",
            "bookings",
            "attended",
            "covers",
            "cancelled",
            "no_shows",
            "walk_ins");
    assertThat(queries.fields(OWNER, "resolved_recipe").stream().map(FieldDescriptor::name))
        .contains("recipe_id", "recipe_name", "cost_per_portion", "menu_price", "menu_state");
    assertThat(
            queries.fields(OWNER, "resolved_recipe_ingredient").stream().map(FieldDescriptor::name))
        .contains(
            "parent_recipe_id", "stock_code", "cost_per_qty", "cost_per_unit", "supplier_name");
    assertThat(queries.fields(OWNER, "resolved_inventory_day").stream().map(FieldDescriptor::name))
        .contains("trading_date", "purchases", "wastage", "stock_on_hand");
  }

  // ---- independent-SQL cross-checks ----

  @Test
  void reservationGroupedCountsMatchIndependentSql() {
    reservation("2026-10-01", "dinner", 10, 9, 36, 1, 0, 2);
    reservation("2026-10-01", "lunch", 5, 5, 20, 0, 0, 1);
    reservation("2026-10-02", "dinner", 12, 11, 44, 1, 0, 3);

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_reservation_day",
                List.of(sel("service_period"), sum("covers"), sum("bookings")),
                null,
                List.of(by("service_period")),
                List.of(order("service_period", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT service_period, SUM(covers) AS covers, SUM(bookings) AS bookings "
                + "FROM resolved_reservation_day GROUP BY service_period ORDER BY service_period");

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0)).isEqualTo("dinner");
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("covers"));
    assertThat((BigDecimal) generic.rows().get(0).get(2))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("bookings"));
    assertThat(generic.rows().get(1).get(0)).isEqualTo("lunch");
  }

  @Test
  void recipeCostAndUnresolvedNullsMatchIndependentSql() {
    recipe(1, "Steak", new BigDecimal("12.50"), new BigDecimal("28.00"), "ACTIVE", "MENU");
    // Recipe 2 is present but its cost has not resolved: null measures, not zero.
    recipe(2, "Salad", null, null, "ACTIVE", "MENU");

    QueryResult total =
        queries.run(
            OWNER,
            plan(
                "resolved_recipe",
                List.of(sum("cost_per_portion")),
                null,
                List.of(),
                List.of(),
                100,
                0));
    List<Map<String, Object>> reference =
        jdbc.queryForList("SELECT SUM(cost_per_portion) AS cpp FROM resolved_recipe");
    // SUM skips the unresolved NULL, so the total is 12.50, not 12.50 + 0.
    assertThat((BigDecimal) total.rows().get(0).get(0))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("cpp"));
  }

  @Test
  void ingredientGroupedCostMatchesIndependentSql() {
    ingredient(1, 101, "BEEF", new BigDecimal("10.00"), "Supplier A");
    ingredient(1, 102, "BUTTER", new BigDecimal("0.80"), "Supplier B");
    ingredient(2, 201, "LETTUCE", new BigDecimal("1.20"), "Supplier A");

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_recipe_ingredient",
                List.of(sel("parent_recipe_id"), sum("cost_per_qty"), count("id")),
                null,
                List.of(by("parent_recipe_id")),
                List.of(order("parent_recipe_id", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT parent_recipe_id, SUM(cost_per_qty) AS cq, COUNT(*) AS n "
                + "FROM resolved_recipe_ingredient GROUP BY parent_recipe_id ORDER BY parent_recipe_id");

    assertThat(generic.rows()).hasSize(2);
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("cq"));
    assertThat((Long) generic.rows().get(0).get(2)).isEqualTo((Long) reference.get(0).get("n"));
    assertThat((BigDecimal) generic.rows().get(1).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(1).get("cq"));
  }

  @Test
  void inventoryPurchasesMatchIndependentSql() {
    inventory("2026-10-01", new BigDecimal("500.00"), null, null);
    inventory("2026-10-02", new BigDecimal("600.00"), null, null);

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_inventory_day",
                List.of(sum("purchases")),
                null,
                List.of(),
                List.of(),
                100,
                0));
    List<Map<String, Object>> reference =
        jdbc.queryForList("SELECT SUM(purchases) AS p FROM resolved_inventory_day");

    assertThat((BigDecimal) generic.rows().get(0).get(0))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("p"));
  }

  // ---- null / missing-data semantics ----

  @Test
  void missingMeasureIsNullNotZero() {
    inventory("2026-10-01", new BigDecimal("500.00"), null, null);

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_inventory_day",
                List.of(sel("trading_date"), sel("wastage")),
                null,
                List.of(),
                List.of(),
                100,
                0));

    assertThat(generic.rows()).hasSize(1);
    // wastage has no source data: it must surface as NULL, never coerced to 0.
    assertThat(generic.rows().get(0).get(1)).isNull();
  }

  @Test
  void filterCanIsolateUnresolvedFactsByNullMeasure() {
    recipe(1, "Steak", new BigDecimal("12.50"), new BigDecimal("28.00"), "ACTIVE", "MENU");
    recipe(2, "Salad", null, null, "ACTIVE", "MENU");

    FilterNode filter = FilterNode.leaf(new Predicate("cost_per_portion", FilterOp.IS_NULL, null));

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_recipe",
                List.of(sel("recipe_id")),
                filter,
                List.of(),
                List.of(),
                100,
                0));

    assertThat(generic.rows()).hasSize(1);
    assertThat(generic.rows().get(0).get(0)).isEqualTo(2);
  }

  // ---- filtering, sorting, pagination ----

  @Test
  void filteringByDimensionReturnsOnlyMatchingRows() {
    reservation("2026-10-01", "dinner", 10, 9, 36, 1, 0, 2);
    reservation("2026-10-01", "lunch", 5, 5, 20, 0, 0, 1);

    FilterNode filter =
        FilterNode.leaf(
            new Predicate(
                "service_period", FilterOp.EQ, mapper.getNodeFactory().textNode("lunch")));

    QueryResult generic =
        queries.run(
            OWNER,
            plan(
                "resolved_reservation_day",
                List.of(sel("trading_date"), sel("covers")),
                filter,
                List.of(),
                List.of(),
                100,
                0));

    assertThat(generic.rows()).hasSize(1);
    assertThat(generic.rows().get(0).get(1)).isEqualTo(20L);
  }

  @Test
  void sortingAndPaginationRespectOrderLimitAndOffset() {
    recipe(1, "Steak", new BigDecimal("12.50"), new BigDecimal("28.00"), "ACTIVE", "MENU");
    recipe(2, "Salad", new BigDecimal("3.00"), new BigDecimal("14.00"), "ACTIVE", "MENU");
    recipe(3, "Chips", new BigDecimal("1.50"), new BigDecimal("8.00"), "ACTIVE", "MENU");

    QueryResult page =
        queries.run(
            OWNER,
            plan(
                "resolved_recipe",
                List.of(sel("recipe_name"), sel("cost_per_portion")),
                null,
                List.of(),
                List.of(order("cost_per_portion", SortDirection.DESC)),
                1,
                1));

    assertThat(page.rows()).hasSize(1);
    // Second-highest cost per portion is "Salad" (3.00), after "Steak" (12.50).
    assertThat(page.rows().get(0).get(0)).isEqualTo("Salad");
    assertThat((BigDecimal) page.rows().get(0).get(1)).isEqualByComparingTo("3.0000");
  }

  // ---- negative guards ----

  @Test
  void groupingByMeasureIsRejected() {
    assertThatThrownBy(
            () ->
                queries.run(
                    OWNER,
                    plan(
                        "resolved_inventory_day",
                        List.of(sum("purchases")),
                        null,
                        List.of(by("purchases")),
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
      String dimension = dimensionField(dataset);
      assertThatThrownBy(
              () ->
                  queries.run(
                      STAFF,
                      plan(dataset, List.of(sel(dimension)), null, List.of(), List.of(), 10, 0)))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> queries.fields(STAFF, dataset))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  // ---- helpers ----

  /** A representative dimension that exists on every dataset, so authorization is reached. */
  private static String dimensionField(String dataset) {
    return switch (dataset) {
      case "resolved_reservation_day", "resolved_inventory_day" -> "trading_date";
      case "resolved_recipe" -> "recipe_name";
      case "resolved_recipe_ingredient" -> "stock_code";
      default -> throw new IllegalArgumentException("unknown dataset " + dataset);
    };
  }

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

  private static SelectItem count(String field) {
    return new SelectItem(field, AggregateFunction.COUNT, false);
  }

  private static GroupByItem by(String field) {
    return new GroupByItem(field, null);
  }

  private static OrderByItem order(String field, SortDirection direction) {
    return new OrderByItem(field, direction);
  }

  private void reservation(
      String date,
      String period,
      long bookings,
      long attended,
      long covers,
      long cancelled,
      long noShows,
      long walkIns) {
    jdbc.update(
        "insert into resolved_reservation_day (trading_date, service_period, bookings, attended, "
            + "covers, cancelled, no_shows, walk_ins, resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, 'single', 'OPENTABLE', false, now())",
        Date.valueOf(date),
        period,
        bookings,
        attended,
        covers,
        cancelled,
        noShows,
        walkIns);
  }

  private void recipe(
      int id,
      String name,
      BigDecimal costPerPortion,
      BigDecimal menuPrice,
      String menuState,
      String priceSource) {
    jdbc.update(
        "insert into resolved_recipe (recipe_id, recipe_name, cost_per_portion, menu_price, "
            + "menu_state, price_source, resolution_type, authoritative_source, resolved_at) "
            + "values (?, ?, ?, ?, ?, ?, 'single', 'CTB', now())",
        id,
        name,
        costPerPortion,
        menuPrice,
        menuState,
        priceSource);
  }

  private void ingredient(
      int parentRecipeId,
      int recipeDetailId,
      String stockCode,
      BigDecimal costPerQty,
      String supplierName) {
    jdbc.update(
        "insert into resolved_recipe_ingredient (id, parent_recipe_id, recipe_detail_id, stock_code, "
            + "cost_per_qty, supplier_name, resolved_at) "
            + "values (?, ?, ?, ?, ?, ?, now())",
        UUID.randomUUID(),
        parentRecipeId,
        recipeDetailId,
        stockCode,
        costPerQty,
        supplierName);
  }

  private void inventory(
      String date, BigDecimal purchases, BigDecimal wastage, BigDecimal stockOnHand) {
    jdbc.update(
        "insert into resolved_inventory_day (trading_date, purchases, wastage, stock_on_hand, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, 'single', 'CTB', false, now())",
        Date.valueOf(date),
        purchases,
        wastage,
        stockOnHand);
  }
}
