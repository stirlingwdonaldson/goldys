package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEvaluatorTest {

  private static final Instant OLD = Instant.parse("2026-09-13T09:00:00Z");
  private static final Instant NEW = Instant.parse("2026-09-14T09:00:00Z");

  private static ResolutionRule rule(String strategy, String customLogic, List<String> priority) {
    return ResolutionRule.create(
        "daily_sales", "daily_sales", strategy, customLogic, priority, "a@b.com", OLD);
  }

  private static SourceMetric m(String source, String metric, Instant at) {
    return new SourceMetric(source, new BigDecimal(metric), at);
  }

  @Test
  void priorityFallsThroughToTheNextSourceThatHasData() {
    ResolutionRule r = rule("priority", null, List.of("LIGHTSPEED", "CTB"));
    List<SourceMetric> sources = List.of(m("CTB", "12.00", OLD));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }

  @Test
  void priorityIsUnresolvedWhenNoListedSourceHasData() {
    ResolutionRule r = rule("priority", null, List.of("LIGHTSPEED"));
    assertThat(RuleEvaluator.resolve(r, List.of(m("CTB", "12.00", OLD)))).isEmpty();
  }

  @Test
  void manualLeavesEverythingUnresolved() {
    ResolutionRule r = rule("manual", null, null);
    assertThat(RuleEvaluator.resolve(r, List.of(m("LIGHTSPEED", "12.00", OLD)))).isEmpty();
  }

  @Test
  void customFlagLeavesEverythingUnresolved() {
    ResolutionRule r = rule("custom", "flag", null);
    assertThat(RuleEvaluator.resolve(r, List.of(m("LIGHTSPEED", "12.00", OLD)))).isEmpty();
  }

  @Test
  void customHighestPicksTheLargestMetric() {
    ResolutionRule r = rule("custom", "highest", null);
    List<SourceMetric> sources = List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }

  @Test
  void customLowestPicksTheSmallestMetric() {
    ResolutionRule r = rule("custom", "lowest", null);
    List<SourceMetric> sources = List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("LIGHTSPEED");
  }

  @Test
  void customNewestPicksTheMostRecentlyRecorded() {
    ResolutionRule r = rule("custom", "newest", null);
    List<SourceMetric> sources = List.of(m("LIGHTSPEED", "12.00", OLD), m("CTB", "20.00", NEW));
    assertThat(RuleEvaluator.resolve(r, sources)).contains("CTB");
  }
}
