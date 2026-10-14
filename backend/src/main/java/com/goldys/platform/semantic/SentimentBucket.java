package com.goldys.platform.semantic;

import java.time.LocalDate;

/** One weekly sentiment bucket; {@code periodStart} is the inclusive start of the week. */
public record SentimentBucket(LocalDate periodStart, long positive, long neutral, long negative) {}
