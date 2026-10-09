package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One deleted-order row from the Lightspeed Insights "all-deleted-orders" scheduled CSV. */
public record LightspeedDeletedSale(
    LocalDate saleOpenedDate,
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
    String customerName) {}
