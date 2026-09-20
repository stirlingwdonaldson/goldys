package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of a source's daily sales total.
 *
 * <p>Fact fields ({@code totalSales}, {@code gstTotal}, {@code netTotal}) are immutable; a
 * correction closes this row and inserts a successor sharing the same logical identity. The logical
 * identity is derived deterministically from the trading date, so two sources describing the same
 * day share it — this is the date-keyed matching.
 */
@Entity
@Table(name = "canonical_daily_sales")
class CanonicalDailySales extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "total_sales", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal totalSales;

  @Column(name = "gst_total", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal gstTotal;

  @Column(name = "net_total", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal netTotal;

  protected CanonicalDailySales() {}

  private CanonicalDailySales(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      BigDecimal totalSales,
      BigDecimal gstTotal,
      BigDecimal netTotal) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.totalSales = totalSales;
    this.gstTotal = gstTotal;
    this.netTotal = netTotal;
  }

  static CanonicalDailySales create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      BigDecimal totalSales,
      BigDecimal gstTotal,
      BigDecimal netTotal) {
    return new CanonicalDailySales(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        tradingDate,
        totalSales,
        gstTotal,
        netTotal);
  }

  boolean sameFact(DailySalesInput input) {
    return sameAmount(totalSales, input.totalSales())
        && sameAmount(gstTotal, input.gstTotal())
        && sameAmount(netTotal, input.netTotal());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  BigDecimal totalSales() {
    return totalSales;
  }

  BigDecimal gstTotal() {
    return gstTotal;
  }

  BigDecimal netTotal() {
    return netTotal;
  }
}
