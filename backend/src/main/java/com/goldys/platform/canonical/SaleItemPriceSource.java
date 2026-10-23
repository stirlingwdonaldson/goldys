package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Scalar price inputs keep sale history out of the projector's persistence context. */
record SaleItemPriceSource(
    String itemName,
    BigDecimal soldPriceIncTax,
    BigDecimal costIncTax,
    LocalDate tradingDate,
    UUID rawRecordId,
    String sourceRecordRef) {}
