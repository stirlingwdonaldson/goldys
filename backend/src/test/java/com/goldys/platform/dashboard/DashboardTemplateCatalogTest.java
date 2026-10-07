package com.goldys.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.semantic.catalog.MetricCatalog;
import org.junit.jupiter.api.Test;

class DashboardTemplateCatalogTest {

  @Test
  void templatesAreValidDashboardDocuments() {
    var catalog = new DashboardTemplateCatalog();
    assertThat(catalog.templates()).hasSize(9);
    for (var t : catalog.templates()) {
      assertThat(t.widgets()).isNotEmpty();
      for (var w : t.widgets())
        for (var q : w.queries())
          assertThat(new MetricCatalog().definition(q.metric())).isNotNull(); // valid by construction
    }
  }

  @Test
  void byIdReturnsTheNamedTemplate() {
    var catalog = new DashboardTemplateCatalog();
    assertThat(catalog.byId("daily").name()).isEqualTo("Daily Management");
    assertThat(catalog.byId("owner").widgets()).hasSize(3);
  }

  @Test
  void byIdRejectsUnknownTemplate() {
    var catalog = new DashboardTemplateCatalog();
    assertThatThrownBy(() -> catalog.byId("nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nope");
  }
}
