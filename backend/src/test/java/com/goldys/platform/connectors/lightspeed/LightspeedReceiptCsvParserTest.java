package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.canonical.ReceiptImportScope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class LightspeedReceiptCsvParserTest {
  private final LightspeedReceiptCsvParser parser = new LightspeedReceiptCsvParser();
  private static final Path FIX = Path.of("src/test/resources/fixtures/lightspeed/separated-feeds");
  private static final ReceiptImportScope SCOPE =
      new ReceiptImportScope(
          96181, "Goldy’s Tavern", LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"));

  @Test
  void realSevenDayExportsHaveFullObservedMappingCoverageAndStableTransactionFacts()
      throws Exception {
    var mappings =
        parser.mappings(
            Files.readAllBytes(FIX.resolve("minimal-goldys-mapping-2026-10-26-to-11-01.csv")),
            SCOPE);
    var financial =
        new LightspeedSaleItemCsvParser()
            .parse(
                Files.readAllBytes(
                    FIX.resolve("minimal-goldys-financial-2026-10-26-to-11-01.csv")));
    assertThat(financial).hasSize(9405);
    assertThat(
            mappings.stream()
                .map(m -> m.receiptLineId())
                .collect(java.util.stream.Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(
            financial.stream().map(r -> r.receiptLineId()).toList());
    assertThat(mappings).hasSize(9405);
    assertThat(mappings.stream().map(m -> m.receiptLineId()).distinct().count()).isEqualTo(9405);
    var transactions =
        parser.transactions(
            Files.readAllBytes(FIX.resolve("minimal-sales_feed_20261102_export.csv")), SCOPE);
    assertThat(transactions).hasSize(5415);
    for (LocalDate day = SCOPE.from(); !day.isAfter(SCOPE.to()); day = day.plusDays(1)) {
      LocalDate date = day;
      BigDecimal financialGross =
          financial.stream()
              .filter(r -> r.saleDate().equals(date))
              .map(r -> r.amount())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal feedGross =
          transactions.stream()
              .filter(r -> r.sourceLocalTime().toLocalDate().equals(date))
              .map(r -> r.total())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(financialGross).isEqualByComparingTo(feedGross);
    }

    assertThat(transactions.stream().map(r -> r.total()).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("156695.57");
    for (String file : List.of("transactions-repeat-1.csv", "transactions-repeat-2.csv"))
      assertThat(parser.transactions(Files.readAllBytes(FIX.resolve(file)), SCOPE))
          .containsExactlyElementsOf(transactions);
  }

  @Test
  void rejectsWrongSiteDateConflictingTransactionsAndMerchandiseColumns() throws Exception {
    byte[] bytes = Files.readAllBytes(FIX.resolve("minimal-sales_feed_20261102_export.csv"));
    assertThatThrownBy(
            () ->
                parser.transactions(
                    bytes, new ReceiptImportScope(96181, "wrong site", SCOPE.from(), SCOPE.to())))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
    assertThatThrownBy(
            () ->
                parser.transactions(
                    bytes,
                    new ReceiptImportScope(
                        96181,
                        SCOPE.siteName(),
                        LocalDate.parse("2026-10-28"),
                        LocalDate.parse("2026-10-28"))))
        .isInstanceOf(RuntimeException.class);
    String header =
        "SaleID,SaleNo,SaleDate,SiteName,LinkedSaleID,Net Amount,Tax Amount,Tip,Total\r\n";
    String row = "one,receipt,2026-10-28 12:00:00,Goldy’s Tavern,,10,1,0,11\r\n";
    assertThatThrownBy(
            () ->
                parser.transactions(
                    (header + row + row.replace(",10,1,0,11", ",20,2,0,22"))
                        .getBytes(StandardCharsets.UTF_8),
                    SCOPE))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
    String mapping =
        "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date,Sales Data Total Inc Tax\nline,receipt,96181,2026-10-28,11\n";
    assertThatThrownBy(() -> parser.mappings(mapping.getBytes(StandardCharsets.UTF_8), SCOPE))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void acceptsQuotedMultilineOriginalExtraColumnsAndPreservesLocalTimeAndBlankNumbers() {
    String csv =
        "SaleID,SaleNo,SaleDate,SiteName,LinkedSaleID,Net Amount,Tax Amount,Tip,Total,Notes\r\n"
            + "one,,2026-10-28 12:00:00,Goldy’s Tavern,,10,1,0,11,\"two\r\nlines\"\r\n";
    var rows = parser.transactions(csv.getBytes(StandardCharsets.UTF_8), SCOPE);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().receiptNumber()).isNull();
    assertThat(rows.getFirst().sourceLocalTime().toString()).isEqualTo("2026-10-28T12:00");
  }
}
