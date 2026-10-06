package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.InvoiceLineView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the inventory read model ({@code resolved_inventory_day}) from canonical invoice lines
 * and manual overrides.
 *
 * <p>Inventory is single-source (CTB), so there is no multi-source conflict. Purchases (COGS) is
 * computed from invoice line totals grouped by invoice date — never from invoice header totals.
 * Wastage and stock-on-hand are modelled but have no ingestion source yet, so they stay null.
 */
@Service
public class InventoryProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalInventoryQuery inventory;
  private final InventoryOverrideRepository overrides;
  private final ResolvedInventoryDayRepository resolved;

  public InventoryProjector(
      CanonicalInventoryQuery inventory,
      InventoryOverrideRepository overrides,
      ResolvedInventoryDayRepository resolved) {
    this.inventory = inventory;
    this.overrides = overrides;
    this.resolved = resolved;
  }

  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (InvoiceLineView v : inventory.currentInvoiceLines()) {
      dates.add(v.invoiceDate());
    }
    project(dates);
  }

  @Transactional
  public void recompute(LocalDate... dates) {
    project(Set.of(dates));
  }

  private void project(Set<LocalDate> dates) {
    if (dates.isEmpty()) {
      return;
    }
    resolved.deleteByTradingDateIn(dates);

    Map<LocalDate, BigDecimal> purchasesByDate = new HashMap<>();
    for (InvoiceLineView v : inventory.currentInvoiceLinesForDates(dates)) {
      purchasesByDate.merge(v.invoiceDate(), v.lineTotal(), BigDecimal::add);
    }

    Map<LocalDate, BigDecimal> overrideByDate = new HashMap<>();
    for (InventoryOverride o : overrides.findAllCurrent()) {
      if (o.overriddenPurchases() != null) {
        overrideByDate.put(o.tradingDate(), o.overriddenPurchases());
      }
    }

    Instant now = CLOCK.instant();
    List<ResolvedInventoryDay> rows = new ArrayList<>();
    for (Map.Entry<LocalDate, BigDecimal> e : purchasesByDate.entrySet()) {
      LocalDate date = e.getKey();
      BigDecimal overridden = overrideByDate.get(date);
      if (overridden != null) {
        rows.add(
            new ResolvedInventoryDay(date, overridden, null, null, "override", "manual", false, now));
      } else {
        rows.add(
            new ResolvedInventoryDay(date, e.getValue(), null, null, "single", "CTB", false, now));
      }
    }
    resolved.saveAll(rows);
  }
}
