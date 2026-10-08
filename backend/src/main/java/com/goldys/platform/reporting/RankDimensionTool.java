package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
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
 * Ranks the groups of a dimension by a metric over a date range, delegating to {@link
 * RankingService} and rendering a ranked-list widget.
 */
@Component
public class RankDimensionTool implements ReportingTool {
  private final RankingService ranking;
  private final WidgetRenderer renderer;

  public RankDimensionTool(RankingService ranking, WidgetRenderer renderer) {
    this.ranking = ranking;
    this.renderer = renderer;
  }

  @Override
  public ToolId id() {
    return ToolId.RANK_DIMENSION;
  }

  @Override
  public String name() {
    return "rank_dimension";
  }

  @Override
  public String description() {
    return "Rank the groups of a dimension (e.g. product, department) by a metric over a date "
        + "range, returning the top groups as a ranked list.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return RankDimensionInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("conversational.chat");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    RankDimensionInput in = require(input);
    RankedListResult ranked =
        ranking.rank(
            in.metric(),
            in.dimension(),
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            in.limit());
    WidgetSpec widget =
        renderer.render(UUID.randomUUID().toString(), "ranked-list", List.of(ranked));
    return new ToolResult(widget, ranked.notices(), List.of(ranked.provenance()), List.of());
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    RankDimensionInput in = require(input);
    return List.of(
        new MetricQuery(
            in.metric(),
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            TimeGrain.DAY,
            Set.of(in.dimension()),
            null));
  }

  private static RankDimensionInput require(ToolInput input) {
    if (!(input instanceof RankDimensionInput in)) {
      throw new IllegalArgumentException(
          "Expected RankDimensionInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }
}
