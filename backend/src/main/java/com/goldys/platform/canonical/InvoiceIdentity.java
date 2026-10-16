package com.goldys.platform.canonical;

import java.util.UUID;

/**
 * A recorded invoice's identity: its logical id, its supplier-scoped source key, and the supplier's
 * own stable identity, so line items can reference both the invoice and the supplier.
 */
public record InvoiceIdentity(
    UUID logicalEntityId,
    String sourceRecordRef,
    UUID supplierLogicalId,
    String supplierSourceRef) {}
