package com.goldys.platform.canonical;

import java.util.UUID;

/** A committed observation batch requires its disposable receipt projections to be rebuilt. */
public record ReceiptEvidenceRecorded(UUID rawRecordId) {}
