package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regresses a production crash-loop: {@code CtbDetailBackfillRunner} has two constructors, and
 * without {@code @Autowired} on the public one Spring could not choose between them, so enabling
 * {@code app.ctb-detail-backfill} aborted the whole context ("No default constructor found") and
 * the backend restarted in a loop. This test enables the flag and asserts the bean is created.
 */
@SpringBootTest(properties = "app.ctb-detail-backfill=true")
@Import(PostgresContainerConfiguration.class)
class CtbDetailBackfillRunnerBeanIntegrationTest {
  @MockitoBean ConnectorRunner connectorRunner;
  @Autowired CtbDetailBackfillRunner runner;

  @Test
  void runnerBeanIsCreatedWhenTheFlagIsEnabled() {
    assertThat(runner).isNotNull();
  }
}
