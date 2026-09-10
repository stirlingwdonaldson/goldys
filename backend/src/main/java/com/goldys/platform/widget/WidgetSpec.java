package com.goldys.platform.widget;

import java.util.Map;

/**
 * Phase 2 stub (spec Requirement 9's "JSON-driven UI generation" invariant). The Conversational BI
 * assistant emits instances of this shape for the frontend to render - it never generates
 * executable frontend code directly. Shape is intentionally minimal/placeholder; flesh out widget
 * "type" values (chart, table, stat-tile, etc.) against dataviz needs once Phase 2 starts.
 */
public record WidgetSpec(String type, String title, Map<String, Object> data) {}
