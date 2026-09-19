package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.UUID;

/** The normalized candidate for one sale item, independent of any JPA entity. */
record SaleItemInput(
    String sourceSystem,
    String sourceRecordRef,
    String itemName,
    Integer quantitySold,
    BigDecimal amount,
    UUID rawRecordId) {}
