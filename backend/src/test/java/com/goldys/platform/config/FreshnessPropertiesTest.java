package com.goldys.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that the provisional per-domain freshness thresholds in {@code application.yml} bind to
 * {@link FreshnessProperties} as {@link Duration}s, exercising the same
 * {@code @ConfigurationPropertiesScan} registration the application uses.
 */
class FreshnessPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(TestConfig.class);

  @Test
  void bindsProvisionalThresholdsFromApplicationYaml() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          FreshnessProperties properties = context.getBean(FreshnessProperties.class);

          assertThat(properties.thresholds())
              .containsEntry("resolved_daily_sales", Duration.ofHours(2))
              .containsEntry("resolved_reservation_day", Duration.ofDays(1))
              .containsEntry("resolved_labour_day", Duration.ofDays(1))
              .containsEntry("resolved_inventory_day", Duration.ofDays(7))
              .containsEntry("accounting", Duration.ofDays(30));
        });
  }

  @Configuration(proxyBeanMethods = false)
  @ConfigurationPropertiesScan("com.goldys.platform")
  static class TestConfig {}
}
