package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A typed view of one current payment tender, for the Payments list endpoint. */
public record PaymentRow(
    LocalDate tradingDate,
    String saleNumber,
    String paymentTypeName,
    String paymentTypeCode,
    String paymentSourceType,
    String lspayPaymentMode,
    String clearingAccount,
    BigDecimal amount,
    BigDecimal tip,
    BigDecimal tendered,
    BigDecimal surcharge,
    int paymentCount,
    int tipCount,
    String reconciled,
    String registerCode,
    String registerName,
    String staffName,
    String staffCode,
    String siteId,
    String customerName) {}
