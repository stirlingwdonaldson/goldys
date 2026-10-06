package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import com.goldys.platform.metrics.OperationalMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the product-sales read model ({@code resolved_product_sales} and {@code
 * reconciliation_exception}) from canonical rows, overrides, and rules.
 *
 * <p>Injects repositories (not the trigger services) to avoid a Spring bean cycle with {@link
 * ProductSalesOverrideService} and {@link ResolutionRuleService}.
 */
@Service
public class ProductSalesProjector {
  private static final String ENTITY_TYPE = "product_sales";
  private static final String FIELD_KEY = "product_sales";
  private static final String SEP = "\u0000";
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductSalesQuery productSales;
  private final ProductSalesOverrideRepository overrides;
  private final ResolutionRuleRepository rules;
  private final ResolvedProductSalesRepository resolved;
  private final ReconciliationExceptionRowRepository exceptions;
  private final OperationalMetrics metrics;

  public ProductSalesProjector(
      CanonicalProductSalesQuery productSales,
      ProductSalesOverrideRepository overrides,
      ResolutionRuleRepository rules,
      ResolvedProductSalesRepository resolved,
      ReconciliationExceptionRowRepository exceptions,
      OperationalMetrics metrics) {
    this.productSales = productSales;
    this.overrides = overrides;
    this.rules = rules;
    this.resolved = resolved;
    this.exceptions = exceptions;
    this.metrics = metrics;
  }

  /** Recompute one product/day pair's resolved value and exception. */
  @Transactional
  public void recompute(String productNameKey, LocalDate date) {
    List<ProductSourceTotal> sources = sourcesFor(date, productNameKey);
    Optional<String> overrideSource =
        overrides.findCurrent(productNameKey, date).map(ProductSalesOverride::authoritativeSource);
    Optional<ProductSalesResolver.Result> result =
        ProductSalesResolver.resolve(sources, overrideSource, ruleFor(productNameKey));

    Instant now = CLOCK.instant();
    if (result.isPresent()) {
      upsertResolved(productNameKey, date, result.get(), now);
    } else {
      resolved.findByTradingDateAndProductNameKey(date, productNameKey).ifPresent(resolved::delete);
    }
    reconcileException(productNameKey, date, result, now);
  }

  /**
   * Rebuild the whole read model from canonical + overrides + rules. Used on rule changes and
   * deploy backfill.
   */
  @Transactional
  public void recomputeAll() {
    Timer.Sample sample = metrics.start();
    try {
      rebuildAll();
    } finally {
      metrics.stopProjection(sample, ENTITY_TYPE);
    }
  }

  private void rebuildAll() {
    resolved.deleteAllInBatch();
    exceptions.deleteByEntityType(ENTITY_TYPE);

    Map<String, List<ProductSourceTotal>> byPair = new HashMap<>();
    for (ProductSalesView v : productSales.currentProductSales()) {
      byPair
          .computeIfAbsent(v.tradingDate() + SEP + v.productNameKey(), k -> new ArrayList<>())
          .add(
              new ProductSourceTotal(
                  v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()));
    }
    Map<String, String> overrideByPair = new HashMap<>();
    for (ProductSalesOverride o : overrides.findAllCurrent()) {
      overrideByPair.put(o.tradingDate() + SEP + o.productNameKey(), o.authoritativeSource());
    }
    Map<String, ResolutionRule> ruleByFieldKey = new HashMap<>();
    for (ResolutionRule r : rules.findByEntityTypeAndSupersededAtIsNull(ENTITY_TYPE)) {
      ruleByFieldKey.putIfAbsent(r.fieldKey(), r);
    }
    ResolutionRule catchAll = ruleByFieldKey.get("*");

    Instant now = CLOCK.instant();
    List<ResolvedProductSales> resolvedRows = new ArrayList<>();
    List<ReconciliationExceptionRow> exceptionRows = new ArrayList<>();
    for (Map.Entry<String, List<ProductSourceTotal>> e : byPair.entrySet()) {
      String[] parts = e.getKey().split(SEP);
      LocalDate date = LocalDate.parse(parts[0]);
      String product = parts[1];
      Optional<ResolutionRule> rule =
          Optional.ofNullable(ruleByFieldKey.get(product)).or(() -> Optional.ofNullable(catchAll));
      ProductSalesResolver.resolve(
              e.getValue(), Optional.ofNullable(overrideByPair.get(e.getKey())), rule)
          .ifPresent(
              r -> {
                resolvedRows.add(
                    new ResolvedProductSales(
                        date,
                        product,
                        r.quantitySold(),
                        r.amount(),
                        r.resolutionType(),
                        r.authoritativeSource(),
                        r.hasConflict(),
                        now));
                if (r.hasConflict()) {
                  exceptionRows.add(
                      new ReconciliationExceptionRow(
                          ENTITY_TYPE, product, FIELD_KEY, date, r.resolutionType(), now));
                }
              });
    }
    resolved.saveAll(resolvedRows);
    exceptions.saveAll(exceptionRows);
  }

  private List<ProductSourceTotal> sourcesFor(LocalDate date, String productNameKey) {
    return productSales.currentProductSalesForDateAndProduct(date, productNameKey).stream()
        .map(
            v ->
                new ProductSourceTotal(
                    v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()))
        .toList();
  }

  private Optional<ResolutionRule> ruleFor(String productNameKey) {
    Optional<ResolutionRule> specific = rules.findCurrent(ENTITY_TYPE, productNameKey);
    return specific.isPresent() ? specific : rules.findCurrent(ENTITY_TYPE, "*");
  }

  private void upsertResolved(
      String product, LocalDate date, ProductSalesResolver.Result r, Instant now) {
    Optional<ResolvedProductSales> existing =
        resolved.findByTradingDateAndProductNameKey(date, product);
    if (existing.isPresent()) {
      existing
          .get()
          .replace(
              r.quantitySold(),
              r.amount(),
              r.resolutionType(),
              r.authoritativeSource(),
              r.hasConflict(),
              now);
    } else {
      resolved.save(
          new ResolvedProductSales(
              date,
              product,
              r.quantitySold(),
              r.amount(),
              r.resolutionType(),
              r.authoritativeSource(),
              r.hasConflict(),
              now));
    }
  }

  private void reconcileException(
      String product, LocalDate date, Optional<ProductSalesResolver.Result> result, Instant now) {
    List<ReconciliationExceptionRow> existing =
        exceptions.findByEntityTypeAndEntityKeyAndTradingDate(ENTITY_TYPE, product, date);
    if (result.isPresent() && result.get().hasConflict()) {
      String status = result.get().resolutionType();
      if (existing.isEmpty()) {
        exceptions.save(
            new ReconciliationExceptionRow(ENTITY_TYPE, product, FIELD_KEY, date, status, now));
      } else if (!status.equals(existing.get(0).status())) {
        existing.get(0).changeStatus(status);
      }
      // else: unchanged — keep the original detectedAt
    } else if (!existing.isEmpty()) {
      exceptions.deleteAll(existing);
    }
  }
}
