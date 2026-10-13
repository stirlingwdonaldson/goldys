package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;

/**
 * Regression guard for the production crash loop where enabling the assisted OpenTable session
 * ({@code opentable.api-enabled=true}) stopped the application context from starting.
 *
 * <p>The session scheduler used to be exposed as a bean whose type is a {@link
 * java.util.concurrent.Executor}. That made Spring Boot's {@code TaskExecutionAutoConfiguration}
 * back off, which removed the {@code applicationTaskExecutor} bean that {@code ConnectorRunner} and
 * {@code ForecastService} require, so the context failed with {@code NoSuchBeanDefinitionException}
 * and the backend restarted forever. This boots the real context with the feature on to prove it
 * starts and that Boot's async executor is still there.
 */
@SpringBootTest(properties = "opentable.api-enabled=true")
@Import(PostgresContainerConfiguration.class)
class OpenTableApiConfigIntegrationTest {

  @Autowired ApplicationContext context;

  @Test
  void enablingTheAssistedSessionKeepsBootsAsyncExecutor() {
    assertThat(context.getBean("applicationTaskExecutor", TaskExecutor.class)).isNotNull();
  }
}
