package com.goldys.platform.connectors.opentable;

import java.math.BigDecimal;

/**
 * A single menu-item row parsed from the OpenTable GuestCenter menu export.
 *
 * <p>Raw-only for now: the typed rows are parked for the menu canonicalization slice and are not
 * written anywhere by the ingest pipeline yet.
 *
 * @param invariantId the {@code Invariant ID} column — the stable identity
 * @param itemId the {@code Item ID} column
 * @param name the {@code Items} column
 * @param price the {@code Price} column with the leading {@code A$} stripped, or {@code null} when
 *     blank
 */
public record OpenTableMenuItem(String invariantId, String itemId, String name, BigDecimal price) {}
