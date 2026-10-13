package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetSpec;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolved sales grouped by the pub's internal product category for Ask Goldy's. Reads {@link
 * ProductCategoryMetricsQuery} directly (category sales are a resolved projection, not a
 * time-series catalogue metric), so {@code toMetricQueries} is empty and the widget is not
 * re-runnable from a saved dashboard.
 */
@Component
public class GetSalesByCategoryTool implements ReportingTool {
  private final ProductCategoryMetricsQuery metrics;

  public GetSalesByCategoryTool(ProductCategoryMetricsQuery metrics) {
    this.metrics = metrics;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_SALES_BY_CATEGORY;
  }

  @Override
  public String name() {
    return "get_sales_by_category";
  }

  @Override
  public String description() {
    return "Sales totals grouped by the internal product category.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetSalesByCategoryInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("products.category");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    GetSalesByCategoryInput in = require(input);
    LocalDate from = in.startDate();
    LocalDate to = in.endDate();
    List<CategorySales> rows = metrics.salesByCategory(from, to);
    List<Column> columns =
        List.of(
            new Column("group", "Group", null),
            new Column("category", "Category", null),
            new Column("quantity", "Quantity", null),
            new Column("amount", "Amount", "currency"));
    List<Map<String, Object>> tableRows =
        rows.stream()
            .map(
                r -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  // A flat category has no parent group; keep the column present with a null value.
                  row.put("group", r.group());
                  row.put("category", r.category());
                  row.put("quantity", r.quantity());
                  row.put("amount", r.amount());
                  return row;
                })
            .toList();
    WidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Sales by category", null, columns, tableRows, null);
    return new ToolResult(widget, List.of(), List.of(), List.of());
  }

  private static GetSalesByCategoryInput require(ToolInput input) {
    if (!(input instanceof GetSalesByCategoryInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByCategoryInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }
}
