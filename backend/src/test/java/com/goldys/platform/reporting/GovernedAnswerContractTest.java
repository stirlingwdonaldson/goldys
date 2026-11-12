package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.conversational.AnswerPayload;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class GovernedAnswerContractTest {
  @Autowired ObjectMapper mapper;

  @Test
  void serializesAnswerForFrontendContractCheck() throws Exception {
    var catalog = new MetricCatalog();
    var describe = new DescribeMetricsTool(catalog, mock(PermissionService.class));
    var metrics = mock(MetricQueryService.class);
    var analysis = new RunMetricAnalysisTool(catalog, metrics);
    var date = LocalDate.of(2026, 11, 2);
    var range = new TimeRange(date, date.plusDays(6), Calendar.CALENDAR);
    var provenance =
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            range,
            TimeGrain.TOTAL,
            "resolved_daily_sales",
            Instant.EPOCH,
            List.of(),
            "1",
            new TrustSummary(
                TrustState.SINGLE_SOURCE,
                FreshnessState.FRESH,
                "LIGHTSPEED",
                Instant.EPOCH,
                Instant.EPOCH,
                Duration.ofHours(48)),
            new MetricCoverage(range.from(), range.to(), List.of(date), List.of(), List.of()));
    when(metrics.query(any()))
        .thenReturn(
            new TimeSeriesResult(
                MetricId.SALES_GROSS,
                List.of(
                    new MetricSeries(
                        null, List.of(new MetricPoint(date, new BigDecimal("123.45"))))),
                List.of(),
                provenance));
    var definition = describe.execute(new DescribeMetricsInput(Set.of(MetricId.SALES_GROSS)), null);
    var result =
        analysis.execute(
            new RunMetricAnalysisInput(
                List.of(
                    new GetMetricInput(
                        MetricId.SALES_GROSS,
                        range.from(),
                        range.to(),
                        TimeGrain.TOTAL,
                        Set.of()))),
            null);
    var payload =
        new AnswerPayload(
            List.of(definition.widget(), result.widget()),
            List.of(
                new AnswerPayload.TraceEntry(
                    describe.name(), describe.description(), definition.provenance()),
                new AnswerPayload.TraceEntry(
                    analysis.name(), analysis.description(), result.provenance())),
            Instant.EPOCH,
            List.of(),
            null);
    ObjectNode actual = (ObjectNode) mapper.readTree(mapper.writeValueAsString(payload));
    ((ObjectNode) actual.at("/widgets/0")).put("id", "metric-definitions");
    ((ObjectNode) actual.at("/widgets/1")).put("id", "metric-analysis");
    // The frontend parses this same fixture; backend shape changes must update both sides.
    var expected =
        mapper.readTree(
            Files.readString(
                Path.of("..", "frontend", "tests", "fixtures", "governed-answer.json")));
    assertThat(actual).isEqualTo(expected);
  }
}
