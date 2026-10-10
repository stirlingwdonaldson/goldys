package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment-tender row from the Lightspeed Insights "all-payments" scheduled CSV. */
public record LightspeedPayment(
    LocalDate createdDate,
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
    String customerName) {}
