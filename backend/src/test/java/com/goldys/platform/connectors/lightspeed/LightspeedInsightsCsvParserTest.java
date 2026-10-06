package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightspeedInsightsCsvParserTest {

  @Test
  void parsesRowsStrippingRowNumbersAndCurrency() throws Exception {
    byte[] csv =
        Files.readAllBytes(
            Path.of("src/test/resources/fixtures/lightspeed/insights_sales_sample.csv"));

    List<LightspeedInsightsSale> sales = new LightspeedInsightsCsvParser().parse(csv);

    assertThat(sales).hasSize(4);
    assertThat(sales.get(0).reconciliationDate()).isEqualTo(LocalDate.parse("2026-09-20"));
    assertThat(sales.get(0).totalIncTax()).isEqualByComparingTo("16.00");
    // Currency formatting: thousands comma stripped; negative adjustment preserved.
    assertThat(sales.get(2).totalIncTax()).isEqualByComparingTo("1089.96");
    assertThat(sales.get(2).totalAdjustmentIncTax()).isEqualByComparingTo("-1.46");
  }

  @Test
  void skipsTotalsRowWithBlankReconciliationDate() {
    String csv =
        ",Reconciliation Date,Total Tax,Total Inc Tax\n"
            + "1,2026-10-05,$80.63,$885.00\n"
            + ",,$80.63,$885.00\n";

    List<LightspeedInsightsSale> sales =
        new LightspeedInsightsCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(sales).hasSize(1);
    assertThat(sales.get(0).reconciliationDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(sales.get(0).totalIncTax()).isEqualByComparingTo("885.00");
  }
}
