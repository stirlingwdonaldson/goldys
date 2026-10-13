package com.goldys.platform.connectors.opentable.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class LiveFramePollerTest {

  @Test
  void pushesFramesOnTheCadenceUntilClosed() throws Exception {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      AtomicInteger captured = new AtomicInteger();
      List<ScreencastFrame> pushed = new CopyOnWriteArrayList<>();
      Supplier<ScreencastFrame> capture =
          () -> new ScreencastFrame("frame-" + captured.incrementAndGet(), 1280, 800);
      LiveFramePoller poller =
          new LiveFramePoller(scheduler, Duration.ofMillis(20), capture, pushed::add);

      poller.start();
      awaitUntil(() -> pushed.size() >= 3, Duration.ofSeconds(5));
      assertThat(pushed.get(0).jpegBase64()).isEqualTo("frame-1");

      poller.close();
      int afterClose = pushed.size();
      Thread.sleep(150);
      assertThat(pushed.size()).isEqualTo(afterClose);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void skipsEmptyCaptures() throws Exception {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      AtomicInteger captures = new AtomicInteger();
      List<ScreencastFrame> pushed = new CopyOnWriteArrayList<>();
      Supplier<ScreencastFrame> capture =
          () -> {
            captures.incrementAndGet();
            return null;
          };
      LiveFramePoller poller =
          new LiveFramePoller(scheduler, Duration.ofMillis(20), capture, pushed::add);

      poller.start();
      awaitUntil(() -> captures.get() >= 3, Duration.ofSeconds(5));
      poller.close();

      assertThat(pushed).isEmpty();
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static void awaitUntil(java.util.function.BooleanSupplier condition, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within " + timeout);
      }
      Thread.sleep(10);
    }
  }
}
