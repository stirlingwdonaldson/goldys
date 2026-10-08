package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.RankingService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.RankedListWidgetSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankDimensionToolTest {

  private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_7 = LocalDate.of(2026, 1, 7);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final MetricCatalog CATALOG = new MetricCatalog();
  private static final WidgetRenderer RENDERER = new WidgetRenderer(CATALOG);

  private static RankDimensionTool tool(RankingService ranking) {
    return new RankDimensionTool(ranking, RENDERER);
  }

  private static RankDimensionInput input() {
    return new RankDimensionInput(
        MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, JAN_1, JAN_7, 10);
  }

  @Test
  void rankDimensionDelegatesToRankingService() {
    RankingService ranking = mock(RankingService.class);
    RankedListResult ranked = mock(RankedListResult.class);
    when(ranked.metric()).thenReturn(MetricId.PRODUCT_SALES_AMOUNT);
    when(ranked.items()).thenReturn(List.of());
    when(ranked.notices()).thenReturn(List.of());
    when(ranked.provenance()).thenReturn(provenance());
    when(ranking.rank(any(), any(), any(), anyInt())).thenReturn(ranked);

    ToolResult result = tool(ranking).execute(input(), OWNER);

    assertThat(result.widget()).isInstanceOf(RankedListWidgetSpec.class);
    assertThat(result.provenance()).containsExactly(provenance());
    assertThat(result.relatedMetrics()).isEmpty();
    verify(ranking)
        .rank(
            MetricId.PRODUCT_SALES_AMOUNT,
            Dimension.PRODUCT,
            new TimeRange(JAN_1, JAN_7, Calendar.CALENDAR),
            10);
  }

  @Test
  void toMetricQueriesReturnsRankedMetricQueryWithDimensionForPerMetricAuth() {
    RankDimensionTool tool = tool(mock(RankingService.class));

    List<MetricQuery> queries = tool.toMetricQueries(input());

    assertThat(queries).hasSize(1);
    MetricQuery q = queries.get(0);
    assertThat(q.metric()).isEqualTo(MetricId.PRODUCT_SALES_AMOUNT);
    assertThat(q.range()).isEqualTo(new TimeRange(JAN_1, JAN_7, Calendar.CALENDAR));
    assertThat(q.grain()).isEqualTo(TimeGrain.DAY);
    assertThat(q.dimensions()).containsExactly(Dimension.PRODUCT);
    assertThat(q.comparison()).isNull();
  }

  @Test
  void validatesInput() {
    assertThatThrownBy(
            () ->
                new RankDimensionInput(
                    MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, JAN_7, JAN_1, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("endDate");
    assertThatThrownBy(() -> new RankDimensionInput(null, Dimension.PRODUCT, JAN_1, JAN_7, 10))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new RankDimensionInput(MetricId.PRODUCT_SALES_AMOUNT, null, JAN_1, JAN_7, 10))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new RankDimensionInput(
                    MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, JAN_1, JAN_7, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit");
  }

  @Test
  void rejectsWrongInputType() {
    RankDimensionTool tool = tool(mock(RankingService.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RankDimensionInput");
    assertThatThrownBy(() -> tool.toMetricQueries(new OtherInput()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RankDimensionInput");
  }

  @Test
  void exposesStableIdentity() {
    RankDimensionTool tool = tool(mock(RankingService.class));

    assertThat(tool.id()).isEqualTo(ToolId.RANK_DIMENSION);
    assertThat(tool.name()).isEqualTo("rank_dimension");
    assertThat(tool.inputType()).isEqualTo(RankDimensionInput.class);
    assertThat(tool.resource()).isEqualTo(new ResourceKey("conversational.chat"));
  }

  @Test
  void validatesBeforeQuerying() {
    RankingService ranking = mock(RankingService.class);

    assertThatThrownBy(() -> tool(ranking).execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(ranking);
  }

  private static MetricProvenance provenance() {
    return new MetricProvenance(
        MetricId.PRODUCT_SALES_AMOUNT,
        "1",
        new TimeRange(JAN_1, JAN_7, Calendar.CALENDAR),
        TimeGrain.DAY,
        "test",
        Instant.EPOCH,
        List.of(),
        "1");
  }

  private record OtherInput() implements ToolInput {}
}
