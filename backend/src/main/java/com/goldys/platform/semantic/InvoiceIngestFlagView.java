package com.goldys.platform.semantic;

import java.time.Instant;

/**
 * One invoice-ingestion anomaly, exposed read-only to the Inventory screen so a missing or
 * unparseable PDF is a visible fact rather than a silent gap.
 */
public record InvoiceIngestFlagView(
    String flagType,
    String invoiceNumber,
    String pdfFilename,
    String stockCode,
    String detail,
    Instant occurredAt) {}
