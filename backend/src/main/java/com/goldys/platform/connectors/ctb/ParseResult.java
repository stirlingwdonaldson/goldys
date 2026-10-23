package com.goldys.platform.connectors.ctb;

import java.util.List;

/**
 * A parsed CSV's outcome: the normalized invoices plus any raw columns the source contract does not
 * declare (additive schema drift). The caller surfaces the drift rather than discarding it.
 */
public record ParseResult(List<CtInvoice> invoices, List<String> unknownColumns) {}
