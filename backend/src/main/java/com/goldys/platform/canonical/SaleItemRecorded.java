package com.goldys.platform.canonical;

import java.time.LocalDate;

/**
 * Published after a canonical sale-item fact is recorded, so the sale-item projector can update the
 * affected trading date's resolved projection in the same transaction.
 */
public record SaleItemRecorded(LocalDate tradingDate) {}
