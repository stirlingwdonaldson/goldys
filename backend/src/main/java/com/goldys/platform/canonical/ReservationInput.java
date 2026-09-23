package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.UUID;

/** A normalized reservation fact from one source, to be recorded as a canonical version. */
public record ReservationInput(
    String sourceSystem,
    String reservationId,
    Instant reservationAt,
    int partySize,
    String status,
    String tableName,
    String sourceChannel,
    String partyName,
    UUID rawRecordId) {}
