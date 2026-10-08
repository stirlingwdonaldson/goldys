package com.goldys.platform.semantic;

/** One raw record's metadata plus its payload. {@code isJson} is true when the payload parsed as JSON. */
public record RawRecordDetail(
    RawRecordSummary summary, String payload, boolean isJson, String sha256) {}
