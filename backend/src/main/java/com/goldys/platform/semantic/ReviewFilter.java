package com.goldys.platform.semantic;

import java.time.LocalDate;

/** Optional filter for the review list. All fields nullable; null means "no filter". */
public record ReviewFilter(
    LocalDate from,
    LocalDate to,
    String source,
    String sentiment,
    String aspect,
    Integer minRating,
    String query) {}
