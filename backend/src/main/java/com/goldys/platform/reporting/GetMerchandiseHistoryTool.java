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
    return "Search recorded product quantities and amounts in Lightspeed merchandise history, including older "
        + "dates absent from reconciled product metrics. For ANY named-product question (e.g. parma or roast), "
        + "use SEARCH with productName as a literal case-insensitive substring and inclusive startDate/endDate. "
        + "Search filters ALL matching lines BEFORE pagination, not a top-100 list. Use a short stem and inspect "
        + "names/categories for variants and modifiers. Return the observed quantities even when incomplete, "
        + "with observed days and dates; do not turn a completeness caveat into a blanket refusal. "
        + "COVERAGE without dates discovers earliest/latest retained dates. PRODUCTS without productName lists "
        + "rankings only: absence from a ranking proves nothing about a named product. page is zero-based; "
        + "limit is 1–100. Follow hasMore notices before claiming complete matching results. "
        + "These are recorded observations, not certified annual totals or reconciled revenue.";
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
    return in.mode() == GetMerchandiseHistoryInput.Mode.COVERAGE
        ? coverageResult()
        : productResult(in);
  }

  private ToolResult coverageResult() {
    var columns =
        List.of(
            new Column("dataset", "Dataset", null),
            new Column("firstDate", "First observed date", null),
            new Column("lastDate", "Last observed date", null),
            new Column("observedDays", "Observed days", null),
            new Column("rows", "Retained rows", null));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (var c : query.coverage()) {
      var row = new LinkedHashMap<String, Object>();
      row.put("dataset", c.dataset());
      row.put("firstDate", c.firstDate());
      row.put("lastDate", c.lastDate());
      row.put("observedDays", c.observedDays());
      row.put("rows", c.rows());
      rows.add(row);
    }
    return result(
        "All retained dates; date bounds do not establish continuous or complete coverage.",
        columns,
        rows,
        List.of());
  }

  private ToolResult productResult(GetMerchandiseHistoryInput in) {
    var columns =
        List.of(
            new Column("site", "Site", null),
            new Column("productNumber", "Product number", null),
            new Column("sku", "SKU", null),
            new Column("name", "Product", null),
            new Column("category", "Source category", null),
            new Column("quantity", "Observed quantity", null),
            new Column("amount", "Observed amount (AUD)", "currency"),
            new Column("observedDays", "Days with matching records", null),
            new Column("firstDate", "First matching date", null),
            new Column("lastDate", "Last matching date", null),
            new Column("lines", "Recorded lines", null));
    var page =
        query.products(in.startDate(), in.endDate(), in.productName(), in.page(), in.limit());
    String scope =
        "Scope: "
            + in.startDate()
            + " to "
            + in.endDate()
            + " inclusive; up to "
            + in.limit()
            + " groups ranked by observed amount, not a complete venue total. Source site, product number, SKU, name and category remain separate.";
    return result(
        scope,
        columns,
        page.items().stream().map(GetMerchandiseHistoryTool::productRow).toList(),
        List.of(
            selectionNotice(in, page),
            "Report available recorded quantities first, with the matching names and observed-day coverage. "
                + "Completeness is not verified by this query; that is not evidence that data is missing. "
                + "Days with matching records are not days of complete source coverage. Do not infer missing trading days from them."));
  }

  private static Map<String, Object> productRow(MerchandiseHistoryQuery.Product p) {
    var row = new LinkedHashMap<String, Object>();
    row.put("site", p.site());
    row.put("productNumber", p.productNumber());
    row.put("sku", p.sku());
    row.put("name", p.name());
    row.put("category", p.category());
    row.put("quantity", p.quantity());
    row.put("amount", p.amount());
    row.put("observedDays", p.observedDays());
    row.put("firstDate", p.firstDate());
    row.put("lastDate", p.lastDate());
    row.put("lines", p.lines());
    return row;
  }

  private static String selectionNotice(
      GetMerchandiseHistoryInput in, MerchandiseHistoryQuery.ProductPage page) {
    String selection =
        in.mode() == GetMerchandiseHistoryInput.Mode.SEARCH
            ? "Literal name search: "
                + in.productName()
                + ". Search covers all retained lines in the requested dates before ranking. "
            : "Unfiltered ranking: absence from this page is NOT evidence of no matching product. Use SEARCH for a named product. ";
    return "Matching source product groups: "
        + page.matchingGroups()
        + "; page: "
        + page.page()
        + "; returned: "
        + page.items().size()
        + "; hasMore: "
        + page.hasMore()
        + ". "
        + (page.hasMore()
            ? "Read the next page or narrow the name before presenting all matches. "
            : "")
        + selection
        + "Report quantities by returned product name and source group. Modifiers (e.g. No Parma Sauce), "
        + "different dishes and variants are not interchangeable; do not silently combine them. "
        + "No matches means no matching retained observations, not zero sales. Try a shorter stem or known spelling variant before concluding no matches.";
  }

  private static ToolResult result(
      String scope,
      List<Column> columns,
      List<Map<String, Object>> rows,
      List<String> selectionNotices) {
    List<String> notices = new ArrayList<>();
    notices.add(scope);
    notices.addAll(selectionNotices);
    notices.add(
        "Source: resolved Lightspeed merchandise lines; separate from reconciled product/day metrics. "
            + "Amounts retain source tax basis. Missing amounts or quantities remain unknown. "
            + "This tool does not certify repairs or completeness. Only describe a period as incomplete when current evidence establishes a gap for that period; "
            + "observed history must not be presented as verified revenue. Empty results are missing evidence, not zero sales.");
    return new ToolResult(
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Merchandise history", scope, columns, rows, null),
        notices,
        List.of(),
        List.of());
  }
}
