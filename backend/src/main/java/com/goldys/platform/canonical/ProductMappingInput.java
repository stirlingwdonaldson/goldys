package com.goldys.platform.canonical;

import java.util.UUID;

/** A source-product → business-product mapping decision. */
public record ProductMappingInput(
    String sourceSystem,
    String sourceRecordRef,
    UUID productId,
    String confidence,
    String reviewStatus,
    UUID rawRecordId) {}
