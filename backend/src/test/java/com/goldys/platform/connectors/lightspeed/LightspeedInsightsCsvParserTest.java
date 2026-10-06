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
    assertThat(sales.get(0).saleDate()).isEqualTo(LocalDate.parse("2026-09-20"));
    assertThat(sales.get(0).saleNumber()).isEqualTo("SP-56 0920042116");
    assertThat(sales.get(0).totalIncTax()).isEqualByComparingTo("16.00");
  }

  @Test
  void skipsDimensionFillContinuationRowsWithBlankDates() {
    // Looker "dimension fill": repeated dimension values (Sale Opened Date, Sale Number) are blank
    // on continuation rows, which repeat the measure values. These must be skipped, not treated as
    // extra sales (which would double-count) nor allowed to abort the parse with a bad-date error.
    String csv =
        ",Reconciliation Date,Sale Opened Date,Sale Type,Sale Number,Order Type,Total Tax,Total Inc Tax\n"
            + "1,2026-10-05,2026-10-03,Sale,SP-4 1003022358,Unspecified,$80.63,$885.00\n"
            + ",,,,,,$80.63,$885.00\n";

    List<LightspeedInsightsSale> sales =
        new LightspeedInsightsCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(sales).hasSize(1);
    assertThat(sales.get(0).saleNumber()).isEqualTo("SP-4 1003022358");
    assertThat(sales.get(0).totalIncTax()).isEqualByComparingTo("885.00");
  }
}
