package com.goldys.platform.canonical;

import java.time.LocalDate;

/** A source-observed receipt group, never an inferred transaction identity. */
public record ReceiptMappingInput(
    String receiptLineId,
    String receiptNumber,
    long siteNumber,
    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        LocalDate tradingDate) {}
