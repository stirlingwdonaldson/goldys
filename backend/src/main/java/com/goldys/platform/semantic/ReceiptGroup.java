package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Observed receipt-number grouping, not a transaction-level receipt or completeness guarantee. */
public record ReceiptGroup(
    String receiptNumber,
    String association,
    String coverage,
    long lineCount,
    List<Transaction> transactions) {
  public record Transaction(
      long siteNumber,
      String transactionId,
      String linkedTransactionId,
      LocalDateTime sourceLocalTime,
      BigDecimal net,
      BigDecimal tax,
      BigDecimal tip,
      BigDecimal total) {}
}
