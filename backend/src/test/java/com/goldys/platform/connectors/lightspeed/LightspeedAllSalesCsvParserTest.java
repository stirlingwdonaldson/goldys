package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightspeedAllSalesCsvParserTest {

  @Test
  void parsesTransactionsKeyedOnSaleClosedDate() throws Exception {
    byte[] csv =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/lightspeed/all_sales_sample.csv"));

    List<LightspeedAllSale> sales = new LightspeedAllSalesCsvParser().parse(csv);

    assertThat(sales).hasSize(4); // the trailing blank row is skipped

    assertThat(sales.get(0).saleDate()).isEqualTo(LocalDate.parse("2026-09-20"));
    assertThat(sales.get(0).totalIncTax()).isEqualByComparingTo("93.00");
    assertThat(sales.get(0).totalTax()).isEqualByComparingTo("8.45");

    // A sale with no reconciliation date is still keyed by its closed date, and "$" is stripped.
    assertThat(sales.get(3).saleDate()).isEqualTo(LocalDate.parse("2020-12-07"));
    assertThat(sales.get(3).totalIncTax()).isEqualByComparingTo("10.00");
  }

  @Test
  void failsLoudlyWhenMoneyColumnsAreMissing() {
    String csv = "Sales Data Sale Closed Date,Sales Data Total Inc Tax\n2026-09-20,10.00\n";

    assertThatThrownBy(
            () -> new LightspeedAllSalesCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Sales Data Total Tax");
  }
}
