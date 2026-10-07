package com.goldys.platform.dashboard;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The code-based template catalogue (mirrors {@link
 * com.goldys.platform.semantic.catalog.MetricCatalog}). Every template is built from {@link
 * MetricId}s, so a template cannot reference a nonexistent metric and is valid by construction.
 */
@Component
public class DashboardTemplateCatalog {

  private final List<DashboardTemplate> templates;

  public DashboardTemplateCatalog() {
    this.templates =
        List.of(
            t(
                "daily",
                "Daily Management",
                "Today's sales, covers, labour and conflicts.",
                ts("w1", MetricId.SALES_GROSS),
                ts("w2", MetricId.RESERVATIONS_COVERS),
                ts("w3", MetricId.LABOUR_COST)),
            t(
                "weekly-foh",
                "Weekly — Front of House",
                "Covers and reservations for the week.",
                ts("w1", MetricId.RESERVATIONS_COVERS)),
            t(
                "weekly-boh",
                "Weekly — Back of House",
                "Inventory and food-cost for the week.",
                ts("w1", MetricId.INVENTORY_PURCHASES)),
            t(
                "inventory",
                "Inventory",
                "Purchases and wastage.",
                ts("w1", MetricId.INVENTORY_PURCHASES),
                ts("w2", MetricId.INVENTORY_WASTAGE)),
            t(
                "sales",
                "Sales Performance",
                "Gross and net sales trend.",
                ts("w1", MetricId.SALES_GROSS),
                ts("w2", MetricId.SALES_NET)),
            t(
                "labour",
                "Labour",
                "Scheduled/actual hours, cost and variance.",
                table(
                    "w1",
                    MetricId.LABOUR_SCHEDULED_HOURS,
                    MetricId.LABOUR_ACTUAL_HOURS,
                    MetricId.LABOUR_COST,
                    MetricId.LABOUR_HOURS_VARIANCE)),
            t(
                "reservations",
                "Reservations",
                "Bookings.",
                ts("w1", MetricId.RESERVATIONS_BOOKINGS)),
            t(
                "food-cost",
                "Food Cost",
                "Purchases and wastage.",
                table("w1", MetricId.INVENTORY_PURCHASES, MetricId.INVENTORY_WASTAGE)),
            t(
                "owner",
                "Owner Overview",
                "Headline sales, labour % and food cost %.",
                ts("w1", MetricId.SALES_GROSS),
                ts("w2", MetricId.LABOUR_FOH_PERCENT),
                ts("w3", MetricId.INVENTORY_FOOD_COST_PERCENT)));
  }

  public List<DashboardTemplate> templates() {
    return templates;
  }

  public DashboardTemplate byId(String id) {
    return templates.stream()
        .filter(t -> t.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard template: " + id));
  }

  private static DashboardTemplate t(
      String id, String name, String description, SavedWidget... widgets) {
    return new DashboardTemplate(id, name, description, List.of(widgets));
  }

  private static SavedWidget ts(String id, MetricId metric) {
    return new SavedWidget(id, "time-series", List.of(query(metric)), new WidgetLayout(6, 2));
  }

  private static SavedWidget table(String id, MetricId... metrics) {
    return new SavedWidget(
        id, "table", Arrays.stream(metrics).map(DashboardTemplateCatalog::query).toList(),
        new WidgetLayout(12, 2));
  }

  private static MetricQuery query(MetricId metric) {
    LocalDate today = LocalDate.now();
    return new MetricQuery(
        metric,
        new TimeRange(today.minusDays(6), today, Calendar.CALENDAR),
        TimeGrain.DAY,
        Set.of(),
        null);
  }
}
