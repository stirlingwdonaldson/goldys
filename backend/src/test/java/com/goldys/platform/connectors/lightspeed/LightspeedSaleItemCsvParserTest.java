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

class LightspeedSaleItemCsvParserTest {

  @Test
  void parsesRowsByHeaderName() throws Exception {
    byte[] csv =
        Files.readAllBytes(Path.of("src/test/resources/fixtures/lightspeed/sale_items_sample.csv"));

    List<LightspeedSaleItem> items = new LightspeedSaleItemCsvParser().parse(csv);

    assertThat(items).hasSize(3);
    LightspeedSaleItem first = items.get(0);
    assertThat(first.receiptLineId()).isEqualTo("LI-1");
    assertThat(first.saleDate()).isEqualTo(LocalDate.parse("2026-09-19"));
    assertThat(first.itemName()).isEqualTo("Burger");
    assertThat(first.quantity()).isEqualByComparingTo("1");
    assertThat(first.amount()).isEqualByComparingTo("22.00");
    assertThat(first.soldPriceIncTax()).isEqualByComparingTo("22.00");
    assertThat(first.totalTax()).isEqualByComparingTo("2.00");
    assertThat(first.registerName()).isEqualTo("Goldy's Main Bar");

    // Nullable fields are preserved or nulled correctly.
    LightspeedSaleItem third = items.get(2);
    assertThat(third.sku()).isNull();
    assertThat(third.costIncTax()).isNull();
    assertThat(third.saleType()).isEqualTo("Refund");
  }

  @Test
  void failsOnMissingRequiredColumn() {
    String csv = "Sales Data Receipt Line ID,Sales Data Sale Type\n" + "LI-1,Sale\n";

    assertThatThrownBy(
            () -> new LightspeedSaleItemCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("Sales Data Sale Closed Date");
  }

  @Test
  void parsesFractionalQuantity() {
    String csv =
        "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Sales Data Product Quantity\n"
            + "LI-1,2026-09-19,0.5\n";

    List<LightspeedSaleItem> items =
        new LightspeedSaleItemCsvParser().parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(items.get(0).quantity()).isEqualByComparingTo("0.5");
  }

  @Test
  void retainsRealAdjustmentsAndTenderJoinEvidence() throws Exception {
    var items =
        new LightspeedSaleItemCsvParser()
            .parse(
                Files.readAllBytes(
                    Path.of("src/test/resources/fixtures/lightspeed/sale_costing_real.csv")));
    assertThat(items).hasSize(9);
    assertThat(items.stream().map(LightspeedSaleItem::receiptLineId).distinct()).hasSize(7);
    var steak =
        items.stream()
            .filter(i -> i.receiptLineId().equals("31036767650001"))
            .findFirst()
            .orElseThrow();
    assertThat(steak.itemName()).isEqualTo("$28 Steak Frites");
    assertThat(steak.adjustment().amountIncTax()).isZero();
    assertThat(steak.costIncTax()).isZero();
    var comp =
        items.stream()
            .filter(i -> i.receiptLineId().equals("31036049020002"))
            .findFirst()
            .orElseThrow();
    assertThat(comp.amount()).isZero();
    assertThat(comp.adjustment().grossIncTax()).isEqualByComparingTo("10");
    assertThat(comp.adjustment().amountIncTax()).isEqualByComparingTo("-10");
    assertThat(comp.adjustment().reason()).isEqualTo("Testing/Manager photos");
    var spritz =
        items.stream()
            .filter(i -> i.receiptLineId().equals("31036797080003"))
            .findFirst()
            .orElseThrow();
    assertThat(spritz.quantity()).isEqualByComparingTo("2");
    assertThat(spritz.soldPriceIncTax()).isEqualByComparingTo("36");
    assertThat(spritz.amount()).isEqualByComparingTo("36");
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
      named = "LIGHTSPEED_EVIDENCE_CSV",
      matches = ".+")
  void measuresRetainedReportCoverage() throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(System.getenv("LIGHTSPEED_EVIDENCE_CSV")));
    var rows =
        org.apache.commons.csv.CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)))
            .getRecords();
    long distinct = rows.stream().map(r -> r.get("Sales Data Receipt Line ID")).distinct().count();
    var parsed = new LightspeedSaleItemCsvParser().parse(bytes);
    assertThat(parsed).hasSize(rows.size());
    assertThat(parsed.stream().map(LightspeedSaleItem::receiptLineId).distinct())
        .hasSize((int) distinct);
    assertThat(parsed).allSatisfy(i -> assertThat(i.adjustment()).isNotNull());
    System.out.println(
        "Real-report coverage: "
            + rows.size()
            + " rows -> "
            + distinct
            + " distinct receipt lines; zero failures");
  }
}
