package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A typed view of one current deleted sale, for the Deleted-sales list endpoint. */
public record DeletedSaleRow(
    LocalDate tradingDate,
    String saleNumber,
    String orderType,
    String note,
    BigDecimal totalIncTax,
    BigDecimal totalExTax,
    BigDecimal totalTax,
    BigDecimal totalCost,
    String openedRegisterName,
    String deletedRegisterName,
    String staffName,
    String deletedByStaffName,
    String tableNumber,
    String siteId,
    String customerName) {}
