package com.goldys.platform.ingestion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.goldys.platform.connectors.ctb.CtbDetailBackfillConnector;
import org.junit.jupiter.api.Test;

class CtbDetailBackfillRunnerTest {

  @Test
  void runStartsTheConstructedConnector() {
    ConnectorRunner connectorRunner = mock(ConnectorRunner.class);
    CtbDetailBackfillConnector connector = mock(CtbDetailBackfillConnector.class);
    CtbDetailBackfillRunner runner = new CtbDetailBackfillRunner(connectorRunner, () -> connector);

    runner.run(null);

    verify(connectorRunner).run(connector, null);
  }
}
