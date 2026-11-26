package com.goldys.platform.ingestion;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class TandaScheduledPullTest {
  @Test
  void captureWaitsForStartupProjectionCompletion() {
    var ingestion = mock(IngestionService.class);
    var scheduled = new TandaScheduledPull(ingestion);
    scheduled.refresh();
    verifyNoInteractions(ingestion);
    scheduled.onReady();
    verify(ingestion).runConnector("TANDA");
    scheduled.refresh();
    verify(ingestion, times(2)).runConnector("TANDA");
  }
}
