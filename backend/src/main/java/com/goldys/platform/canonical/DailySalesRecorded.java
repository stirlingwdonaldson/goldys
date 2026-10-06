package com.goldys.platform.canonical;

import java.time.LocalDate;

/** Published after a canonical daily-sales fact is recorded, so the reconciliation projector can
 * update the affected date's resolved projection in the same transaction. */
public record DailySalesRecorded(LocalDate tradingDate) {}
