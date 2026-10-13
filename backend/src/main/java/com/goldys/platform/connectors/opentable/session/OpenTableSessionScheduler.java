package com.goldys.platform.connectors.opentable.session;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Owns the single-threaded scheduler the assisted sign-in session runs on.
 *
 * <p>This is a dedicated type rather than a bare {@link ScheduledExecutorService} bean on purpose:
 * a bean whose type is an {@link java.util.concurrent.Executor} makes Spring Boot's {@code
 * TaskExecutionAutoConfiguration} back off, which removes the {@code applicationTaskExecutor} bean
 * that {@code ConnectorRunner} and {@code ForecastService} inject. Keeping the executor behind this
 * wrapper keeps it off that condition; the session service takes the {@link #delegate() delegate}.
 */
public final class OpenTableSessionScheduler implements AutoCloseable {

  private final ScheduledExecutorService delegate;

  public OpenTableSessionScheduler() {
    this.delegate =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "opentable-session");
              thread.setDaemon(true);
              return thread;
            });
  }

  public ScheduledExecutorService delegate() {
    return delegate;
  }

  @Override
  public void close() {
    delegate.shutdownNow();
  }
}
