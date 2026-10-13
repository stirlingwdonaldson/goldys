package com.goldys.platform.connectors.opentable.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OpenTableSessionSchedulerTest {

  @Test
  void delegateRunsScheduledWork() throws Exception {
    OpenTableSessionScheduler scheduler = new OpenTableSessionScheduler();
    try {
      CountDownLatch ran = new CountDownLatch(1);

      scheduler.delegate().execute(ran::countDown);

      assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      scheduler.close();
    }
  }

  @Test
  void closeStopsTheExecutor() {
    OpenTableSessionScheduler scheduler = new OpenTableSessionScheduler();

    scheduler.close();

    assertThat(scheduler.delegate().isShutdown()).isTrue();
  }
}
