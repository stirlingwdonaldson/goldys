package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MetricCatalogDocTest {

  @Test
  void everyMetricAppearsInTheCatalogueDocument() throws Exception {
    Path doc = Path.of("../docs/metrics/catalog.md");
    String text = Files.readString(doc);
    for (MetricId id : MetricId.values()) {
      assertThat(text).contains("`" + id.value() + "`");
    }
  }
}
