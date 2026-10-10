package com.goldys.platform.canonical;

import java.time.LocalDate;

/**
 * Published after a canonical deleted-sale fact is recorded, so the projector can update the
 * affected trading date's resolved projection in the same transaction.
 */
public record DeletedSaleRecorded(LocalDate tradingDate) {}
