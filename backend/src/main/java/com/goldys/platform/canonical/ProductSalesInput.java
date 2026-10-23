package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized per-product sales total from one source, to be recorded canonically. {@code
 * stockCode} is the source's own product code (CTB stock code, or Lightspeed where present) and is
 * nullable when the source does not supply one.
 */
public record ProductSalesInput(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount,
    String stockCode,
    UUID rawRecordId,
    String sourceProductRef,
    java.util.List<UUID> contributorRawIds) {
  public ProductSalesInput(
      String source,
      LocalDate date,
      String name,
      BigDecimal quantity,
      BigDecimal amount,
      String code,
      UUID raw) {
    this(source, date, name, quantity, amount, code, raw, null, java.util.List.of(raw));
  }

  public ProductSalesInput {
    contributorRawIds =
        contributorRawIds == null
            ? java.util.List.of(rawRecordId)
            : java.util.List.copyOf(contributorRawIds);
    if (!contributorRawIds.contains(rawRecordId))
      throw new IllegalArgumentException("Primary raw receipt must be a contributor");
    java.util.Objects.requireNonNull(quantitySold, "Missing sold quantity");
    java.util.Objects.requireNonNull(amount, "Missing source sale amount");
  }

  public static String sourceIdentity(String code, Integer site) {
    if (code == null || code.isBlank()) return null;
    return "site:" + (site == null ? "unknown" : site) + ":code:" + code.length() + ":" + code;
  }
}
