package com.goldys.platform.reconciliation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only history of manual overrides, derived from the append-only override rows. Both
 * daily-sales and product-sales overrides are surfaced; a row whose {@code supersededAt} is set is
 * no longer authoritative (it was replaced by a later override for the same date/field).
 */
@Service
public class OverrideAuditService {
  private final DailySalesOverrideRepository daily;
  private final ProductSalesOverrideRepository product;

  public OverrideAuditService(
      DailySalesOverrideRepository daily, ProductSalesOverrideRepository product) {
    this.daily = daily;
    this.product = product;
  }

  public List<OverrideAuditEntry> history() {
    List<OverrideAuditEntry> out = new ArrayList<>();
    for (DailySalesOverride o : daily.findAllByOrderByRecordedAtDesc()) {
      out.add(
          new OverrideAuditEntry(
              "daily_sales",
              "daily_sales",
              o.authoritativeSource(),
              o.reason(),
              o.actorEmail(),
              o.recordedAt(),
              o.supersededAt()));
    }
    for (ProductSalesOverride o : product.findAllByOrderByRecordedAtDesc()) {
      out.add(
          new OverrideAuditEntry(
              "product_sales",
              o.productNameKey(),
              o.authoritativeSource(),
              o.reason(),
              o.actorEmail(),
              o.recordedAt(),
              o.supersededAt()));
    }
    out.sort(Comparator.comparing(OverrideAuditEntry::at).reversed());
    return out;
  }

  public record OverrideAuditEntry(
      String entityType,
      String fieldKey,
      String authoritativeSource,
      String reason,
      String by,
      Instant at,
      Instant supersededAt) {}
}
