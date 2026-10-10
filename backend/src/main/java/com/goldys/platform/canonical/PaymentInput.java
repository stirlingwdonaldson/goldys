package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized payment-tender fact from one source, to be recorded as a canonical version. */
public record PaymentInput(
    String sourceSystem,
    LocalDate tradingDate,
    String saleNumber,
    String paymentTypeCode,
    String paymentTypeName,
    String paymentSourceType,
    String lspayPaymentMode,
    String clearingAccount,
    BigDecimal amount,
    BigDecimal tip,
    BigDecimal tendered,
    BigDecimal surcharge,
    Integer paymentCount,
    Integer tipCount,
    String reconciled,
    String registerCode,
    String registerName,
    String staffName,
    String staffCode,
    String siteId,
    String customerName,
    UUID rawRecordId) {}
