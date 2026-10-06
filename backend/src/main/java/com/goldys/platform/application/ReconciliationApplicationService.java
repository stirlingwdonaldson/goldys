package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.ProductSalesExceptionQuery;
import com.goldys.platform.reconciliation.ProductSalesOverrideService;
import com.goldys.platform.reconciliation.ReconciliationExceptionQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  private final PermissionService permissions;

  public ReconciliationApplicationService(
      ReconciliationExceptionQuery dailyExceptions,
      ProductSalesExceptionQuery productExceptions,
      CanonicalDailySalesQuery dailySales,
      CanonicalProductSalesQuery productSales,
      DailySalesOverrideService dailyOverrides,
      ProductSalesOverrideService productOverrides,
      PermissionService permissions) {
    this.dailyExceptions = dailyExceptions;
    this.productExceptions = productExceptions;
    this.dailySales = dailySales;
    this.productSales = productSales;
    this.dailyOverrides = dailyOverrides;
    this.productOverrides = productOverrides;
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
    Optional<String> authoritative = dailyOverrides.currentAuthoritativeSource(date);
    Field field =
        new Field(
            "daily_sales",
            "Daily sales",
            sources,
            authoritative.isPresent(),
            authoritative.orElse(null));
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
    Optional<String> authoritative = productOverrides.currentAuthoritativeSource(date, product);
    Field field =
        new Field(product, product, sources, authoritative.isPresent(), authoritative.orElse(null));
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
      String authoritativeSource) {}

  public record SourceValue(String source, String value) {}

  public record OverrideResult(boolean ok, String recordId, String field) {}
}
