package com.goldys.platform.connectors.ctb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.SourceProductSaleFactInput;
import com.goldys.platform.canonical.SourceProductSaleFactRepair;
import com.goldys.platform.ingestion.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit retained-record recovery; never runs at startup or re-ingests product/day totals. */
@Service
public class CtbProductDetailRecovery {
  private final RawLedgerQuery ledger;
  private final CtbSaleItemParser parser;
  private final CanonicalProductSalesQuery products;
  private final SourceProductSaleFactRepair facts;
  private final IngestionService ingestion;
  private final ObjectMapper mapper;
  private final TransactionTemplate read;
  private final TransactionTemplate write;
  private final TransactionTemplate evidence;

  public CtbProductDetailRecovery(
      RawLedgerQuery ledger,
      CtbSaleItemParser parser,
      CanonicalProductSalesQuery products,
      SourceProductSaleFactRepair facts,
      IngestionService ingestion,
      ObjectMapper mapper,
      PlatformTransactionManager transactions) {
    this.ledger = ledger;
    this.parser = parser;
    this.products = products;
    this.facts = facts;
    this.ingestion = ingestion;
    this.mapper = mapper;
    read = new TransactionTemplate(transactions);
    read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    read.setReadOnly(true);
    read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    write = new TransactionTemplate(transactions);
    write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    evidence = new TransactionTemplate(transactions);
    evidence.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Preview preview(Request request) {
    return read.execute(status -> review(request, load(request)));
  }

  public Result apply(Request request, String approvedPreviewSha256) {
    requireDigest(approvedPreviewSha256);
    var planId = new AtomicReference<UUID>();
    try {
      return write.execute(
          status -> {
            var inputs = load(request);
            facts.lock(inputs.getFirst().tradingDate());
            var preview = review(request, inputs);
            if (!preview.sha256().equals(approvedPreviewSha256))
              throw new IllegalArgumentException(
                  "Recovery preview changed; review a fresh preview");
            if (preview.plan().snapshot().state() == SourceProductSaleFactRepair.State.HELD)
              throw new IllegalArgumentException("Existing CTB detail differs; recovery is held");
            planId.set(
                evidence.execute(
                    tx ->
                        ingestion.ingestPush(
                            "CTB",
                            "ctb-detail-recovery-plan",
                            FetchMethod.MANUAL,
                            "application/json",
                            json(preview),
                            "UTF-8",
                            "ctb-detail-recovery-plan")));
            int inserted = facts.restore(inputs);
            ingestion.recordStage(
                planId.get(), IngestionStageKind.CANONICALIZED, IngestionStageOutcome.SUCCESS);
            return new Result(preview, planId.get(), inserted);
          });
    } catch (RuntimeException failure) {
      if (planId.get() != null)
        evidence.executeWithoutResult(
            tx ->
                ingestion.recordStage(
                    planId.get(), IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED));
      throw failure;
    }
  }

  private List<SourceProductSaleFactInput> load(Request request) {
    var ref =
        ledger.rawRecordRefsForFetcher("CTB", "ctb-revenue").stream()
            .filter(r -> r.id().equals(request.rawRecordId()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Expected a retained CTB revenue record"));
    byte[] bytes = ledger.payloadBytes(ref.id());
    if (bytes == null || bytes.length > 2_000_000 || !sha256(bytes).equals(request.payloadSha256()))
      throw new IllegalArgumentException("Retained payload size or checksum does not match");
    // shortcut: one complete envelope only; multi-page recovery needs durable report membership.
    if (!parser.isSaleItemEnvelope(bytes) || !parser.isCompleteReport(bytes))
      throw new IllegalArgumentException("Expected a complete single-page sale-item report");
    LocalDate day = tradingDate(ref);
    var rows = parser.parse(bytes);
    if (rows.isEmpty() || rows.size() > 1000)
      throw new IllegalArgumentException("Report row limit exceeded");
    var batch = new CtbProductSalesBatch(day);
    rows.forEach(row -> batch.add(row, ref.id()));
    if (batch.rowCount() != rows.size())
      throw new IllegalArgumentException("Duplicate report rows require review");
    var inputs =
        batch.sourceFacts().stream().map(CtbProductDetailRecovery::storedPrecision).toList();
    if (inputs.isEmpty()
        || inputs.stream().map(SourceProductSaleFactInput::sourceRecordRef).distinct().count()
            != inputs.size())
      throw new IllegalArgumentException("Report identities are not unique");
    return inputs;
  }

  // Match the existing numeric(14,4) contract explicitly. The original bytes retain full precision.
  private static SourceProductSaleFactInput storedPrecision(SourceProductSaleFactInput i) {
    return new SourceProductSaleFactInput(
        i.sourceSystem(),
        i.sourceRecordRef(),
        i.tradingDate(),
        i.stockCode(),
        fourDecimals(i.costPerUnit()),
        fourDecimals(i.foodCost()),
        fourDecimals(i.totalFoodCost()),
        fourDecimals(i.profit()),
        fourDecimals(i.discountedAmount()),
        fourDecimals(i.taxAmount()),
        fourDecimals(i.unitPrice()),
        i.uniqueGroupId(),
        i.rawRecordId(),
        fourDecimals(i.quantitySold()),
        fourDecimals(i.amount()),
        i.productName(),
        i.siteNumber());
  }

  private static BigDecimal fourDecimals(BigDecimal value) {
    return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
  }

  private LocalDate tradingDate(RawLedgerQuery.RawRecordRef ref) {
    var dates = products.tradingDatesForRawRecord(ref.id());
    LocalDate day;
    if (ref.sourceRecordRef() != null) {
      if (!ref.sourceRecordRef().startsWith("sale-items:"))
        throw new IllegalArgumentException("Unexpected CTB report reference");
      day = LocalDate.parse(ref.sourceRecordRef().substring("sale-items:".length()));
      if (dates.stream().anyMatch(d -> !d.equals(day)))
        throw new IllegalArgumentException("Conflicting durable trading dates");
    } else {
      if (dates.size() != 1)
        throw new IllegalArgumentException("No unambiguous durable trading date");
      day = dates.getFirst();
    }
    return day;
  }

  private Preview review(Request request, List<SourceProductSaleFactInput> inputs) {
    var plan =
        new Plan(
            "ctb-detail-recovery-v1",
            request,
            sha256(json(inputs)),
            "Existing numeric(14,4) storage: round half away from zero; original raw bytes retain full precision.",
            inputs.getFirst().tradingDate(),
            inputs.size(),
            inputs.stream()
                .map(SourceProductSaleFactInput::quantitySold)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            inputs.stream()
                .map(SourceProductSaleFactInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            facts.snapshot(inputs));
    return new Preview(sha256(json(plan)), plan);
  }

  private byte[] json(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode recovery plan", e);
    }
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void requireDigest(String digest) {
    if (digest == null || !digest.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Expected SHA-256 digest");
  }

  public record Request(UUID rawRecordId, String payloadSha256) {
    public Request {
      if (rawRecordId == null) throw new IllegalArgumentException("Raw record is required");
      requireDigest(payloadSha256);
    }
  }

  public record Plan(
      String version,
      Request request,
      String factsSha256,
      String storageBasis,
      LocalDate day,
      int facts,
      BigDecimal quantity,
      BigDecimal amount,
      SourceProductSaleFactRepair.Snapshot snapshot) {}

  public record Preview(String sha256, Plan plan) {}

  public record Result(Preview preview, UUID planRawRecordId, int inserted) {}
}
