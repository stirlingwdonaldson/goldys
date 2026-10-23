package com.goldys.platform.semantic.query;

/** One order-by item. */
public record OrderByItem(String field, SortDirection direction) {}
