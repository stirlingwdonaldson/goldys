package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GetSalesByCategoryToolTest {

  private static final LocalDate START = LocalDate.of(2026, 9, 1);
  private static final LocalDate END = LocalDate.of(2026, 9, 30);

  private final ProductCategoryMetricsQuery metrics = mock(ProductCategoryMetricsQuery.class);
  private final GetSalesByCategoryTool tool = new GetSalesByCategoryTool(metrics);

  @Test
  void returnsTableWidget() {
    when(metrics.salesByCategory(START, END))
        .thenReturn(
            List.of(
                new CategorySales("Food", "Burgers", new BigDecimal("10"), new BigDecimal("150"))));

    ToolResult result = tool.execute(new GetSalesByCategoryInput(START, END), null);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.columns())
        .extracting(c -> c.key())
        .containsExactly("group", "category", "quantity", "amount");
    assertThat(widget.columns().get(3).format()).isEqualTo("currency");
    assertThat(widget.rows()).hasSize(1);
    Map<String, Object> row = widget.rows().get(0);
    assertThat(row.get("group")).isEqualTo("Food");
    assertThat(row.get("category")).isEqualTo("Burgers");
    assertThat(row.get("quantity")).isEqualTo(new BigDecimal("10"));
    assertThat(row.get("amount")).isEqualTo(new BigDecimal("150"));
  }

  @Test
  void resourceIsProductsCategory() {
    assertThat(tool.resource().value()).isEqualTo("products.category");
  }

  @Test
  void toMetricQueriesIsEmpty() {
    assertThat(tool.toMetricQueries(new GetSalesByCategoryInput(START, END))).isEmpty();
  }
}
