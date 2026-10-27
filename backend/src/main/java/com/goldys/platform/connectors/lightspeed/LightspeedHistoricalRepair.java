package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalSaleItemIngest;
import com.goldys.platform.canonical.CanonicalSaleItemRepair;
import com.goldys.platform.canonical.SaleItemInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit five-file repair; preview never persists evidence or canonical facts. */
@Service
public class LightspeedHistoricalRepair {
  static final String SAUCE_ID = "27919361920005";
  static final Map<String, Window> WINDOWS =
      Map.of(
          "bd25c0ddd099c9581ff613a1869d96a4d13b2ca746e10743adf6a12e66fa6577",
              new Window(LocalDate.parse("2025-07-25"), 1548),
          "cbc553e69e4fd6cb682d8910cf978d7b1e5f6b1a824cfc2ea528262645960149",
              new Window(LocalDate.parse("2025-08-17"), 1131),
          "edd84cd94f711cbbdaf168e57f5bc35ad106186863c5f9ce347a993dcdacf656",
              new Window(LocalDate.parse("2025-11-29"), 2726),
          "4c02adedc06fa81a4559e073d7f5da251c45ab5fccf7eb1e48be735364c0fce6",
              new Window(LocalDate.parse("2025-11-30"), 1399),
          "c47a0bb2479e2970f8039a585aeeb9f693c454fd3e9e93fb5daf65dd291570e1",
              new Window(LocalDate.parse("2025-12-01"), 657));
  private static final List<String> QUALIFICATIONS =
      List.of(
          "Independent daily source completeness/control totals are unverified.",
          "Same-line multiple-adjustment semantics remain unverified; signed source amounts are retained.",
          "Inserted lines have no receipt number or staff name; no identity is guessed.",
          "Existing metadata/adjustment evidence is retained, not enriched by this repair; observations absent from the export are not deleted.");
  private final LightspeedSaleItemCsvParser parser;
  private final CanonicalSaleItemRepair canonical;
  private final CanonicalSaleItemIngest ingest;
  private final IngestionService ingestion;
  private final ObjectMapper mapper;
  private final TransactionTemplate previewTransaction;
  private final TransactionTemplate writeTransaction;
  private final TransactionTemplate evidenceTransaction;

  public LightspeedHistoricalRepair(
      LightspeedSaleItemCsvParser parser,
      CanonicalSaleItemRepair canonical,
      CanonicalSaleItemIngest ingest,
      IngestionService ingestion,
      ObjectMapper mapper,
      PlatformTransactionManager manager) {
    this.parser = parser;
    this.canonical = canonical;
    this.ingest = ingest;
    this.ingestion = ingestion;
    this.mapper = mapper;
    previewTransaction = new TransactionTemplate(manager);
    previewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    previewTransaction.setReadOnly(true);
    previewTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    writeTransaction = new TransactionTemplate(manager);
    writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    evidenceTransaction = new TransactionTemplate(manager);
    evidenceTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Preview preview(byte[] bytes) {
    Export export = load(bytes);
    return previewTransaction.execute(status -> review(export));
  }

  public Result apply(byte[] bytes, String approvedPreviewSha256) {
    if (approvedPreviewSha256 == null || !approvedPreviewSha256.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Apply requires the reviewed preview SHA-256");
    Export export = load(bytes);
    AtomicReference<UUID> rawId = new AtomicReference<>();
    AtomicReference<UUID> planRawId = new AtomicReference<>();
    try {
      return writeTransaction.execute(
          status -> {
            canonical.lockForRepair();
            Preview preview = review(export);
            if (!preview.sha256().equals(approvedPreviewSha256))
              throw new IllegalArgumentException(
                  "Preview changed; review a fresh preview before applying");
            rawId.set(
                evidenceTransaction.execute(
                    evidence ->
                        ingestion.ingestPush(
                            "LIGHTSPEED",
                            "lightspeed-historical-repair",
                            FetchMethod.FILE_EXPORT,
                            "text/csv",
                            export.bytes(),
                            "UTF-8",
                            "lightspeed-historical-repair")));
            planRawId.set(
                evidenceTransaction.execute(
                    evidence -> {
                      UUID id =
                          ingestion.ingestPush(
                              "LIGHTSPEED",
                              "lightspeed-historical-repair-plan",
                              FetchMethod.MANUAL,
                              "application/json",
                              json(Map.of("preview", preview, "csvRawId", rawId.get())),
                              "UTF-8",
                              "lightspeed-historical-repair-plan");
                      ingestion.recordStage(
                          rawId.get(), IngestionStageKind.PARSED, IngestionStageOutcome.SUCCESS);
                      return id;
                    }));
            int inserted = 0, corrected = 0;
            Map<String, LightspeedSaleItem> rows = new HashMap<>();
            export.rows().forEach(row -> rows.put(row.receiptLineId(), row));
            for (Change change : preview.plan().changes()) {
              if (change.action() == Action.INSERT) {
                if (!ingest.recordMissing(
                    LightspeedSaleItemIngestService.input(
                        rows.get(change.receiptLineId()), rawId.get())))
                  throw new IllegalStateException("Missing key changed during repair");
                inserted++;
              } else if (change.action() == Action.CORRECT_QUANTITY) {
                canonical.correctQuantity(
                    "LIGHTSPEED",
                    change.receiptLineId(),
                    change.currentVersion(),
                    new BigDecimal("3"),
                    BigDecimal.ONE,
                    rawId.get());
                corrected++;
              }
            }
            ingestion.recordStage(
                rawId.get(),
                IngestionStageKind.CANONICALIZED,
                preview.plan().held() == 0
                    ? IngestionStageOutcome.SUCCESS
                    : IngestionStageOutcome.FAILED);
            ingestion.recordStage(
                planRawId.get(),
                IngestionStageKind.CANONICALIZED,
                preview.plan().held() == 0
                    ? IngestionStageOutcome.SUCCESS
                    : IngestionStageOutcome.FAILED);
            return new Result(preview, rawId.get(), inserted, corrected);
          });
    } catch (RuntimeException e) {
      if (rawId.get() != null)
        ingestion.recordStage(
            rawId.get(), IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      if (planRawId.get() != null)
        ingestion.recordStage(
            planRawId.get(), IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
  }

  Export load(byte[] supplied) {
    if (supplied.length > 1_501_148)
      throw new IllegalArgumentException("Export exceeds the bounded repair size");
    byte[] bytes = supplied.clone();
    String hash = sha256(bytes);
    Window window = WINDOWS.get(hash);
    if (window == null)
      throw new IllegalArgumentException("Export SHA-256 is not in the five-file manifest");
    parser.requireReceiptLineGrain(bytes);
    List<LightspeedSaleItem> rows = parser.parse(bytes);
    if (rows.size() != window.rows()
        || rows.stream().map(LightspeedSaleItem::receiptLineId).distinct().count() != rows.size())
      throw new IllegalArgumentException("Export row count/identity differs from manifest");
    for (LightspeedSaleItem row : rows) {
      if (!window.day().equals(row.saleDate())
          || row.quantity() == null
          || row.amount() == null
          || row.totalTax() == null
          || row.adjustment().finalExTax() == null
          || row.amount().compareTo(row.totalTax().add(row.adjustment().finalExTax())) != 0)
        throw new IllegalArgumentException(
            "Export date or financial fields differ from the reviewed contract");
    }
    try (var csv =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      for (var row : csv)
        if (!"96181".equals(row.get("Site Numeric ID")))
          throw new IllegalArgumentException("Export contains an unapproved site");
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Cannot validate export site", e);
    }
    return new Export(hash, window.day(), bytes, rows);
  }

  private Preview review(Export export) {
    List<String> refs = export.rows().stream().map(LightspeedSaleItem::receiptLineId).toList();
    var snapshot = canonical.snapshot("LIGHTSPEED", export.day(), refs);
    String stateSha256 =
        sha256(
            json(
                snapshot.stream()
                    .sorted(Comparator.comparing(s -> s.fact().receiptLineId()))
                    .toList()));
    Map<String, CanonicalSaleItemRepair.Snapshot> current = new HashMap<>();
    snapshot.forEach(s -> current.put(s.fact().receiptLineId(), s));
    List<Change> changes = new ArrayList<>();
    BigDecimal before =
        snapshot.stream()
            .map(CanonicalSaleItemRepair.Snapshot::fact)
            .filter(s -> export.day().equals(s.tradingDate()))
            .map(SaleItemInput::amount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal after = before;
    for (LightspeedSaleItem row : export.rows()) {
      var previous = current.remove(row.receiptLineId());
      Action action;
      String reason;
      if (previous == null) {
        action = Action.INSERT;
        reason = "MISSING_SOURCE_KEY; RECEIPT_NUMBER_UNAVAILABLE; STAFF_NAME_UNAVAILABLE";
        after = after.add(row.amount());
      } else if (!sameComparableFields(previous.fact(), row)) {
        action = Action.HOLD;
        reason = "EXISTING_FACT_DIFFERS; NO_GENERAL_OVERWRITE_AUTHORIZED";
      } else if (same(previous.fact().quantitySold(), row.quantity())) {
        action = Action.UNCHANGED;
        reason = "EXISTING_FACT_RETAINED_WITH_METADATA";
      } else if (SAUCE_ID.equals(row.receiptLineId())
          && export.day().equals(LocalDate.parse("2025-12-01"))
          && "Peppercorn Sauce".equals(row.itemName())
          && row.amount().signum() == 0
          && same(previous.fact().quantitySold(), new BigDecimal("3"))
          && same(row.quantity(), BigDecimal.ONE)) {
        action = Action.CORRECT_QUANTITY;
        reason = "RECEIPT_VERIFIED_ZERO_VALUE_SAUCE_3_TO_1; OTHER_FIELDS_RETAINED";
      } else {
        action = Action.HOLD;
        reason = "UNAPPROVED_QUANTITY_DIFFERENCE";
      }
      changes.add(
          new Change(
              row.receiptLineId(),
              action,
              reason,
              previous == null ? null : previous.versionId(),
              previous == null ? null : previous.fact().quantitySold(),
              row.quantity(),
              previous == null ? null : previous.fact().amount(),
              row.amount()));
    }
    for (var extra : current.values())
      changes.add(
          new Change(
              extra.fact().receiptLineId(),
              Action.HOLD,
              "CURRENT_LINE_ABSENT_FROM_EXPORT; RETAINED_NOT_DELETED",
              extra.versionId(),
              extra.fact().quantitySold(),
              null,
              extra.fact().amount(),
              null));
    changes.sort(Comparator.comparing(Change::receiptLineId));
    int inserts = (int) changes.stream().filter(c -> c.action() == Action.INSERT).count();
    int corrections =
        (int) changes.stream().filter(c -> c.action() == Action.CORRECT_QUANTITY).count();
    int held = (int) changes.stream().filter(c -> c.action() == Action.HOLD).count();
    Plan plan =
        new Plan(
            "goldys-five-days-2026-10-27-v1",
            export.sha256(),
            stateSha256,
            "96181",
            export.day(),
            export.rows().size(),
            inserts,
            corrections,
            held,
            before,
            after,
            List.copyOf(changes),
            QUALIFICATIONS);
    return new Preview(sha256(json(plan)), plan);
  }

  private static boolean sameComparableFields(SaleItemInput old, LightspeedSaleItem row) {
    return Objects.equals(old.tradingDate(), row.saleDate())
        && Objects.equals(old.itemName(), row.itemName())
        && Objects.equals(old.productNumber(), row.productNumber())
        && Objects.equals(old.sku(), row.sku())
        && Objects.equals(old.categoryName(), row.categoryName())
        && Objects.equals(old.orderType(), row.orderType())
        && Objects.equals(old.saleType(), row.saleType())
        && Objects.equals(old.registerName(), row.registerName())
        && Objects.equals(old.tableNumber(), row.tableNumber())
        && same(old.amount(), row.amount())
        && same(old.totalTax(), row.totalTax())
        && same(old.soldPriceIncTax(), row.soldPriceIncTax())
        && same(old.costIncTax(), row.costIncTax());
  }

  private static boolean same(BigDecimal a, BigDecimal b) {
    return a == null ? b == null : b != null && a.compareTo(b) == 0;
  }

  private byte[] json(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize repair preview", e);
    }
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public enum Action {
    INSERT,
    UNCHANGED,
    CORRECT_QUANTITY,
    HOLD
  }

  record Window(LocalDate day, int rows) {}

  record Export(String sha256, LocalDate day, byte[] bytes, List<LightspeedSaleItem> rows) {}

  public record Change(
      String receiptLineId,
      Action action,
      String reason,
      UUID currentVersion,
      BigDecimal oldQuantity,
      BigDecimal candidateQuantity,
      BigDecimal oldAmount,
      BigDecimal candidateAmount) {}

  public record Plan(
      String policy,
      String exportSha256,
      String stateSha256,
      String site,
      LocalDate day,
      int rows,
      int inserts,
      int corrections,
      int held,
      BigDecimal amountBefore,
      BigDecimal amountAfter,
      List<Change> changes,
      List<String> qualifications) {}

  public record Preview(String sha256, Plan plan) {}

  public record Result(Preview preview, UUID csvRawId, int inserted, int corrected) {}
}
