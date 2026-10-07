package com.goldys.platform.semantic.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable, machine-readable metric identifiers. Never a table or Java class name. */
public enum MetricId {
  SALES_GROSS("sales.gross"),
  SALES_NET("sales.net"),
  SALES_GST("sales.gst"),
  RESERVATIONS_BOOKINGS("reservations.bookings"),
  RESERVATIONS_ATTENDED("reservations.attended"),
  RESERVATIONS_COVERS("reservations.covers"),
  RESERVATIONS_NO_SHOWS("reservations.no_shows"),
  LABOUR_SCHEDULED_HOURS("labour.scheduled_hours"),
  LABOUR_ACTUAL_HOURS("labour.actual_hours"),
  LABOUR_COST("labour.cost"),
  INVENTORY_PURCHASES("inventory.purchases"),
  INVENTORY_WASTAGE("inventory.wastage"),
  INVENTORY_STOCK_ON_HAND("inventory.stock_on_hand"),
  PRODUCT_SALES_AMOUNT("product.sales_amount"),
  PRODUCT_SALES_QUANTITY("product.sales_quantity"),
  RESERVATIONS_NO_SHOW_RATE("reservations.no_show_rate"),
  RESERVATIONS_BOOKING_TO_COVER_CONVERSION("reservations.booking_to_cover_conversion"),
  RESERVATIONS_AVG_PARTY_SIZE("reservations.avg_party_size"),
  SALES_AVERAGE_SPEND_PER_COVER("sales.average_spend_per_cover"),
  LABOUR_HOURS_PER_COVER("labour.hours_per_cover"),
  LABOUR_COST_PER_COVER("labour.cost_per_cover"),
  LABOUR_HOURS_VARIANCE("labour.hours_variance"),
  LABOUR_FOH_PERCENT("labour.foh_percent"),
  LABOUR_BOH_PERCENT("labour.boh_percent"),
  INVENTORY_FOOD_COST_PERCENT("inventory.food_cost_percent"),
  PRODUCT_TOP_SELLERS("product.top_sellers");

  private final String value;

  MetricId(String value) {
    this.value = value;
  }

  /** Canonical wire form: the dotted id, e.g. {@code "sales.gross"}. */
  @JsonValue
  public String value() {
    return value;
  }

  /**
   * Accepts either the enum name ({@code "SALES_GROSS"}) or the dotted value ({@code
   * "sales.gross"}).
   */
  @JsonCreator
  public static MetricId fromValue(String input) {
    try {
      return MetricId.valueOf(input);
    } catch (IllegalArgumentException ignore) {
      // fall through to dotted-value match
    }
    for (MetricId id : values()) {
      if (id.value.equals(input)) {
        return id;
      }
    }
    throw new IllegalArgumentException("Unknown metric id: " + input);
  }
}
