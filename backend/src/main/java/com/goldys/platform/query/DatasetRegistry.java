package com.goldys.platform.query;

import com.goldys.platform.semantic.query.Cardinality;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.FieldKind;
import com.goldys.platform.semantic.query.FieldType;
import com.goldys.platform.semantic.query.ReadMode;
import com.goldys.platform.semantic.query.RelationshipDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The allowlisted catalogue of datasets and relationships the relational query layer may read.
 *
 * <p>This is the read-direction enforcement point: only resolved projections and single-source
 * canonical facts are registered. The raw ledger and multi-source canonical tables (which have
 * resolved projections) are deliberately absent, so the query path cannot bypass reconciliation.
 */
@Component
public class DatasetRegistry {

  private final Map<String, DatasetDescriptor> datasets = new LinkedHashMap<>();
  private final Map<String, RelationshipDescriptor> relationships = new LinkedHashMap<>();

  public DatasetRegistry() {
    datasets.put(
        "resolved_daily_sales",
        new DatasetDescriptor(
            "resolved_daily_sales",
            "resolved_daily_sales",
            ReadMode.RESOLVED,
            "one trading day",
            "reconciliation.sales",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f("total_sales", "total_sales", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("net_sales", "net_sales", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("gst", "gst", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_product_sales",
        new DatasetDescriptor(
            "resolved_product_sales",
            "resolved_product_sales",
            ReadMode.RESOLVED,
            "one product per trading day",
            "reconciliation.sales",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "product_name_key",
                    "product_name_key",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "quantity_sold",
                    "quantity_sold",
                    FieldType.DECIMAL,
                    "each",
                    true,
                    FieldKind.MEASURE),
                f("amount", "amount", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_payment_day",
        new DatasetDescriptor(
            "resolved_payment_day",
            "resolved_payment_day",
            ReadMode.RESOLVED,
            "one payment tender per trading day",
            "payments.metrics",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "payment_type_name",
                    "payment_type_name",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("amount", "amount", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f("tip", "tip", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f(
                    "payment_count",
                    "payment_count",
                    FieldType.INTEGER,
                    null,
                    false,
                    FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_deleted_sale_day",
        new DatasetDescriptor(
            "resolved_deleted_sale_day",
            "resolved_deleted_sale_day",
            ReadMode.RESOLVED,
            "one trading day",
            "deleted-sales.metrics",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "deleted_count",
                    "deleted_count",
                    FieldType.INTEGER,
                    null,
                    false,
                    FieldKind.MEASURE),
                f(
                    "total_inc_tax",
                    "total_inc_tax",
                    FieldType.DECIMAL,
                    "AUD",
                    false,
                    FieldKind.MEASURE),
                f("total_tax", "total_tax", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_sale_item_day",
        new DatasetDescriptor(
            "resolved_sale_item_day",
            "resolved_sale_item_day",
            ReadMode.RESOLVED,
            "one product category per trading day",
            "sale-items.metrics",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "category_name",
                    "category_name",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("quantity", "quantity", FieldType.DECIMAL, "each", false, FieldKind.MEASURE),
                f("amount", "amount", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_reservation_day",
        new DatasetDescriptor(
            "resolved_reservation_day",
            "resolved_reservation_day",
            ReadMode.RESOLVED,
            "one service period per trading day",
            "reservations.metrics",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "service_period",
                    "service_period",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("bookings", "bookings", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f("attended", "attended", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f("covers", "covers", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f("cancelled", "cancelled", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f("no_shows", "no_shows", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f("walk_ins", "walk_ins", FieldType.INTEGER, null, false, FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_recipe",
        new DatasetDescriptor(
            "resolved_recipe",
            "resolved_recipe",
            ReadMode.RESOLVED,
            "one recipe",
            "recipes.cost",
            false,
            List.of(
                f("recipe_id", "recipe_id", FieldType.INTEGER, null, false, FieldKind.DIMENSION),
                f("recipe_name", "recipe_name", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "recipe_folder_name",
                    "recipe_folder_name",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "cost_per_portion",
                    "cost_per_portion",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "cost_per_portion_incl_labour",
                    "cost_per_portion_incl_labour",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f("total_cost", "total_cost", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("portion_size", "portion_size", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "portion_size_value",
                    "portion_size_value",
                    FieldType.DECIMAL,
                    null,
                    true,
                    FieldKind.MEASURE),
                f("menu_price", "menu_price", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("actual_cost", "actual_cost", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("menu_state", "menu_state", FieldType.TEXT, null, false, FieldKind.DIMENSION),
                f("price_source", "price_source", FieldType.TEXT, null, false, FieldKind.DIMENSION),
                f(
                    "last_sold_price",
                    "last_sold_price",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "last_sold_cost",
                    "last_sold_cost",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "last_sold_date",
                    "last_sold_date",
                    FieldType.DATE,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_recipe_ingredient",
        new DatasetDescriptor(
            "resolved_recipe_ingredient",
            "resolved_recipe_ingredient",
            ReadMode.RESOLVED,
            "one recipe ingredient line",
            "recipes.ingredients",
            false,
            List.of(
                f("id", "id", FieldType.UUID, null, false, FieldKind.DIMENSION),
                f(
                    "parent_recipe_id",
                    "parent_recipe_id",
                    FieldType.INTEGER,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "line_recipe_id",
                    "line_recipe_id",
                    FieldType.INTEGER,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "line_recipe_name",
                    "line_recipe_name",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "is_sub_recipe",
                    "is_sub_recipe",
                    FieldType.BOOLEAN,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "recipe_detail_id",
                    "recipe_detail_id",
                    FieldType.INTEGER,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("stock_id", "stock_id", FieldType.INTEGER, null, true, FieldKind.DIMENSION),
                f("stock_code", "stock_code", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "stock_description",
                    "stock_description",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "portion_weight",
                    "portion_weight",
                    FieldType.DECIMAL,
                    null,
                    true,
                    FieldKind.MEASURE),
                f(
                    "unit_of_measurement",
                    "unit_of_measurement",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "cost_per_qty",
                    "cost_per_qty",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "cost_per_unit",
                    "cost_per_unit",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "supplier_name",
                    "supplier_name",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "resolved_inventory_day",
        new DatasetDescriptor(
            "resolved_inventory_day",
            "resolved_inventory_day",
            ReadMode.RESOLVED,
            "one trading day",
            "inventory.cost",
            false,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f("purchases", "purchases", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("wastage", "wastage", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "stock_on_hand",
                    "stock_on_hand",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "resolution_type",
                    "resolution_type",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "authoritative_source",
                    "authoritative_source",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "has_conflict",
                    "has_conflict",
                    FieldType.BOOLEAN,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "resolved_at",
                    "resolved_at",
                    FieldType.TIMESTAMP,
                    null,
                    false,
                    FieldKind.DIMENSION))));

    datasets.put(
        "invoice",
        new DatasetDescriptor(
            "invoice",
            "canonical_invoice",
            ReadMode.CANONICAL_DIRECT,
            "one invoice header",
            "inventory.cost",
            true,
            List.of(
                f(
                    "invoice_number",
                    "invoice_number",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f(
                    "supplier_name",
                    "supplier_name",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "supplier_logical_id",
                    "supplier_logical_id",
                    FieldType.UUID,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f("invoice_date", "invoice_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f("due_date", "due_date", FieldType.DATE, null, true, FieldKind.DIMENSION),
                f(
                    "total_amount",
                    "total_amount",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "amount_ex_tax",
                    "amount_ex_tax",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f("gst_amount", "gst_amount", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "freight_amount",
                    "freight_amount",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "purchase_number",
                    "purchase_number",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "account_number",
                    "account_number",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f("tax_code", "tax_code", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "pdf_filename",
                    "pdf_filename",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION))));

    datasets.put(
        "invoice_line",
        new DatasetDescriptor(
            "invoice_line",
            "canonical_invoice_line",
            ReadMode.CANONICAL_DIRECT,
            "one invoice line item",
            "inventory.cost",
            true,
            List.of(
                f(
                    "invoice_number",
                    "invoice_number",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("invoice_date", "invoice_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "product_name_key",
                    "product_name_key",
                    FieldType.TEXT,
                    null,
                    false,
                    FieldKind.DIMENSION),
                f("stock_code", "stock_code", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f("quantity", "quantity", FieldType.DECIMAL, "each", false, FieldKind.MEASURE),
                f("unit_cost", "unit_cost", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f("line_total", "line_total", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f("category", "category", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f("uom", "uom", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "unit_quantity",
                    "unit_quantity",
                    FieldType.DECIMAL,
                    null,
                    true,
                    FieldKind.MEASURE),
                f("pack_size", "pack_size", FieldType.DECIMAL, null, true, FieldKind.MEASURE),
                f("wet_amount", "wet_amount", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "line_total_inc_tax",
                    "line_total_inc_tax",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "line_gst_amount",
                    "line_gst_amount",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f(
                    "line_tax_rate",
                    "line_tax_rate",
                    FieldType.DECIMAL,
                    null,
                    true,
                    FieldKind.MEASURE),
                f("origin", "origin", FieldType.TEXT, null, true, FieldKind.DIMENSION))));

    datasets.put(
        "source_product_sale_fact",
        new DatasetDescriptor(
            "source_product_sale_fact",
            "source_product_sale_fact",
            ReadMode.CANONICAL_DIRECT,
            "one product per trading day per source group",
            "inventory.cost",
            true,
            List.of(
                f("trading_date", "trading_date", FieldType.DATE, null, false, FieldKind.DIMENSION),
                f(
                    "quantity_sold",
                    "quantity_sold",
                    FieldType.DECIMAL,
                    null,
                    true,
                    FieldKind.MEASURE),
                f("amount", "amount", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("product_name", "product_name", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f("site_number", "site_number", FieldType.INTEGER, null, true, FieldKind.DIMENSION),
                f("stock_code", "stock_code", FieldType.TEXT, null, true, FieldKind.DIMENSION),
                f(
                    "unique_group_id",
                    "unique_group_id",
                    FieldType.TEXT,
                    null,
                    true,
                    FieldKind.DIMENSION),
                f(
                    "cost_per_unit",
                    "cost_per_unit",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f("food_cost", "food_cost", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "total_food_cost",
                    "total_food_cost",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f("profit", "profit", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f(
                    "discounted_amount",
                    "discounted_amount",
                    FieldType.DECIMAL,
                    "AUD",
                    true,
                    FieldKind.MEASURE),
                f("tax_amount", "tax_amount", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE),
                f("unit_price", "unit_price", FieldType.DECIMAL, "AUD", true, FieldKind.MEASURE))));

    // invoice -> invoice_line on the supplier-scoped identity (Phase 2), never the ambiguous
    // invoice_number. 1:N: summing an invoice-header measure across this join would double-count.
    relationships.put(
        "invoice_lines",
        new RelationshipDescriptor(
            "invoice_lines",
            "invoice",
            "invoice_line",
            List.of(new RelationshipDescriptor.JoinKey("logical_entity_id", "invoice_logical_id")),
            Cardinality.ONE_TO_MANY,
            true,
            "inventory.cost"));
  }

  public DatasetDescriptor dataset(String id) {
    return datasets.get(id);
  }

  public List<DatasetDescriptor> datasets() {
    return List.copyOf(datasets.values());
  }

  public RelationshipDescriptor relationship(String id) {
    return relationships.get(id);
  }

  public List<RelationshipDescriptor> relationships() {
    return List.copyOf(relationships.values());
  }

  public List<RelationshipDescriptor> relationshipsFrom(String datasetId) {
    return relationships.values().stream().filter(r -> r.from().equals(datasetId)).toList();
  }

  private static FieldDescriptor f(
      String name, String column, FieldType type, String unit, boolean nullable, FieldKind kind) {
    return new FieldDescriptor(name, column, type, unit, nullable, kind, null);
  }
}
