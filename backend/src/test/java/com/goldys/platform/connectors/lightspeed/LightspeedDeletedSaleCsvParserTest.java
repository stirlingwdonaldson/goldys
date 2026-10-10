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

class LightspeedDeletedSaleCsvParserTest {

  @Test
  void parsesRowsByHeaderName() throws Exception {
    byte[] csv =
        Files.readAllBytes(
            Path.of("src/test/resources/fixtures/lightspeed/deleted_sales_sample.csv"));

    List<LightspeedDeletedSale> sales = new LightspeedDeletedSaleCsvParser().parse(csv);

    assertThat(sales).hasSize(3);
    LightspeedDeletedSale first = sales.get(0);
    assertThat(first.saleOpenedDate()).isEqualTo(LocalDate.parse("2026-10-09"));
    assertThat(first.saleNumber()).isEqualTo("SP-6 1009105801");
    assertThat(first.note()).isEqualTo("Test");
    assertThat(first.totalIncTax()).isEqualByComparingTo("12");
    assertThat(first.totalExTax()).isEqualByComparingTo("10.91");
    assertThat(first.totalTax()).isEqualByComparingTo("1.09");
    assertThat(first.openedRegisterName()).isEqualTo("Functions POS");
    assertThat(first.deletedByStaffName()).isEqualTo("Example Staff");

    // Third row carries a customer and no note.
    LightspeedDeletedSale third = sales.get(2);
    assertThat(third.note()).isNull();
    assertThat(third.customerName()).isEqualTo("Example Customer");
  }

  @Test
  void failsOnMissingRequiredColumn() {
    String csv =
        "Deleted Orders Order Opened Date,Deleted Orders Order Type\n" + "2026-10-09,Unspecified\n";

    assertThatThrownBy(
            () -> new LightspeedDeletedSaleCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Sales Data Sale Number");
  }
}
