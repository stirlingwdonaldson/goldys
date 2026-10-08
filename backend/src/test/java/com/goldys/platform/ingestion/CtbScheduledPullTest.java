package com.goldys.platform.ingestion;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class CtbScheduledPullTest {

  @Test
  void delegatesToRunConnectorForCtb() {
    IngestionService ingestion = mock(IngestionService.class);
    CtbScheduledPull pull = new CtbScheduledPull(ingestion);

    pull.pullCtb();

    verify(ingestion).runConnector("CTB");
  }

  @Test
  void swallowsAnUnexpectedSchedulingError() {
    IngestionService ingestion = mock(IngestionService.class);
    doThrow(new IllegalStateException("boom")).when(ingestion).runConnector("CTB");
    CtbScheduledPull pull = new CtbScheduledPull(ingestion);

    pull.pullCtb(); // must not throw — a connector failure is already ledgered by the runner
  }
}
