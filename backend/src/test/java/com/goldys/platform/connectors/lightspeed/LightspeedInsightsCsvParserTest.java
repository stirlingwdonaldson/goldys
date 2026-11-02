package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LightspeedInsightsCsvParserTest {
  static final Path FIX = Path.of("src/test/resources/fixtures/lightspeed");
  static final String HEADER =
      "Sales Data Sale Closed Date,Site Numeric ID,Sales Data Total Ex Tax,Sales Data Total Inc Tax,Sales Data Total Tax,Sales Data Total Adjustment Inc Tax\n";
  private final LightspeedInsightsCsvParser parser = new LightspeedInsightsCsvParser();

  @Test
  void parsesSevenRealClosedDatesAndPreservesSeparateAdjustment() throws Exception {
    var sales = parser.parse(Files.readAllBytes(FIX.resolve("daily-sales-closed-date.csv")));
    assertThat(sales).hasSize(7);
    var oct28 =
        sales.stream()
            .filter(s -> s.saleClosedDate().equals(LocalDate.parse("2026-10-28")))
            .findFirst()
            .orElseThrow();
    assertThat(oct28.netTotal()).isEqualByComparingTo("18269.42");
    assertThat(oct28.totalTax()).isEqualByComparingTo("1816.62");
    assertThat(oct28.totalIncTax()).isEqualByComparingTo("20086.04");
    assertThat(oct28.totalAdjustmentIncTax()).isEqualByComparingTo("-61.46");
    assertThat(
            sales.stream()
                .map(LightspeedInsightsSale::totalIncTax)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
        .isEqualByComparingTo("156695.57");
  }

  @Test
  void rejectsTheRealLegacyReport() throws Exception {
    assertThatThrownBy(
            () -> parser.parse(Files.readAllBytes(FIX.resolve("insights_sales_sample.csv"))))
        .hasFieldOrPropertyWithValue("failureType", "UNSAFE_DAILY_SALES_BASIS");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-10-28,96182,10,11,1,0\n",
        "2026-10-28,not-site,10,11,1,0\n",
        "2026-02-30,96181,10,11,1,0\n",
        ",96181,10,11,1,0\n",
        "2026-10-28,96181,,11,1,0\n",
        "2026-10-28,96181,10,12,1,0\n",
        "2026-10-28,96181,10,11,1,\n",
        "2026-10-28,96181,NaN,11,1,0\n",
        "2026-10-28,96181,10000000000,10000000000,0,0\n",
        "2026-10-28,96181,10.00001,11.00001,1,0\n",
        "2026-10-28,96181,10,11,1,0,extra\n",
        "2026-10-28,96181,10,11,1\n",
        "2026-10-28,96181,10,11,1,0\n2026-10-28,96181,10,11,1,0\n"
      })
  void rejectsUnsafeRows(String rows) {
    assertMismatch((HEADER + rows).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsHeaderErrorsMalformedCsvAndInvalidUtf8() {
    for (String header :
        new String[] {
          HEADER.replace("Site Numeric ID", "Sales Data Sale Closed Date"),
          HEADER.replace("Site Numeric ID", "Extra Site"),
          "," + HEADER
        }) assertMismatch(header.getBytes(StandardCharsets.UTF_8));
    assertMismatch(new byte[] {(byte) 0xc3, (byte) 0x28});
    assertMismatch((HEADER + "\"unclosed").getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void supportsSignedZeroAndUnformattedValuesWithoutInventingEmptyDays() {
    assertThat(parser.parse(HEADER.getBytes(StandardCharsets.UTF_8))).isEmpty();
    var sales =
        parser.parse(
            (HEADER + "2026-10-28,96181,-10,-11,-1,-2\n2026-10-29,96181,0,0,0,0\n")
                .getBytes(StandardCharsets.UTF_8));
    assertThat(sales.getFirst().totalIncTax()).isEqualByComparingTo("-11");
    assertThat(sales.getLast().netTotal()).isZero();
  }

  @Test
  void everyDayMatchesTheIndependentSalesFeedNetTaxAndGross() throws Exception {
    try (var reader =
            Files.newBufferedReader(
                FIX.resolve("separated-feeds/minimal-sales_feed_20261102_export.csv"));
        var csv =
            org.apache.commons.csv.CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader)) {
      var transactions = csv.getRecords();
      for (var sale :
          parser.parse(Files.readAllBytes(FIX.resolve("daily-sales-closed-date.csv")))) {
        var day =
            transactions.stream()
                .filter(r -> r.get("SaleDate").startsWith(sale.saleClosedDate().toString()))
                .toList();
        assertThat(day).isNotEmpty();
        assertThat(sale.netTotal())
            .isEqualByComparingTo(
                day.stream()
                    .map(r -> new java.math.BigDecimal(r.get("Net Amount")))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        assertThat(sale.totalTax())
            .isEqualByComparingTo(
                day.stream()
                    .map(r -> new java.math.BigDecimal(r.get("Tax Amount")))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        assertThat(sale.totalIncTax())
            .isEqualByComparingTo(
                day.stream()
                    .map(r -> new java.math.BigDecimal(r.get("Total")))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
      }
    }
  }

  private void assertMismatch(byte[] csv) {
    assertThatThrownBy(() -> parser.parse(csv))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
  }
}
