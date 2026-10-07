package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MetricCatalogTest {

  private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z][a-z0-9.-]{0,99}");

  @Test
  void everyMetricIdHasADefinition() {
    MetricCatalog catalog = new MetricCatalog();
    for (MetricId id : MetricId.values()) {
      assertThat(catalog.definition(id)).as(id.value()).isNotNull();
    }
  }

  @Test
  void definitionsCarryAValidPermissionResource() {
    MetricCatalog catalog = new MetricCatalog();
    for (MetricId id : MetricId.values()) {
      MetricDefinition d = catalog.definition(id);
      assertThat(RESOURCE_KEY.matcher(d.requiredPermission()).matches())
          .as(id.value() + " permission")
          .isTrue();
    }
  }

  @Test
  void grossSalesAllowsDayWeekMonthAndNoDimensions() {
    MetricDefinition d = new MetricCatalog().definition(MetricId.SALES_GROSS);
    assertThat(d.allowedGrains())
        .containsExactlyInAnyOrder(TimeGrain.DAY, TimeGrain.WEEK, TimeGrain.MONTH);
    assertThat(d.validDimensions()).isEmpty();
    assertThat(d.unit()).isEqualTo("AUD");
  }
}
