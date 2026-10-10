package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current payment fact, for modules outside the {@code canonical} package.
 */
public record PaymentView(
    LocalDate tradingDate,
    String paymentTypeName,
    BigDecimal amount,
    BigDecimal tip,
    int paymentCount) {}
