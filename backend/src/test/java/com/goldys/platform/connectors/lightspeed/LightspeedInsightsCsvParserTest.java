package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;

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
}
