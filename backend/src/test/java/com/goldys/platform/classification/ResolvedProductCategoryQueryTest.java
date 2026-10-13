package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage for the in-memory join that turns resolved product sales into category sales. */
class ResolvedProductCategoryQueryTest {

  private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);

  private final ProductMetricsQuery productMetrics = mock(ProductMetricsQuery.class);
  private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
  private final ProductCategoryAssignmentRepository assignments =
      mock(ProductCategoryAssignmentRepository.class);

  private final ResolvedProductCategoryQuery query =
      new ResolvedProductCategoryQuery(productMetrics, categories, assignments);

  @Test
  void joins_sales_to_category() {
    ProductCategory mains = ProductCategory.create("Mains", "Food", 0, "Main courses");
    ProductCategoryAssignment assignment = ProductCategoryAssignment.create("kids fish", NOW);
    assignment.markAssigned(mains.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);

    when(productMetrics.productSales(FROM, TO))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    FROM, "kids fish", new BigDecimal("2"), new BigDecimal("30"), "ctb", false)));
    when(categories.findAll()).thenReturn(List.of(mains));
    when(assignments.findAll()).thenReturn(List.of(assignment));

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales("Food", "Mains", new BigDecimal("2"), new BigDecimal("30")));
  }

  @Test
  void unassigned_products_roll_up_to_uncategorised() {
    when(productMetrics.productSales(FROM, TO))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    FROM, "kids fish", new BigDecimal("2"), new BigDecimal("30"), "ctb", false)));
    when(categories.findAll()).thenReturn(List.of());
    when(assignments.findAll()).thenReturn(List.of());

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales(null, "Uncategorised", new BigDecimal("2"), new BigDecimal("30")));
  }

  @Test
  void flat_category_has_null_group() {
    ProductCategory cocktails = ProductCategory.create("Cocktails", null, 0, null);
    ProductCategoryAssignment assignment =
        ProductCategoryAssignment.create("espresso martini", NOW);
    assignment.markAssigned(cocktails.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);

    when(productMetrics.productSales(FROM, TO))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    FROM,
                    "espresso martini",
                    new BigDecimal("1"),
                    new BigDecimal("12"),
                    "ctb",
                    false)));
    when(categories.findAll()).thenReturn(List.of(cocktails));
    when(assignments.findAll()).thenReturn(List.of(assignment));

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales(null, "Cocktails", new BigDecimal("1"), new BigDecimal("12")));
  }

  @Test
  void groups_by_category_and_sums() {
    ProductCategory mains = ProductCategory.create("Mains", "Food", 0, null);
    ProductCategoryAssignment beef = ProductCategoryAssignment.create("beef burger", NOW);
    beef.markAssigned(mains.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);
    ProductCategoryAssignment fish = ProductCategoryAssignment.create("kids fish", NOW);
    fish.markAssigned(mains.id(), new BigDecimal("0.8"), AssignmentSource.JEV, NOW);

    when(productMetrics.productSales(FROM, TO))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    FROM, "beef burger", new BigDecimal("2"), new BigDecimal("20"), "ctb", false),
                new ProductSalesMetric(
                    FROM, "kids fish", new BigDecimal("3"), new BigDecimal("30"), "ctb", false)));
    when(categories.findAll()).thenReturn(List.of(mains));
    when(assignments.findAll()).thenReturn(List.of(beef, fish));

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales("Food", "Mains", new BigDecimal("5"), new BigDecimal("50")));
  }

  @Test
  void null_quantity_days_are_treated_as_zero() {
    ProductCategory mains = ProductCategory.create("Mains", "Food", 0, null);
    ProductCategoryAssignment assignment = ProductCategoryAssignment.create("kids fish", NOW);
    assignment.markAssigned(mains.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);

    // A null day after a non-null day exercises nullSafeAdd(non-null, null), the previous NPE path.
    when(productMetrics.productSales(FROM, TO))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    FROM, "kids fish", new BigDecimal("2"), new BigDecimal("30"), "ctb", false),
                new ProductSalesMetric(TO, "kids fish", null, null, "ctb", false)));
    when(categories.findAll()).thenReturn(List.of(mains));
    when(assignments.findAll()).thenReturn(List.of(assignment));

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales("Food", "Mains", new BigDecimal("2"), new BigDecimal("30")));
  }

  @Test
  void all_null_quantities_surface_as_zero() {
    when(productMetrics.productSales(FROM, TO))
        .thenReturn(List.of(new ProductSalesMetric(FROM, "kids fish", null, null, "ctb", false)));
    when(categories.findAll()).thenReturn(List.of());
    when(assignments.findAll()).thenReturn(List.of());

    List<CategorySales> result = query.salesByCategory(FROM, TO);

    assertThat(result)
        .containsExactly(
            new CategorySales(null, "Uncategorised", BigDecimal.ZERO, BigDecimal.ZERO));
  }
}
