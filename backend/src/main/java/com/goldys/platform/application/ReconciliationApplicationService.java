package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.OverrideAuditService;
import com.goldys.platform.reconciliation.ProductSalesExceptionQuery;
import com.goldys.platform.reconciliation.ProductSalesOverrideService;
import com.goldys.platform.reconciliation.ReconciliationExceptionQuery;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The reconciliation screen's read model and manual-override actions. Read use cases are authorized
 * here; override writes delegate authorization to the override services, which enforce WRITE at the
 * mutation boundary. Per-source values are provenance inspection, not business metrics.
 */
@Service
public class ReconciliationApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ReconciliationExceptionQuery dailyExceptions;
  private final ProductSalesExceptionQuery productExceptions;
  private final CanonicalDailySalesQuery dailySales;
  private final CanonicalProductSalesQuery productSales;
  private final DailySalesOverrideService dailyOverrides;
  private final ProductSalesOverrideService productOverrides;
  private final RuleAuditService ruleAudit;
  private final OverrideAuditService overrideAudit;
  private final PermissionService permissions;

  public ReconciliationApplicationService(
      ReconciliationExceptionQuery dailyExceptions,
      ProductSalesExceptionQuery productExceptions,
      CanonicalDailySalesQuery dailySales,
      CanonicalProductSalesQuery productSales,
      DailySalesOverrideService dailyOverrides,
      ProductSalesOverrideService productOverrides,
      RuleAuditService ruleAudit,
      OverrideAuditService overrideAudit,
      PermissionService permissions) {
    this.dailyExceptions = dailyExceptions;
    this.productExceptions = productExceptions;
    this.dailySales = dailySales;
    this.productSales = productSales;
    this.dailyOverrides = dailyOverrides;
    this.productOverrides = productOverrides;
    this.ruleAudit = ruleAudit;
    this.overrideAudit = overrideAudit;
    this.permissions = permissions;
  }

  public List<DailyException> listDailyExceptions(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return dailyExceptions.listDaily().stream().map(this::toException).toList();
  }

  public Record dailyRecord(UserRole role, LocalDate date) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    List<SourceValue> sources =
        dailySales.currentDailySalesForDate(date).stream()
            .map(s -> new SourceValue(s.sourceSystem(), plain(s.totalSales())))
            .toList();
    Optional<DailySalesOverrideService.OverrideDetail> override = dailyOverrides.latestFor(date);
    Field field =
        new Field(
            "daily_sales",
            "Daily sales",
            sources,
            override.isPresent(),
            override
                .map(DailySalesOverrideService.OverrideDetail::authoritativeSource)
                .orElse(null),
            override.map(DailySalesOverrideService.OverrideDetail::reason).orElse(null),
            override.map(DailySalesOverrideService.OverrideDetail::actorEmail).orElse(null),
            override.map(DailySalesOverrideService.OverrideDetail::recordedAt).orElse(null));
    return new Record(date.toString(), date.toString(), "day", List.of(field));
  }

  public List<ProductException> listProductExceptions(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return productExceptions.listAll().stream().map(this::toProductException).toList();
  }

  public List<String> listProducts(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return productSales.distinctProductNameKeys();
  }

  public ProductRecord productRecord(UserRole role, LocalDate date, String product) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    List<SourceValue> sources =
        productSales.currentProductSalesForDate(date).stream()
            .filter(v -> v.productNameKey().equals(product))
            .map(v -> new SourceValue(v.sourceSystem(), productValue(v.quantitySold(), v.amount())))
            .toList();
    Optional<ProductSalesOverrideService.OverrideDetail> override =
        productOverrides.latestFor(date, product);
    Field field =
        new Field(
            product,
            product,
            sources,
            override.isPresent(),
            override
                .map(ProductSalesOverrideService.OverrideDetail::authoritativeSource)
                .orElse(null),
            override.map(ProductSalesOverrideService.OverrideDetail::reason).orElse(null),
            override.map(ProductSalesOverrideService.OverrideDetail::actorEmail).orElse(null),
            override.map(ProductSalesOverrideService.OverrideDetail::recordedAt).orElse(null));
    return new ProductRecord(product, product, "product", List.of(field));
  }

  public OverrideResult overrideDaily(
      UserRole role, String actorEmail, LocalDate date, String source, String reason) {
    dailyOverrides.save(role, actorEmail, date, source, reason);
    return new OverrideResult(true, date.toString(), "daily_sales");
  }

  public OverrideResult overrideProduct(
      UserRole role,
      String actorEmail,
      LocalDate date,
      String product,
      String source,
      String reason) {
    productOverrides.save(role, actorEmail, date, product, source, reason);
    return new OverrideResult(true, product, "product");
  }

  /**
   * The reconciliation screen's combined, read-only audit history: every rule change (created /
   * updated / deleted) plus every manual override (set / removed), newest first. Authorized on the
   * domain read resource like the other reconciliation reads.
   */
  public List<AuditEntry> audit(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    List<AuditEntry> out = new ArrayList<>();
    for (RuleAuditService.RuleAuditEntry e : ruleAudit.history()) {
      out.add(
          new AuditEntry(
              "rule", e.change(), e.entityType(), e.fieldKey(), null, null, e.by(), e.at()));
    }
    for (OverrideAuditService.OverrideAuditEntry o : overrideAudit.history()) {
      boolean active = o.supersededAt() == null;
      out.add(
          new AuditEntry(
              "override",
              active ? "set" : "removed",
              o.entityType(),
              o.fieldKey(),
              o.authoritativeSource(),
              o.reason(),
              o.by(),
              active ? o.at() : o.supersededAt()));
    }
    out.sort(Comparator.comparing(AuditEntry::at).reversed());
    return out;
  }

  private DailyException toException(ReconciliationExceptionQuery.DailyException e) {
    List<SourceValue> sources =
        e.sources().stream()
            .map(s -> new SourceValue(s.sourceSystem(), plain(s.totalSales())))
            .toList();
    return new DailyException(
        e.tradingDate() + ":daily_sales",
        e.tradingDate().toString(),
        e.tradingDate().toString(),
        "daily_sales",
        sources,
        e.status());
  }

  private ProductException toProductException(ProductSalesExceptionQuery.ProductException e) {
    List<SourceValue> sources =
        e.sources().stream()
            .map(s -> new SourceValue(s.sourceSystem(), productValue(s.quantitySold(), s.amount())))
            .toList();
    return new ProductException(
        e.tradingDate() + ":" + e.productNameKey(),
        e.productNameKey(),
        e.productNameKey(),
        e.productNameKey(),
        sources,
        e.status());
  }

  private static String productValue(BigDecimal quantity, BigDecimal amount) {
    return quantity.toPlainString() + " × $" + amount.toPlainString();
  }

  private static String plain(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  public record DailyException(
      String id,
      String recordId,
      String entity,
      String field,
      List<SourceValue> sources,
      String status) {}

  public record ProductException(
      String id,
      String recordId,
      String entity,
      String field,
      List<SourceValue> sources,
      String status) {}

  public record Record(String id, String entity, String entityType, List<Field> fields) {}

  public record ProductRecord(String id, String entity, String entityType, List<Field> fields) {}

  public record Field(
      String name,
      String label,
      List<SourceValue> sources,
      boolean overridden,
      String authoritativeSource,
      String overrideReason,
      String overrideActor,
      Instant overrideAt) {}

  public record SourceValue(String source, String value) {}

  public record OverrideResult(boolean ok, String recordId, String field) {}

  /** One entry in the combined rule + override audit history (see {@link #audit}). */
  public record AuditEntry(
      String kind, // "rule" | "override"
      String change, // rule: created/updated/deleted; override: set/removed
      String entityType,
      String fieldKey,
      String source, // override authoritative source, else null
      String reason, // override reason, else null
      String by,
      Instant at) {}
}
