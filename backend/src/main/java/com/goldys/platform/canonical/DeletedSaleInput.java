package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A normalized deleted-order fact from one source, to be recorded as a canonical version. */
public record DeletedSaleInput(
    String sourceSystem,
    LocalDate tradingDate,
    String saleNumber,
    String orderType,
    String note,
    BigDecimal totalIncTax,
    BigDecimal totalExTax,
    BigDecimal totalTax,
    BigDecimal totalCost,
    String openedRegisterCode,
    String openedRegisterName,
    String deletedRegisterCode,
    String deletedRegisterName,
    String staffName,
    String staffCode,
    String deletedByStaffName,
    String deletedByStaffCode,
    String tableNumber,
    String siteId,
    String customerName,
    UUID rawRecordId) {}
