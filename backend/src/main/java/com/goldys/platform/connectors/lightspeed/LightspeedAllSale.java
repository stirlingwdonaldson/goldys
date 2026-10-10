package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One sale-transaction row from the Lightspeed "all sales" scheduled report.
 *
 * <p>Unlike {@link LightspeedInsightsSale} (the daily reconciliation report), this is keyed on the
 * sale's closed date. {@code totalIncTax} is already net of refunds and adjustments: refunds are
 * separate rows carrying a negative total, and adjustments are folded into the total rather than
 * reported as a separate additive column.
 */
public record LightspeedAllSale(LocalDate saleDate, BigDecimal totalIncTax, BigDecimal totalTax) {}
