package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.*;
import com.goldys.platform.conversational.ConversationContext;
import com.goldys.platform.conversational.ReportingToolCallbacks;
import com.goldys.platform.metrics.OperationalMetrics;
import com.goldys.platform.semantic.catalog.*;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GovernedAnalysisTest {
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
  private final MetricCatalog catalog = new MetricCatalog();
  private final PermissionService permissions = mock(PermissionService.class);
  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final DescribeMetricsTool describe = new DescribeMetricsTool(catalog, permissions);
  private final RunMetricAnalysisTool analysis = new RunMetricAnalysisTool(catalog, metrics);
  private final List<ReportingTool> tools = List.of(describe, analysis);
  private final ToolDispatcher dispatcher =
      new ToolDispatcher(
          new ToolRegistry(tools), permissions, catalog, mock(OperationalMetrics.class));

  @Test
  void discoversOnlyDefinitionsWhoseTransitiveOperandsArePermitted() {
    when(permissions.isAllowed(any(), any(), any()))
        .thenAnswer(call -> call.getArgument(1).equals(new ResourceKey("reconciliation.sales")));
    var result =
        dispatcher.dispatch(ToolId.DESCRIBE_METRICS, new DescribeMetricsInput(Set.of()), OWNER);
    var rows = ((TableWidgetSpec) result.widget()).rows();
    assertThat(rows)
        .anySatisfy(
            row -> {
              assertThat(row.get("metric")).isEqualTo("sales.gross");
              assertThat(row.get("definition"))
                  .isEqualTo(catalog.definition(MetricId.SALES_GROSS).definition());
              assertThat(row.get("version"))
                  .isEqualTo(catalog.definition(MetricId.SALES_GROSS).version());
            });
    assertThat(rows)
        .noneSatisfy(
            row -> assertThat(row.get("metric")).isEqualTo("sales.average_spend_per_cover"));
    assertThat(rows).noneSatisfy(row -> assertThat(row.get("metric")).isEqualTo("labour.cost"));
    verifyNoInteractions(metrics);
  }

  @Test
  void explicitlyRequestedRestrictedDefinitionIsDeniedRatherThanFiltered() {
    deny("reservations.metrics");
    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    ToolId.DESCRIBE_METRICS,
                    new DescribeMetricsInput(
                        Set.of(MetricId.SALES_GROSS, MetricId.SALES_AVERAGE_SPEND_PER_COVER)),
                    OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deniesWholeAnalysisBeforeReadingAnyMetric() {
    deny("labour.cost");
    var plan =
        new RunMetricAnalysisInput(
            List.of(query(MetricId.SALES_GROSS), query(MetricId.LABOUR_COST)));
    assertThatThrownBy(() -> dispatcher.dispatch(ToolId.RUN_METRIC_ANALYSIS, plan, OWNER))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(metrics);
  }

  @Test
  void validatesWholePlanBeforeReadingAnyMetric() {
    var invalid =
        new GetMetricInput(MetricId.INVENTORY_STOCK_ON_HAND, DATE, DATE, TimeGrain.TOTAL, Set.of());
    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    ToolId.RUN_METRIC_ANALYSIS,
                    new RunMetricAnalysisInput(List.of(query(MetricId.SALES_GROSS), invalid)),
                    OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("grain");
    verifyNoInteractions(metrics);
  }

  @Test
  void preservesMissingZeroScopeAndQualificationsWithoutSummingRatios() {
    when(metrics.query(any()))
        .thenAnswer(
            call -> {
              MetricQuery q = call.getArgument(0);
              return result(q, q.metric() == MetricId.SALES_GROSS ? BigDecimal.ZERO : null);
            });
    var input =
        new RunMetricAnalysisInput(
            List.of(query(MetricId.SALES_GROSS), query(MetricId.RESERVATIONS_NO_SHOW_RATE)));
    var result = dispatcher.dispatch(ToolId.RUN_METRIC_ANALYSIS, input, OWNER);
    var rows = ((TableWidgetSpec) result.widget()).rows();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("value", BigDecimal.ZERO)
        .containsEntry("status", "ZERO")
        .containsEntry("from", "2026-11-02")
        .containsEntry("grain", "TOTAL")
        .containsEntry("unit", "AUD");
    assertThat(rows.get(1))
        .containsEntry("value", null)
        .containsEntry("status", "UNKNOWN")
        .containsEntry("unit", "%")
        .containsEntry("query", 2);
    assertThat(result.notices())
        .containsExactly("Query 1: Unverified coverage", "Query 2: Unverified coverage");
    assertThat(result.provenance()).hasSize(2);
    verify(metrics, times(2)).query(any());
    assertThat(analysis.toMetricQueries(input))
        .extracting(MetricQuery::metric)
        .containsExactly(MetricId.SALES_GROSS, MetricId.RESERVATIONS_NO_SHOW_RATE);
  }

  @Test
  void sameMetricInDifferentPeriodsRetainsBothScopes() {
    when(metrics.query(any()))
        .thenAnswer(call -> result(call.getArgument(0), new BigDecimal("12.50")));
    var previous =
        new GetMetricInput(
            MetricId.SALES_GROSS,
            DATE.minusWeeks(1),
            DATE.minusWeeks(1),
            TimeGrain.TOTAL,
            Set.of());
    var result =
        dispatcher.dispatch(
            ToolId.RUN_METRIC_ANALYSIS,
            new RunMetricAnalysisInput(List.of(previous, query(MetricId.SALES_GROSS))),
            OWNER);
    assertThat(((TableWidgetSpec) result.widget()).rows())
        .extracting(row -> row.get("from"))
        .containsExactly("2026-10-26", "2026-11-02");
  }

  @Test
  void analysisHasBoundedSizeAndRejectsUnsupportedRankedResults() {
    assertThatThrownBy(() -> new RunMetricAnalysisInput(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new RunMetricAnalysisInput(
                    java.util.Collections.nCopies(7, query(MetricId.SALES_GROSS))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    ToolId.RUN_METRIC_ANALYSIS,
                    new RunMetricAnalysisInput(List.of(query(MetricId.PRODUCT_TOP_SELLERS))),
                    OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("get_metric");
    verifyNoInteractions(metrics);
  }

  @Test
  void realCallbackDeserializesPlanAndRecordsEvidence() {
    when(metrics.query(any()))
        .thenAnswer(call -> result(call.getArgument(0), new BigDecimal("4273.50")));
    var context = new ConversationContext();
    var callback =
        new ReportingToolCallbacks()
            .forTools(tools, OWNER, context, dispatcher, new ObjectMapper()).stream()
                .filter(c -> c.getToolDefinition().name().equals("run_metric_analysis"))
                .findFirst()
                .orElseThrow();
    String answer =
        callback.call(
            """
        {"queries":[{"metric":"sales.gross","startDate":"2026-11-02","endDate":"2026-11-08","grain":"TOTAL","dimensions":[]}]}
        """);
    assertThat(answer).contains("4273.50");
    assertThat(context.toAnswerPayload().widgets()).hasSize(1);
    assertThat(context.toAnswerPayload().trace().get(0).provenance()).hasSize(1);
    assertThat(callback.getToolDefinition().inputSchema()).contains("SALES_GROSS", "TOTAL");
  }

  private void deny(String resource) {
    doThrow(AccessDeniedException.forResource(resource))
        .when(permissions)
        .require(OWNER, new ResourceKey(resource), PermissionAction.READ);
  }

  private static GetMetricInput query(MetricId id) {
    return new GetMetricInput(id, DATE, DATE, TimeGrain.TOTAL, Set.of());
  }

  private static TimeSeriesResult result(MetricQuery query, BigDecimal value) {
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, List.of(new MetricPoint(query.range().from(), value)))),
        List.of("Unverified coverage"),
        new MetricProvenance(
            query.metric(),
            "1",
            query.range(),
            query.grain(),
            "test",
            Instant.EPOCH,
            List.of(),
            "1"));
  }
}
