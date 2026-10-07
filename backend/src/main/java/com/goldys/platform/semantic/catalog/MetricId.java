package com.goldys.platform.semantic.catalog;

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

  public String value() {
    return value;
  }
}
