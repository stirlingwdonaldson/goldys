package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.canonical.CanonicalSaleItemIngest;
import com.goldys.platform.canonical.SaleItemInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PostgresContainerConfiguration.class)
class LightspeedHistoricalRepairIntegrationTest {
  @Autowired LightspeedHistoricalRepair repair;
  @Autowired LightspeedSaleItemCsvParser parser;
  @Autowired IngestionService ingestion;
  @Autowired RawLedgerQuery ledger;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @MockitoSpyBean com.goldys.platform.canonical.CanonicalSaleItemRepair factRepair;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
  @MockitoSpyBean CanonicalSaleItemIngest canonical;

  @BeforeEach
  void clean() {
    reset(canonical);
    reset(factRepair);
    jdbc.execute("TRUNCATE TABLE canonical_sale_item");
  }

  @Test
  void allFiveRealFilesPassTheBoundedContractAndPreviewWritesNothing() throws Exception {
    doAnswer(
            invocation -> {
              assertThat(jdbc.queryForObject("show transaction_read_only", String.class))
                  .isEqualTo("on");
              return invocation.callRealMethod();
            })
        .when(factRepair)
        .snapshot(any(), any(), any());
    long raws = count("raw_record"),
        stages = count("ingestion_stage"),
        runs = count("ingestion_run");
    int rows = 0;
    for (var window : LightspeedHistoricalRepair.WINDOWS.values()) {
      byte[] bytes = csv(window.day().toString());
      var preview = repair.preview(bytes);
      assertThat(preview).isEqualTo(repair.preview(bytes));
      assertThat(preview.plan().site()).isEqualTo("96181");
      assertThat(preview.plan().inserts()).isEqualTo(window.rows());
      assertThat(preview.plan().rows()).isEqualTo(window.rows());
      assertThat(preview.plan().held()).isZero();
      assertThat(preview.plan().qualifications()).isNotEmpty();
      rows += window.rows();
    }
    assertThat(rows).isEqualTo(7461);
    assertThat(count("raw_record")).isEqualTo(raws);
    assertThat(count("ingestion_stage")).isEqualTo(stages);
    assertThat(count("ingestion_run")).isEqualTo(runs);
    assertThat(count("canonical_sale_item")).isZero();
    verifyNoInteractions(canonical);
  }

  @Test
  void insertsMissingAndCorrectsOnlyVerifiedQuantityPreservingEveryOtherColumnAndHistory()
      throws Exception {
    byte[] bytes = csv("2025-12-01");
    var rows = parser.parse(bytes);
    var sauce = row(rows, LightspeedHistoricalRepair.SAUCE_ID);
    seed(sauce, "3", sauce.amount(), sauce.saleDate());
    var other =
        rows.stream()
            .filter(r -> !r.receiptLineId().equals(sauce.receiptLineId()))
            .findFirst()
            .orElseThrow();
    seed(other, other.quantity().toPlainString(), other.amount(), other.saleDate());
    jdbc.update(
        "update canonical_sale_item set sale_closed_time='18:23:45', valid_to=valid_from+interval '1 day' where source_record_ref=?",
        sauce.receiptLineId());
    UUID oldVersion = currentVersion(sauce.receiptLineId());
    String metadata = metadata(oldVersion);
    UUID otherVersion = currentVersion(other.receiptLineId());
    long versions = count("canonical_sale_item");

    var preview = repair.preview(bytes);
    assertThat(preview.plan().inserts()).isEqualTo(655);
    assertThat(preview.plan().corrections()).isEqualTo(1);
    assertThat(preview.plan().held()).isZero();
    assertThat(preview.plan().amountAfter()).isEqualByComparingTo("9549.16");
    var response =
        mvc.perform(
                multipart("/api/connectors/lightspeed/historical-repair/apply")
                    .file(new MockMultipartFile("file", "december.csv", "text/csv", bytes))
                    .param("approvedPreviewSha256", preview.sha256())
                    .with(asRole("ALL", "OWNER"))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    var result =
        mapper.readValue(
            response.getResponse().getContentAsByteArray(),
            LightspeedHistoricalRepair.Result.class);
    assertThat(result.inserted()).isEqualTo(655);
    assertThat(result.corrected()).isEqualTo(1);
    assertThat(count("canonical_sale_item")).isEqualTo(versions + 656);
    assertThat(metadata(currentVersion(sauce.receiptLineId()))).isEqualTo(metadata);
    assertThat(currentVersion(other.receiptLineId())).isEqualTo(otherVersion);
    assertThat(
            jdbc.queryForObject(
                "select superseded_at is not null from canonical_sale_item where id=?",
                Boolean.class,
                oldVersion))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select quantity_sold from canonical_sale_item where id=?",
                BigDecimal.class,
                oldVersion))
        .isEqualByComparingTo("3");
    assertThat(
            jdbc.queryForObject(
                "select quantity_sold from canonical_sale_item where source_record_ref=? and superseded_at is null",
                BigDecimal.class,
                sauce.receiptLineId()))
        .isEqualByComparingTo("1");
    assertThat(ledger.payloadBytes(result.csvRawId())).containsExactly(bytes);
    assertThat(
            jdbc.queryForObject(
                "select payload_sha256 from raw_record where id=?",
                String.class,
                result.csvRawId()))
        .isEqualTo(LightspeedHistoricalRepair.sha256(bytes));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_sale_item where superseded_at is null and sale_number is null and staff_name is null",
                Integer.class))
        .isEqualTo(655);
    long completedVersions = count("canonical_sale_item");
    var repeated = repair.preview(bytes);
    assertThat(repeated.plan().inserts()).isZero();
    assertThat(repeated.plan().corrections()).isZero();
    assertThat(repair.apply(bytes, repeated.sha256()).inserted()).isZero();
    assertThat(count("canonical_sale_item")).isEqualTo(completedVersions);
  }

  @Test
  void disagreementsOtherDatesAndAbsentRowsAreHeldAndPersistedAsUnresolved() throws Exception {
    byte[] bytes = csv("2025-12-01");
    var rows = parser.parse(bytes);
    var sauce = row(rows, LightspeedHistoricalRepair.SAUCE_ID);
    seed(sauce, "2", sauce.amount(), sauce.saleDate());
    var others =
        rows.stream()
            .filter(r -> !r.receiptLineId().equals(sauce.receiptLineId()))
            .limit(2)
            .toList();
    seed(
        others.get(0),
        others.get(0).quantity().toPlainString(),
        others.get(0).amount().add(BigDecimal.ONE),
        others.get(0).saleDate());
    seed(
        others.get(1),
        others.get(1).quantity().toPlainString(),
        others.get(1).amount(),
        LocalDate.parse("2025-12-02"));
    UUID extraRaw = raw(bytes);
    canonical.record(
        new SaleItemInput(
            "LIGHTSPEED",
            "not-in-export",
            sauce.saleDate(),
            "KEEP",
            "Keep me",
            null,
            null,
            null,
            BigDecimal.ONE,
            BigDecimal.TEN,
            null,
            null,
            null,
            null,
            null,
            "Existing staff",
            null,
            null,
            extraRaw,
            null));
    var preview = repair.preview(bytes);
    assertThat(preview.plan().held()).isEqualTo(4);
    assertThat(preview.plan().corrections()).isZero();
    var held =
        preview.plan().changes().stream()
            .filter(c -> c.action() == LightspeedHistoricalRepair.Action.HOLD)
            .toList();
    assertThat(held)
        .extracting(LightspeedHistoricalRepair.Change::reason)
        .anyMatch(r -> r.contains("ABSENT_FROM_EXPORT"));
    var result = repair.apply(bytes, preview.sha256());
    assertThat(result.corrected()).isZero();
    assertThat(result.inserted()).isEqualTo(654);
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where fetcher_identity='lightspeed-historical-repair-plan' and convert_from(payload_bytes,'UTF8')::jsonb->>'csvRawId'=?)",
                String.class,
                result.csvRawId().toString()))
        .isEqualTo("PARTIAL");
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                result.csvRawId()))
        .isEqualTo("PARTIAL");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from raw_record where fetcher_identity='lightspeed-historical-repair-plan' and convert_from(payload_bytes,'UTF8')::jsonb->'preview'->'plan'->>'exportSha256'=?",
                Integer.class,
                preview.plan().exportSha256()))
        .isPositive();
    for (var h : held) assertThat(currentVersion(h.receiptLineId())).isEqualTo(h.currentVersion());
  }

  @Test
  void changedFileOrDatabaseInvalidatesApprovalBeforeAnyEvidenceOrCanonicalWrite()
      throws Exception {
    byte[] bytes = csv("2025-12-01");
    var preview = repair.preview(bytes);
    byte[] changed = bytes.clone();
    changed[changed.length - 1] ^= 1;
    long raws = count("raw_record");
    assertThatThrownBy(() -> repair.preview(changed)).hasMessageContaining("SHA-256");
    assertThatThrownBy(() -> repair.apply(bytes, "")).hasMessageContaining("requires");
    assertThatThrownBy(() -> repair.apply(bytes, "0".repeat(64)))
        .hasMessageContaining("Preview changed");
    assertThat(count("raw_record")).isEqualTo(raws);
    var first = parser.parse(bytes).getFirst();
    seed(first, first.quantity().toPlainString(), first.amount(), first.saleDate());
    raws = count("raw_record");
    assertThatThrownBy(() -> repair.apply(bytes, preview.sha256()))
        .hasMessageContaining("Preview changed");
    var withExisting = repair.preview(bytes);
    jdbc.update(
        "update canonical_sale_item set staff_name='Changed metadata' where source_record_ref=?",
        first.receiptLineId());
    assertThatThrownBy(() -> repair.apply(bytes, withExisting.sha256()))
        .hasMessageContaining("Preview changed");
    assertThat(count("raw_record")).isEqualTo(raws);
    assertThat(count("canonical_sale_item")).isEqualTo(1);
  }

  @Test
  void failureRollsBackTheWholeDateButRetainsOriginalEvidenceAndFailureStatus() throws Exception {
    byte[] bytes = csv("2025-12-01");
    var sauce = row(parser.parse(bytes), LightspeedHistoricalRepair.SAUCE_ID);
    seed(sauce, "3", sauce.amount(), sauce.saleDate());
    var preview = repair.preview(bytes);
    var calls = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (calls.incrementAndGet() == 10)
                throw new IllegalStateException("Injected canonical failure");
              return invocation.callRealMethod();
            })
        .when(canonical)
        .recordMissing(any());
    assertThatThrownBy(() -> repair.apply(bytes, preview.sha256()))
        .hasMessageContaining("Injected canonical failure");
    assertThat(count("canonical_sale_item")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select quantity_sold from canonical_sale_item where source_record_ref=?",
                BigDecimal.class,
                sauce.receiptLineId()))
        .isEqualByComparingTo("3");
    var id =
        jdbc.queryForObject(
            "select id from raw_record where fetcher_identity='lightspeed-historical-repair' order by fetched_at desc limit 1",
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
  void httpPreviewRequiresSessionCsrfAndTheExistingWriteGrant() throws Exception {
    String path = "/api/connectors/lightspeed/historical-repair/";
    var file = new MockMultipartFile("file", "december.csv", "text/csv", csv("2025-12-01"));
    long rawBefore = count("raw_record");
    mvc.perform(multipart(path + "preview").file(file).with(csrf()))
        .andExpect(status().is3xxRedirection());
    mvc.perform(multipart(path + "preview").file(file).with(asRole("ALL", "OWNER")))
        .andExpect(status().isForbidden());
    for (String action : List.of("preview", "apply"))
      mvc.perform(
              multipart(path + action)
                  .file(file)
                  .param("approvedPreviewSha256", "0".repeat(64))
                  .with(asRole("FOH", "STAFF"))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    var response =
        mvc.perform(
                multipart(path + "preview").file(file).with(asRole("ALL", "OWNER")).with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    var preview = mapper.readTree(response.getResponse().getContentAsByteArray());
    assertThat(preview.path("sha256").asText()).hasSize(64);
    assertThat(preview.path("plan").path("inserts").asInt()).isEqualTo(657);
    mvc.perform(multipart(path + "apply").file(file).with(asRole("ALL", "OWNER")).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(path + "apply")
                .file(file)
                .param("approvedPreviewSha256", "0".repeat(64))
                .with(asRole("ALL", "OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(path + "preview")
                .file(
                    new MockMultipartFile("file", "too-large.csv", "text/csv", new byte[1_501_149]))
                .with(asRole("ALL", "OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertThat(count("raw_record")).isEqualTo(rawBefore);
    assertThat(count("canonical_sale_item")).isZero();
  }

  private RequestPostProcessor asRole(String department, String seniority) {
    var user =
        new AccountUserDetails(
            UUID.randomUUID(),
            "repair-test@example.test",
            "unused",
            "Test",
            department,
            seniority,
            true);
    return authentication(
        new UsernamePasswordAuthenticationToken(user, "unused", user.getAuthorities()));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void repairLockSerializesBothMissingKeyInsertsAndExistingRowLockHolders(boolean existing)
      throws Exception {
    var acquired = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var started = new java.util.concurrent.CountDownLatch(1);
    var first = parser.parse(csv("2025-12-01")).getFirst();
    UUID raw = raw(csv("2025-12-01"));
    if (existing) canonical.record(LightspeedSaleItemIngestService.input(first, raw));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var locked =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            factRepair.lockForRepair();
                            acquired.countDown();
                            try {
                              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("Test lock timed out");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                          }));
      try {
        assertThat(acquired.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var writer =
            pool.submit(
                () -> {
                  started.countDown();
                  canonical.record(LightspeedSaleItemIngestService.input(first, raw));
                });
        assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> writer.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
        release.countDown();
        writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
        locked.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(count("canonical_sale_item")).isEqualTo(1);
      } finally {
        release.countDown();
      }
    }
  }

  private void seed(LightspeedSaleItem row, String quantity, BigDecimal amount, LocalDate day)
      throws Exception {
    canonical.record(
        new SaleItemInput(
            "LIGHTSPEED",
            row.receiptLineId(),
            day,
            "KNOWN-RECEIPT",
            row.itemName(),
            row.productNumber(),
            row.sku(),
            row.categoryName(),
            new BigDecimal(quantity),
            amount,
            row.soldPriceIncTax(),
            row.totalTax(),
            row.costIncTax(),
            row.orderType(),
            row.saleType(),
            "Existing staff",
            row.registerName(),
            row.tableNumber(),
            raw(csv("2025-12-01")),
            row.adjustment()));
  }

  private UUID raw(byte[] bytes) {
    return ingestion.ingestPush(
        "LIGHTSPEED", "test", FetchMethod.FILE_EXPORT, "text/csv", bytes, "UTF-8", "test");
  }

  private byte[] csv(String day) throws Exception {
    return Files.readAllBytes(
        Path.of(
            "src/test/resources/fixtures/lightspeed/historical-repair/goldys-look-9501-"
                + day
                + "-unformatted.csv"));
  }

  private LightspeedSaleItem row(List<LightspeedSaleItem> rows, String id) {
    return rows.stream().filter(r -> r.receiptLineId().equals(id)).findFirst().orElseThrow();
  }

  private UUID currentVersion(String id) {
    return jdbc.queryForObject(
        "select id from canonical_sale_item where source_system='LIGHTSPEED' and source_record_ref=? and superseded_at is null",
        UUID.class,
        id);
  }

  private String metadata(UUID id) {
    return jdbc.queryForObject(
        "select (to_jsonb(s) - array['id','recorded_at','superseded_at','raw_record_id','quantity_sold'])::text from canonical_sale_item s where id=?",
        String.class,
        id);
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }
}
