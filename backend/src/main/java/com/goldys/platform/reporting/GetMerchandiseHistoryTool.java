package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.MerchandiseHistoryQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GetMerchandiseHistoryTool implements ReportingTool {
  private final MerchandiseHistoryQuery query;

  public GetMerchandiseHistoryTool(MerchandiseHistoryQuery query) {
    this.query = query;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_MERCHANDISE_HISTORY;
  }

  @Override
  public String name() {
    return "get_merchandise_history";
  }

  @Override
  public String description() {
    return "Discover historical product-level sales and query Lightspeed merchandise lines. "
        + "Use COVERAGE without dates to find the earliest/latest retained dates, including older history absent "
        + "from product.sales_amount/product.sales_quantity. Use PRODUCTS with explicit inclusive dates to rank "
        + "observed merchandise by amount (limit 1–100). These are source-qualified observations, not reconciled "
        + "product metrics. Never infer that older product data is unavailable from an empty product metric alone.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetMerchandiseHistoryInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("sale-items.metrics");
  }

  @Override
  public Set<com.goldys.platform.semantic.catalog.MetricId> referencedMetrics(ToolInput input) {
    return Set.of(com.goldys.platform.semantic.catalog.MetricId.PRODUCT_SALES_AMOUNT);
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetMerchandiseHistoryInput in))
      throw new IllegalArgumentException("Expected merchandise history input");
    List<Column> columns;
    List<Map<String, Object>> rows = new ArrayList<>();
    String scope;
    if (in.mode() == GetMerchandiseHistoryInput.Mode.COVERAGE) {
      columns =
          List.of(
              new Column("dataset", "Dataset", null),
              new Column("firstDate", "First observed date", null),
              new Column("lastDate", "Last observed date", null),
              new Column("observedDays", "Observed days", null),
              new Column("rows", "Retained rows", null));
      for (var c : query.coverage()) {
        var row = new LinkedHashMap<String, Object>();
        row.put("dataset", c.dataset());
        row.put("firstDate", c.firstDate());
        row.put("lastDate", c.lastDate());
        row.put("observedDays", c.observedDays());
        row.put("rows", c.rows());
        rows.add(row);
      }
      scope = "All retained dates; date bounds do not establish continuous or complete coverage.";
    } else {
      columns =
          List.of(
              new Column("site", "Site", null),
              new Column("productNumber", "Product number", null),
              new Column("sku", "SKU", null),
              new Column("name", "Product", null),
              new Column("category", "Source category", null),
              new Column("quantity", "Observed quantity", null),
              new Column("amount", "Observed amount (AUD)", "currency"),
              new Column("observedDays", "Observed days", null));
      for (var p : query.products(in.startDate(), in.endDate(), in.limit())) {
        var row = new LinkedHashMap<String, Object>();
        row.put("site", p.site());
        row.put("productNumber", p.productNumber());
        row.put("sku", p.sku());
        row.put("name", p.name());
        row.put("category", p.category());
        row.put("quantity", p.quantity());
        row.put("amount", p.amount());
        row.put("observedDays", p.observedDays());
        rows.add(row);
      }
      scope =
          "Scope: "
              + in.startDate()
              + " to "
              + in.endDate()
              + " inclusive; up to "
              + in.limit()
              + " groups ranked by observed amount, not a complete venue total. Source site, product number, SKU, name and category remain separate.";
    }
    return new ToolResult(
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Merchandise history", scope, columns, rows, null),
        List.of(
            scope,
            "Source: resolved Lightspeed merchandise lines; separate from reconciled product/day metrics. "
                + "Amounts retain source tax basis. Missing amounts or quantities remain unknown. "
                + "The 12 November 2026 historical audit found substantial missing product lines in 2025 and inflated amounts "
                + "from payment-join duplication in some historical exports. This tool does not certify repairs or completeness; "
                + "observed history must not be presented as verified revenue. Empty results are missing evidence, not zero sales."),
        List.of(),
        List.of());
  }
}
