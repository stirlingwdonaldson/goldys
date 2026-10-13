package com.goldys.platform.connectors.opentable.session;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pushes page frames from {@code capture} on a fixed cadence until {@link #close()}.
 *
 * <p>Chromium's DevTools {@code Page.startScreencast} is damage-driven: it emits a frame only when
 * the page repaints, so an idle page (a login form waiting for input) streams nothing and the live
 * view stays blank. Capturing the page on a timer instead always yields the current view, whether
 * or not it changed. A {@code null} capture (nothing to send) is skipped; a failed capture is
 * logged and retried on the next tick rather than stopping the loop.
 */
final class LiveFramePoller implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(LiveFramePoller.class);

  private final ScheduledExecutorService scheduler;
  private final Duration interval;
  private final Supplier<ScreencastFrame> capture;
  private final Consumer<ScreencastFrame> onFrame;

  private ScheduledFuture<?> task;

  LiveFramePoller(
      ScheduledExecutorService scheduler,
      Duration interval,
      Supplier<ScreencastFrame> capture,
      Consumer<ScreencastFrame> onFrame) {
    this.scheduler = scheduler;
    this.interval = interval;
    this.capture = capture;
    this.onFrame = onFrame;
  }

  /** Begins pushing frames immediately and then every {@code interval}. */
  void start() {
    long millis = Math.max(1, interval.toMillis());
    task = scheduler.scheduleWithFixedDelay(this::tick, 0, millis, TimeUnit.MILLISECONDS);
  }

  private void tick() {
    try {
      ScreencastFrame frame = capture.get();
      if (frame != null) {
        onFrame.accept(frame);
      }
    } catch (RuntimeException e) {
      // A transient CDP failure (e.g. mid-navigation); the next tick retries.
      log.debug("Live view frame capture failed: {}", e.getMessage());
    }
  }

  @Override
  public void close() {
    if (task != null) {
      task.cancel(false);
      task = null;
    }
  }
}
