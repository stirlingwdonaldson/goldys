package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Every resolved domain a catalogue metric reads must say which source systems feed it, or the
 * platform map draws it with no upstream (and freshness can't blame a connector for it). Binds the
 * real {@code application.yml} without starting Spring, so it runs without a database.
 */
class PlatformMapCompletenessTest {

  @Test
  void everyCatalogueDomainDeclaresItsSources() throws IOException {
    FreshnessProperties freshness = loadFreshness();
    MetricCatalog catalog = new MetricCatalog();

    Set<String> undeclared = new TreeSet<>();
    for (MetricId id : catalog.ids()) {
      String domain = catalog.definition(id).sourceDomain();
      if (!PlatformMapService.DERIVED_DOMAIN.equals(domain)
          && freshness.sources().getOrDefault(domain, List.of()).isEmpty()) {
        undeclared.add(domain);
      }
    }

    assertThat(undeclared)
        .as("add these domains under app.freshness.sources in application.yml")
        .isEmpty();
  }

  private static FreshnessProperties loadFreshness() throws IOException {
    StandardEnvironment environment = new StandardEnvironment();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .forEach(source -> environment.getPropertySources().addLast(source));
    return new Binder(ConfigurationPropertySources.get(environment))
        .bind("app.freshness", FreshnessProperties.class)
        .get();
  }
}
