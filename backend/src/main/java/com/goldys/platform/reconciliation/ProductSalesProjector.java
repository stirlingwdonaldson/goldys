package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the product_sales exception projection from canonical rows, overrides, and rules.
 * Injects repositories (not the trigger services) to avoid a Spring bean cycle.
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
  private final ReconciliationExceptionRowRepository exceptions;

  public ProductSalesProjector(
      CanonicalProductSalesQuery productSales,
      ProductSalesOverrideRepository overrides,
      ResolutionRuleRepository rules,
      ReconciliationExceptionRowRepository exceptions) {
    this.productSales = productSales;
    this.overrides = overrides;
    this.rules = rules;
    this.exceptions = exceptions;
  }

  @Transactional
  public void recompute(String productNameKey, LocalDate date) {
    List<ProductSourceTotal> sources =
        productSales.currentProductSalesForDateAndProduct(date, productNameKey).stream()
            .map(
                v ->
                    new ProductSourceTotal(
                        v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()))
            .toList();
    Optional<String> overrideSource =
        overrides.findCurrent(productNameKey, date).map(ProductSalesOverride::authoritativeSource);
    Optional<ResolutionRule> rule = rules.findCurrent(ENTITY_TYPE, productNameKey);
    if (rule.isEmpty()) {
      rule = rules.findCurrent(ENTITY_TYPE, "*");
    }
    reconcile(productNameKey, date, ProductSalesResolver.resolve(sources, overrideSource, rule));
  }

  @Transactional
  public void recomputeAll() {
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

    for (Map.Entry<String, List<ProductSourceTotal>> e : byPair.entrySet()) {
      String[] parts = e.getKey().split(SEP);
      LocalDate date = LocalDate.parse(parts[0]);
      String product = parts[1];
      Optional<ResolutionRule> rule =
          Optional.ofNullable(ruleByFieldKey.get(product)).or(() -> Optional.ofNullable(catchAll));
      ProductSalesResolver.resolve(
              e.getValue(), Optional.ofNullable(overrideByPair.get(e.getKey())), rule)
          .ifPresent(
              status ->
                  exceptions.save(
                      new ReconciliationExceptionRow(
                          ENTITY_TYPE, product, FIELD_KEY, date, status, CLOCK.instant())));
    }
  }

  private void reconcile(String product, LocalDate date, Optional<String> status) {
    List<ReconciliationExceptionRow> existing =
        exceptions.findByEntityTypeAndEntityKeyAndTradingDate(ENTITY_TYPE, product, date);
    if (status.isPresent()) {
      if (existing.isEmpty()) {
        exceptions.save(
            new ReconciliationExceptionRow(
                ENTITY_TYPE, product, FIELD_KEY, date, status.get(), CLOCK.instant()));
      } else if (!status.get().equals(existing.get(0).status())) {
        existing.get(0).changeStatus(status.get());
      }
    } else if (!existing.isEmpty()) {
      exceptions.deleteAll(existing);
    }
  }
}
