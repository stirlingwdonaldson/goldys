package com.goldys.platform.connector;

/** Outcome of one SourceConnector.fetch() run. */
public record IngestionRunResult(int recordsIngested, boolean failed, String failureDetail) {

    public static IngestionRunResult success(int recordsIngested) {
        return new IngestionRunResult(recordsIngested, false, null);
    }

    public static IngestionRunResult failure(String detail) {
        return new IngestionRunResult(0, true, detail);
    }
}
