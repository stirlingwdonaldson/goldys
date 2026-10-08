package com.goldys.platform.semantic;

import java.time.Instant;

/** Optional filter for the raw-record list. All fields nullable; null means "no filter". */
public record RawFilter(
    String sourceSystem, String fetcherIdentity, String fetchMethod, Instant from, Instant to) {}
