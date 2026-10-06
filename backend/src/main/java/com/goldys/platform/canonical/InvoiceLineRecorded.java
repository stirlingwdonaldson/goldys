package com.goldys.platform.canonical;

import java.time.LocalDate;

/**
 * Published after a canonical invoice line is recorded, so the inventory projector can update the
 * affected date's resolved COGS in the same transaction.
 */
public record InvoiceLineRecorded(LocalDate invoiceDate) {}
