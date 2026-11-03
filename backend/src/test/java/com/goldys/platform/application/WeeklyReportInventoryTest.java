package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.semantic.WeeklyInventoryQuery.Waste;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportInventoryTest {
  @Test
  void includesUnknownReasonsAndNormalizesOnlyKnownVariants() {
    var d = LocalDate.parse("2026-10-26");
    var result =
        WeeklyReportInventory.summarize(
            List.of(
                new Waste(d, null, 1, 1, new BigDecimal("40")),
                new Waste(d, "Knockoff", 1, 1, new BigDecimal("10")),
                new Waste(d, " knock off ", 2, 1, new BigDecimal("50"))));
    assertThat(result.lines()).isEqualTo(4);
    assertThat(result.valuedLines()).isEqualTo(3);
    assertThat(result.value()).isEqualByComparingTo("100");
    assertThat(result.reasonCoverageLines()).isEqualByComparingTo("0.75");
    assertThat(result.reasonCoverageValue()).isEqualByComparingTo("0.6");
    assertThat(result.reasons()).hasSize(2);
    assertThat(result.reasons().getLast().originalReasons())
        .containsExactly(" knock off ", "Knockoff");
  }

  @Test
  void noRecordsIsUnknownWhileAllMissingReasonsHasZeroCoverage() {
    var empty = WeeklyReportInventory.summarize(List.of());
    assertThat(empty.value()).isNull();
    assertThat(empty.reasonCoverageLines()).isNull();
    var missing =
        WeeklyReportInventory.summarize(
            List.of(new Waste(LocalDate.now(), " ", 1, 1, BigDecimal.TEN)));
    assertThat(missing.reasonCoverageLines()).isZero();
    assertThat(missing.reasonCoverageValue()).isZero();
    assertThat(missing.reasons().getFirst().reason()).isEqualTo("No reason recorded");
  }
}
