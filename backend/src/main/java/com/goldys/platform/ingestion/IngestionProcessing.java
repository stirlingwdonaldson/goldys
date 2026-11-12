package com.goldys.platform.ingestion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Records ordinary push-processing outcomes after raw persistence. Does not open a transaction:
 * canonical writers retain their transaction ownership and successfully written rows survive a
 * later row's failure. Historical replay policies remain explicit at their callers.
 */
public final class IngestionProcessing {
  private final IngestionService ingestion;

  public IngestionProcessing(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  public <T> List<T> parse(UUID rawId, Supplier<List<T>> parser) {
    List<T> rows = run(rawId, IngestionStageKind.PARSED, parser);
    complete(rawId, IngestionStageKind.PARSED, rows.isEmpty());
    return rows;
  }

  public <T> void canonicalize(UUID rawId, Collection<T> rows, Consumer<T> writer) {
    run(
        rawId,
        IngestionStageKind.CANONICALIZED,
        () -> {
          rows.forEach(writer);
          return null;
        });
    complete(rawId, IngestionStageKind.CANONICALIZED, rows.isEmpty());
  }

  private <T> T run(UUID rawId, IngestionStageKind stage, Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException failure) {
      try {
        ingestion.recordStage(rawId, stage, IngestionStageOutcome.FAILED);
      } catch (RuntimeException accountingFailure) {
        failure.addSuppressed(accountingFailure);
      }
      throw failure;
    }
  }

  private void complete(UUID rawId, IngestionStageKind stage, boolean empty) {
    ingestion.recordStage(
        rawId, stage, empty ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
  }
}
