package com.goldys.platform.classification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Kicks off a review-classification backfill on startup, submitting {@link
 * ReviewClassificationService#backfill()} to the application task executor so startup is never
 * blocked. When every review is already classified the pass classifies nothing (a no-op) and just
 * records a {@code SUCCEEDED} run with a zero count.
 */
@Component
public class ReviewClassificationBackfillRunner implements ApplicationRunner {

  private final ReviewClassificationService service;
  private final TaskExecutor executor;

  public ReviewClassificationBackfillRunner(
      ReviewClassificationService service,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
    this.service = service;
    this.executor = executor;
  }

  @Override
  public void run(ApplicationArguments args) {
    executor.execute(service::backfill);
  }
}
