package com.goldys.platform.canonical;

import java.util.UUID;

/** A normalized supplier observation from one source: its code (identity) and display name. */
public record SupplierInput(
    String sourceSystem, String supplierCode, String supplierName, UUID rawRecordId) {}
