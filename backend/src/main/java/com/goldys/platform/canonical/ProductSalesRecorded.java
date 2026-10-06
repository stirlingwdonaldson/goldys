package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Published after a canonical product-sales fact is recorded, for the product projection. */
public record ProductSalesRecorded(String productNameKey, LocalDate tradingDate) {}
