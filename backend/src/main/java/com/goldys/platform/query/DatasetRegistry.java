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
                f("total_sales", "total_sales", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f("net_sales", "net_sales", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
                f("gst", "gst", FieldType.DECIMAL, "AUD", false, FieldKind.MEASURE),
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
