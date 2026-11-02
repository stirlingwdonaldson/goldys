package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Source-local time has no verified offset; do not convert it to an instant. */
public record SaleTransactionInput(
    String transactionId,
    String receiptNumber,
    String linkedTransactionId,
    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        LocalDateTime sourceLocalTime,
    BigDecimal net,
    BigDecimal tax,
    BigDecimal tip,
    BigDecimal total) {}
