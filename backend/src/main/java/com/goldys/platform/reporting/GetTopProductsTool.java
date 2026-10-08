package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.RankingService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.WidgetSpec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Returns the top products ranked by sales amount over a date range, delegating to {@link
 * RankingService} and rendering a ranked-list widget.
 */
@Component
public class GetTopProductsTool implements ReportingTool {
  private final RankingService ranking;
  private final WidgetRenderer renderer;

  public GetTopProductsTool(RankingService ranking, WidgetRenderer renderer) {
    this.ranking = ranking;
    this.renderer = renderer;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_TOP_PRODUCTS;
  }

  @Override
  public String name() {
    return "get_top_products";
  }

  @Override
  public String description() {
    return "Return the top products ranked by sales amount over a date range as a ranked list.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetTopProductsInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("reconciliation.sales");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    GetTopProductsInput in = require(input);
    RankedListResult ranked =
        ranking.rank(
            MetricId.PRODUCT_SALES_AMOUNT,
            Dimension.PRODUCT,
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            in.limit());
    WidgetSpec widget =
        renderer.render(UUID.randomUUID().toString(), "ranked-list", List.of(ranked));
    return new ToolResult(widget, ranked.notices(), List.of(ranked.provenance()), List.of());
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    GetTopProductsInput in = require(input);
    return List.of(
        new MetricQuery(
            MetricId.PRODUCT_SALES_AMOUNT,
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            TimeGrain.DAY,
            Set.of(Dimension.PRODUCT),
            null));
  }

  private static GetTopProductsInput require(ToolInput input) {
    if (!(input instanceof GetTopProductsInput in)) {
      throw new IllegalArgumentException(
          "Expected GetTopProductsInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }
}
