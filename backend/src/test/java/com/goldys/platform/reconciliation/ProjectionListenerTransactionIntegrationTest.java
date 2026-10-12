package com.goldys.platform.reconciliation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.goldys.platform.canonical.SaleItemRecorded;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the projection listeners enqueue only after the writer's transaction commits: an event
 * published inside a transaction that rolls back must not queue a recompute (the listener uses
 * {@code @TransactionalEventListener(AFTER_COMMIT)}), while a committed publish queues the date.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProjectionListenerTransactionIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Autowired ApplicationEventPublisher publisher;
  @Autowired SaleItemProjectionListener listener;
  @Autowired PlatformTransactionManager txManager;

  @MockitoBean SaleItemProjector projector;

  @Test
  void eventPublishedInsideRolledBackTransactionQueuesNothing() {
    new TransactionTemplate(txManager)
        .execute(
            status -> {
              publisher.publishEvent(new SaleItemRecorded(SEP_13));
              status.setRollbackOnly();
              return null;
            });

    listener.flushPendingDates();

    verify(projector, never()).recompute(any(LocalDate.class));
  }

  @Test
  void eventPublishedInsideCommittedTransactionQueuesAfterCommit() {
    new TransactionTemplate(txManager)
        .executeWithoutResult(status -> publisher.publishEvent(new SaleItemRecorded(SEP_13)));

    listener.flushPendingDates();

    // At least once (not exactly once): the scheduled flush may also fire within the 2s window.
    verify(projector, atLeastOnce()).recompute(SEP_13);
  }
}
