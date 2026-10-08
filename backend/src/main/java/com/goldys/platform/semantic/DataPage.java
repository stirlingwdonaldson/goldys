package com.goldys.platform.semantic;

import java.util.List;

/** A paged result for the explorer's list endpoints. */
public record DataPage<T>(List<T> items, long total, int page, int size) {}
