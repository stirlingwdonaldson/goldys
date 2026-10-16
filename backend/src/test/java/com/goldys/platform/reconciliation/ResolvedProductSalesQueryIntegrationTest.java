package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.application.DashboardApplicationService;
import com.goldys.platform.application.SavedDashboardApplicationService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.reporting.GetTopProductsInput;
import com.goldys.platform.reporting.GetTopProductsTool;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricRankedItem;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedProductSalesQueryIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedProductSalesRepository repository;
  @Autowired ProductMetricsQuery query;
  @Autowired DashboardApplicationService dashboard;
  @Autowired MetricQueryService metrics;
  @Autowired GetTopProductsTool topProducts;
  @Autowired SavedDashboardApplicationService savedDashboards;
  @Autowired SavedDashboardRepository savedRepository;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table resolved_product_sales");
    repository.save(row(SEP_13, "chips", "10", "50.00", "agreed", "agreed", false));
    repository.save(row(SEP_13, "garlic aioli", "150", "380.88", "agreed", "agreed", false));
    repository.save(row(SEP_13, "parma", null, null, "conflict", null, true));
    repository.save(row(SEP_14, "chips", "20", "100.00", "agreed", "agreed", false));
    repository.save(row(SEP_14, "garlic aioli", null, null, "conflict", null, true));
  }

  @Test
  void productSalesReturnsTheResolvedRow() {
    var chips = query.productSales(SEP_14, "chips");
    assertThat(chips).isPresent();
    assertThat(chips.get().amount()).isEqualByComparingTo("100.00");
    assertThat(chips.get().authoritativeSource()).isEqualTo("agreed");
  }

  @Test
  void productSalesReflectsUnresolvedConflict() {
    var garlic = query.productSales(SEP_14, "garlic aioli");
    assertThat(garlic).isPresent();
    assertThat(garlic.get().hasConflict()).isTrue();
    assertThat(garlic.get().amount()).isNull();
  }

  @Test
  void productSalesReturnsRowsOrderedByDateThenProduct() {
    var rows = query.productSales(SEP_13, SEP_14);
    assertThat(rows).hasSize(5);
    assertThat(rows.get(0).tradingDate()).isEqualTo(SEP_13);
    assertThat(rows.get(0).productName()).isEqualTo("chips");
  }

  @Test
  void topSellersSumsResolvedRowsAndFlagsUnresolved() {
    var top = query.topSellers(SEP_13, SEP_14, 10);
    assertThat(top).hasSize(2);

    // garlic aioli: resolved SEP_13 row sums; the unresolved SEP_14 row is flagged, not summed.
    assertThat(top.get(0).productName()).isEqualTo("garlic aioli");
    assertThat(top.get(0).amount()).isEqualByComparingTo("380.88");
    assertThat(top.get(0).hasConflict()).isTrue();

    assertThat(top.get(1).productName()).isEqualTo("chips");
    assertThat(top.get(1).amount()).isEqualByComparingTo("150.00");
    assertThat(top.get(1).quantitySold()).isEqualByComparingTo("30");
    assertThat(top.get(1).hasConflict()).isFalse();
  }

  @Test
  void rankingQualifiesAllRowsBeforeLimitingAndDoesNotInventZeroOrRank() {
    repository.save(row(SEP_13, "zero", "0", "0", "agreed", "agreed", false));
    var ranking = query.qualifiedTopSellers(SEP_13.minusDays(1), SEP_14, 1);
    assertThat(ranking.items()).hasSize(1);
    assertThat(ranking.items().getFirst().amount()).isEqualByComparingTo("380.88");
    assertThat(ranking.coverage().unresolvedDates()).containsExactly(SEP_13, SEP_14);
    assertThat(ranking.coverage().absentDates()).containsExactly(SEP_13.minusDays(1));
    assertThat(ranking.coverage().observedDates()).isEmpty();
    assertThat(ranking.coverage().expectedTradingPopulationKnown()).isFalse();
    assertThat(ranking.notices())
        .contains(
            "1 product(s) excluded from ranking: revenue unavailable.",
            "Ranked by known sales; full-period ranking unverified.");
    var all = query.qualifiedTopSellers(SEP_13, SEP_14, 10).items();
    assertThat(all)
        .extracting(TopSeller::productName)
        .containsExactly("garlic aioli", "chips", "zero");
    assertThat(all.getLast().amount()).isEqualByComparingTo("0");
  }

  @Test
  void emptyRankingStillCarriesRequestedCoverage() {
    var ranking = query.qualifiedTopSellers(SEP_14.plusDays(1), SEP_14.plusDays(2), 5);
    assertThat(ranking.items()).isEmpty();
    assertThat(ranking.coverage().absentDates())
        .containsExactly(SEP_14.plusDays(1), SEP_14.plusDays(2));
    assertThat(ranking.notices()).anyMatch(n -> n.startsWith("Incomplete subtotal"));
  }

  @Test
  void knownDateIsObservedWithoutInventingAbsentProductRows() {
    // Remove only the unavailable product: chips alone represents a date, not every product.
    jdbc.update(
        "delete from resolved_product_sales where trading_date = ? and product_name_key = ?",
        SEP_14,
        "garlic aioli");
    var ranking = query.qualifiedTopSellers(SEP_14, SEP_14, 5);
    assertThat(ranking.items()).extracting(TopSeller::productName).containsExactly("chips");
    assertThat(ranking.coverage().observedDates()).containsExactly(SEP_14);
    assertThat(ranking.coverage().expectedTradingPopulationKnown()).isFalse();
    assertThat(ranking.notices())
        .contains("Ranked by known sales; full-period ranking unverified.");
  }

  @Test
  void allUnknownRevenueDoesNotReceiveANumericalRank() {
    jdbc.update("truncate table resolved_product_sales");
    repository.save(row(SEP_13, "parma", null, null, "conflict", null, true));
    var ranking = query.qualifiedTopSellers(SEP_13, SEP_13, 5);
    assertThat(ranking.items()).isEmpty();
    assertThat(ranking.coverage().unresolvedDates()).containsExactly(SEP_13);
    assertThat(ranking.notices())
        .contains("1 product(s) excluded from ranking: revenue unavailable.");
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void resolvedSubtotalsAndQualificationsAgreeAcrossDashboardCatalogueSavedWidgetAndTool() {
    var owner = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var range = new TimeRange(SEP_13.minusDays(1), SEP_14, Calendar.CALENDAR);
    var q = new MetricQuery(MetricId.PRODUCT_TOP_SELLERS, range, TimeGrain.DAY, Set.of(), null);
    var expected = query.qualifiedTopSellers(range.from(), range.to(), 5);
    var direct = dashboard.topSellers(owner, range.from(), range.to(), 5);
    var catalogue = (RankedListResult) metrics.query(q);
    var tool = topProducts.execute(new GetTopProductsInput(range.from(), range.to(), 5), owner);
    var saved =
        savedRepository.save(
            SavedDashboard.create(
                "Qualified ranking",
                null,
                "grid",
                List.of(
                    new SavedWidget("ranking", "ranked-list", List.of(q), new WidgetLayout(6, 2))),
                DashboardFilters.empty(),
                Visibility.PRIVATE,
                "owner@test.local",
                Instant.EPOCH));
    var rendered = savedDashboards.render(owner, "owner@test.local", saved.id()).getFirst();
    assertThat(direct.items())
        .extracting(DashboardApplicationService.TopSeller::amount)
        .containsExactly(new BigDecimal("380.88"), new BigDecimal("150.00"));
    assertThat(catalogue.items())
        .extracting(MetricRankedItem::primary)
        .containsExactlyElementsOf(
            direct.items().stream().map(DashboardApplicationService.TopSeller::amount).toList());
    assertThat(direct.coverage()).isEqualTo(expected.coverage());
    assertThat(catalogue.provenance().coverage()).isEqualTo(expected.coverage());
    assertThat(tool.provenance().getFirst().coverage()).isEqualTo(expected.coverage());
    assertThat(catalogue.provenance().missingPeriods())
        .containsExactly(range.from(), SEP_13, SEP_14);
    assertThat(tool.provenance().getFirst().missingPeriods())
        .isEqualTo(catalogue.provenance().missingPeriods());
    assertThat(direct.notices()).isEqualTo(expected.notices());
    assertThat(catalogue.notices()).isEqualTo(expected.notices());
    assertThat(tool.notices()).isEqualTo(expected.notices());
    assertThat(rendered.notices()).isEqualTo(expected.notices());
    assertThat(rendered.deniedResource()).isNull();
    assertThat(rendered.widget()).isNotNull();
  }

  @Test
  void openConflictsCountsUnresolvedProductDays() {
    assertThat(query.openConflicts()).isEqualTo(2);
  }

  private static ResolvedProductSales row(
      LocalDate date,
      String key,
      String qty,
      String amount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict) {
    return new ResolvedProductSales(
        date,
        key,
        qty == null ? null : new BigDecimal(qty),
        amount == null ? null : new BigDecimal(amount),
        resolutionType,
        authoritativeSource,
        hasConflict,
        Instant.EPOCH);
  }
}
