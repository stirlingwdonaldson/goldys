package com.goldys.platform.reconciliation;

/** How often staff resolved a conflict by hand, for the dashboard. */
public record OverrideUsage(int count, String period) {}
