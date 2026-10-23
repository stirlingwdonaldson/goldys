package com.goldys.platform.canonical;

import java.util.UUID;

/** A source-scoped product observation: the source's code, its display name, and optional attrs. */
public record SourceProductInput(
    String sourceSystem,
    String sourceRecordRef,
    Integer sourceProductId,
    String name,
    String sku,
    String category,
    UUID rawRecordId) {}
