package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionProcessingTest {
  private final IngestionService ingestion = mock(IngestionService.class);
  private final IngestionProcessing processing = new IngestionProcessing(ingestion);
  private final UUID rawId = UUID.randomUUID();

  @Test
  void keepsEarlierWritesAndStopsAtTheFailedRow() {
    List<Integer> written = new ArrayList<>();
    var failure = new IllegalStateException("write failed");
    assertThatThrownBy(
            () ->
                processing.canonicalize(
                    rawId,
                    List.of(1, 2, 3),
                    row -> {
                      if (row == 2) throw failure;
                      written.add(row);
                    }))
        .isSameAs(failure);
    assertThat(written).containsExactly(1);
    verify(ingestion)
        .recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
    verifyNoMoreInteractions(ingestion);
  }

  @Test
  void failedAccountingDoesNotMaskTheOriginalProcessingFailure() {
    var failure = new IllegalArgumentException("parse failed");
    var accountingFailure = new IllegalStateException("stage store unavailable");
    doThrow(accountingFailure)
        .when(ingestion)
        .recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
    assertThatThrownBy(
            () ->
                processing.parse(
                    rawId,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(failure.getSuppressed()).containsExactly(accountingFailure);
  }
}
