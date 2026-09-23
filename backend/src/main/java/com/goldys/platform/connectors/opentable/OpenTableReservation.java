package com.goldys.platform.connectors.opentable;

import java.time.Instant;

/** One reservation row parsed from a GuestCenter CSV export. */
public record OpenTableReservation(
    String reservationId,
    Instant reservationAt,
    int partySize,
    String status,
    String table,
    String sourceChannel,
    String partyName) {}
