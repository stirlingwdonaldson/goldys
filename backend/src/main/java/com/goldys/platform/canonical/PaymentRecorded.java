package com.goldys.platform.canonical;

import java.time.LocalDate;

/**
 * Published after a canonical payment fact is recorded, so the payment projector can update the
 * affected trading date's resolved projection in the same transaction.
 */
public record PaymentRecorded(LocalDate tradingDate) {}
