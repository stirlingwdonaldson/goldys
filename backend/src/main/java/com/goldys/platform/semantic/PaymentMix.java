package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date/payment-type's resolved payment mix: amount, tip and transaction count. */
public record PaymentMix(
    LocalDate tradingDate,
    String paymentTypeName,
    BigDecimal amount,
    BigDecimal tip,
    long count,
    boolean hasConflict) {}
