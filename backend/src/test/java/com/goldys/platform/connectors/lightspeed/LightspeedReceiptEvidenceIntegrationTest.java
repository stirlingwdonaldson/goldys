package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.canonical.ReceiptImportScope;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LightspeedReceiptEvidenceIntegrationTest {
  @Autowired LightspeedReceiptEvidenceIngestService service;
  @Autowired LightspeedSaleItemIngestService financial;
  @Autowired ReceiptGroupQuery groups;
  @Autowired SaleItemListQuery items;
  @Autowired SaleCostingQuery costs;
  @Autowired RawLedgerQuery ledger;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired com.goldys.platform.reconciliation.ReceiptEvidenceProjector projector;
  private static final Path FIX = Path.of("src/test/resources/fixtures/lightspeed/separated-feeds");
  private static final ReceiptImportScope SCOPE =
      new ReceiptImportScope(
          96181, "Goldy’s Tavern", LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"));

  @BeforeEach
  void clean() {
    jdbc.update("truncate canonical_receipt_evidence_batch,canonical_sale_item");
    projector.recomputeAll();
  }

  @Test
  void realFinancialMappingAndTransactionImportsRemainSeparateAndDetectAmbiguity()
      throws Exception {
    byte[] money =
        Files.readAllBytes(FIX.resolve("minimal-goldys-financial-2026-10-26-to-11-01.csv"));
    financial.ingestCsv(money);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from resolved_sale_line where receipt_association='MAPPING_MISSING'",
                Long.class))
        .isEqualTo(9405);
    byte[] mapping =
        Files.readAllBytes(FIX.resolve("minimal-goldys-mapping-2026-10-26-to-11-01.csv"));
    var receipt = service.ingest(mapping, SCOPE, true);
    assertThat(ledger.payloadBytes(receipt.rawId())).containsExactly(mapping);
    assertThat(receipt.distinctObservations()).isEqualTo(9405);
    assertThat(jdbc.queryForObject("select count(*) from resolved_receipt_mapping", Long.class))
        .isEqualTo(9405);
    var tx =
        service.ingest(
            Files.readAllBytes(FIX.resolve("minimal-sales_feed_20261102_export.csv")),
            SCOPE,
            false);
    assertThat(tx.distinctObservations()).isEqualTo(5415);
    String number = "110032089 1028084915";
    var group = groups.group(number, SCOPE.from(), SCOPE.to());
    assertThat(group.association()).isEqualTo("AMBIGUOUS_TRANSACTION");
    assertThat(group.lineCount()).isEqualTo(5);
    assertThat(group.transactions())
        .extracting(ReceiptGroup.Transaction::transactionId)
        .containsExactly("3110032089", "3110061723");
    assertThat(items.page(null, number, SCOPE.from(), SCOPE.to(), 0, 200).total()).isEqualTo(5);
    assertThat(costs.costing(number, SCOPE.from(), SCOPE.to()).recipeCost()).isNull();
    assertThat(costs.costing(number, SCOPE.from(), SCOPE.to()).qualification())
        .isEqualTo("AMBIGUOUS_TRANSACTION");
    assertThat(
            jdbc.queryForObject(
                "select sum(amount) from resolved_sale_line", java.math.BigDecimal.class))
        .isEqualByComparingTo("156695.57");
    service.ingest(mapping, SCOPE, true);
    assertThat(groups.group(number, SCOPE.from(), SCOPE.to()).lineCount()).isEqualTo(5);
  }

  @Test
  void omissionsPreserveMappingsConflictsAreHeldAndLateTransactionsReevaluateGroups() {
    String header =
        "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date\n";
    service.ingest(
        (header + "line,receipt,96181,2026-10-28\n").getBytes(StandardCharsets.UTF_8), SCOPE, true);
    service.ingest(header.getBytes(StandardCharsets.UTF_8), SCOPE, true);
    assertThat(
            jdbc.queryForObject(
                "select receipt_number from resolved_receipt_mapping where receipt_line_id='line'",
                String.class))
        .isEqualTo("receipt");
    service.ingest(
        (header + "line,other,96181,2026-10-28\n").getBytes(StandardCharsets.UTF_8), SCOPE, true);
    assertThat(
            jdbc.queryForObject(
                "select receipt_number_count from resolved_receipt_mapping where receipt_line_id='line'",
                Long.class))
        .isEqualTo(2);
    String th = "SaleID,SaleNo,SaleDate,SiteName,LinkedSaleID,Net Amount,Tax Amount,Tip,Total\n";
    service.ingest(
        (th + "sale,receipt,2026-10-28 12:00:00,Goldy’s Tavern,,10,1,0,11\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        false);
    assertThat(groups.group("receipt", SCOPE.from(), SCOPE.to()).association())
        .isEqualTo("TRANSACTION_IDENTITY_UNVERIFIED");
    service.ingest(
        (th + "refund,receipt,2026-11-01 12:00:00,Goldy’s Tavern,sale,-10,-1,0,-11\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        false);
    assertThat(
            groups
                .group("receipt", LocalDate.parse("2026-10-28"), LocalDate.parse("2026-10-28"))
                .association())
        .isEqualTo("AMBIGUOUS_TRANSACTION");
  }

  @Test
  void malformedImportRetainsRawFailureButPublishesNoEvidence() {
    byte[] bytes = "bad header\n".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> service.ingest(bytes, SCOPE, false))
        .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_SCHEMA_MISMATCH");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_receipt_evidence_batch", Long.class))
        .isZero();
    UUID id =
        jdbc.queryForObject(
            "select id from raw_record where fetcher_identity='lightspeed-sales-feed' order by fetched_at desc limit 1",
            UUID.class);
    assertThat(ledger.payloadBytes(id)).containsExactly(bytes);
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                id))
        .isEqualTo("PARTIAL");
  }

  @Test
  void conflictingMappingsStaySearchableButNeverProduceTransactionCosting() {
    String money =
        "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Site Numeric ID,Sales Data Product Quantity,Sales Data Total Inc Tax,Sales Data Total Tax\nheld-line,2026-10-28,96181,1,11,1\n";
    financial.ingestCsv(money.getBytes(StandardCharsets.UTF_8));
    String header =
        "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date\n";
    service.ingest(
        (header + "held-line,first,96181,2026-10-28\nheld-line,second,96181,2026-10-28\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        true);
    var page = items.page(null, "first", SCOPE.from(), SCOPE.to(), 0, 200);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().getFirst().saleNumber()).isNull();
    assertThat(page.items().getFirst().receiptAssociation()).isEqualTo("MAPPING_CONFLICT");
    assertThat(groups.group("first", SCOPE.from(), SCOPE.to()).association())
        .isEqualTo("MAPPING_CONFLICT");
    assertThat(costs.costing("first", SCOPE.from(), SCOPE.to()).recipeCost()).isNull();
  }

  @Test
  void changingTransactionClaimsAreHeldRatherThanSilentlyChoosingTheLatest() {
    String header =
        "SaleID,SaleNo,SaleDate,SiteName,LinkedSaleID,Net Amount,Tax Amount,Tip,Total\n";
    service.ingest(
        (header + "one,number,2026-10-28 12:00:00,Goldy’s Tavern,,10,1,0,11\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        false);
    service.ingest(
        (header + "one,number,2026-10-28 12:00:00,Goldy’s Tavern,,20,2,0,22\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        false);
    assertThat(groups.group("number", SCOPE.from(), SCOPE.to()).association())
        .isEqualTo("TRANSACTION_CONFLICT");
    service.ingest(header.getBytes(StandardCharsets.UTF_8), SCOPE, false);
    assertThat(groups.group("number", SCOPE.from(), SCOPE.to()).transactions()).hasSize(2);
    service.ingest(
        (header + "one,renumbered,2026-10-28 12:00:00,Goldy’s Tavern,,10,1,0,11\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        false);
    assertThat(groups.group("renumbered", SCOPE.from(), SCOPE.to()).association())
        .isEqualTo("TRANSACTION_CONFLICT");
  }

  @Test
  void mappingWebhookRetainsEnvelopeAndScopesOnlyTheObservedRows() throws Exception {
    String csv =
        "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date\nwebhook-line,number,96181,2026-10-28\n";
    byte[] body =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
    var result = service.ingestMappingWebhook(body, 96181, "Goldy’s Tavern");
    assertThat(ledger.payloadBytes(result.rawId())).containsExactly(body);
    assertThat(result.distinctObservations()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select window_from from canonical_receipt_evidence_batch where raw_record_id=?",
                LocalDate.class,
                result.rawId()))
        .isEqualTo(LocalDate.parse("2026-10-28"));
  }

  @Test
  void mappingsNeverCrossSitesAndConflictingDatesAreHeld() {
    String money =
        "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Site Numeric ID,Sales Data Product Quantity,Sales Data Total Inc Tax,Sales Data Total Tax\nscoped-line,2026-10-28,96181,1,11,1\n";
    financial.ingestCsv(money.getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(
            () ->
                financial.ingestCsv(
                    money.replace("96181", "96182").getBytes(StandardCharsets.UTF_8)))
        .hasMessageContaining("Conflicting site observations");
    financial.ingestCsv(
        money
            .replace(",Site Numeric ID", "")
            .replace(",96181", "")
            .getBytes(StandardCharsets.UTF_8));
    assertThat(
            items
                .page(null, null, SCOPE.from(), SCOPE.to(), 0, 200)
                .items()
                .getFirst()
                .siteNumber())
        .isEqualTo(96181L);

    String header =
        "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date\n";
    service.ingest(
        (header + "scoped-line,receipt,96182,2026-10-28\n").getBytes(StandardCharsets.UTF_8),
        new ReceiptImportScope(96182, "Other site", SCOPE.from(), SCOPE.to()),
        true);
    assertThat(
            items
                .page(null, null, SCOPE.from(), SCOPE.to(), 0, 200)
                .items()
                .getFirst()
                .receiptAssociation())
        .isEqualTo("MAPPING_MISSING");
    service.ingest(
        (header + "scoped-line,receipt,96181,2026-10-28\nscoped-line,receipt,96181,2026-10-29\n")
            .getBytes(StandardCharsets.UTF_8),
        SCOPE,
        true);
    assertThat(
            items
                .page(null, "receipt", SCOPE.from(), SCOPE.to(), 0, 200)
                .items()
                .getFirst()
                .receiptAssociation())
        .isEqualTo("MAPPING_CONFLICT");
  }

  @Test
  void importsDoNotWaitForReadersAndReadersKeepOneProjectionSnapshot() throws Exception {
    var readStarted = new java.util.concurrent.CountDownLatch(1);
    var imported = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var future =
          executor.submit(
              () -> {
                var transaction =
                    new org.springframework.transaction.support.TransactionTemplate(
                        transactionManager);
                transaction.setReadOnly(true);
                transaction.setIsolationLevel(
                    org.springframework.transaction.TransactionDefinition
                        .ISOLATION_REPEATABLE_READ);
                transaction.executeWithoutResult(
                    status -> {
                      assertThat(
                              jdbc.queryForObject(
                                  "select count(*) from resolved_sale_transaction", Long.class))
                          .isZero();
                      readStarted.countDown();
                      try {
                        assertThat(imported.await(10, java.util.concurrent.TimeUnit.SECONDS))
                            .isTrue();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                      }
                      assertThat(
                              jdbc.queryForObject(
                                  "select count(*) from resolved_receipt_mapping_claim",
                                  Long.class))
                          .isZero();
                    });
              });
      assertThat(readStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      String csv =
          "Sales Data Receipt Line ID,Saleline Payments Sale Number,Site Numeric ID,Sales Data Sale Closed Date\nreader-line,receipt,96181,2026-10-28\n";
      try {
        service.ingest(csv.getBytes(StandardCharsets.UTF_8), SCOPE, true);
      } finally {
        imported.countDown();
      }
      future.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject("select count(*) from resolved_receipt_mapping_claim", Long.class))
        .isEqualTo(1);
  }
}
