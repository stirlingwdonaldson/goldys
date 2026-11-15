package com.goldys.platform.canonical;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Read-only browse access to every canonical entity for the data explorer. Rows are returned as
 * generic {@code {id, columns}} maps (shared bitemporal columns first, then entity columns) so the
 * UI renders any entity without knowing it. This is the canonical half of the explorer's registry:
 * the descriptor list here is the single place a new canonical entity is registered.
 */
@Service
public class CanonicalBrowseQuery {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  private static final int MAX_PAGE_SIZE = 200;

  private final CanonicalDailySalesRepository dailySales;
  private final CanonicalProductSalesRepository productSales;
  private final CanonicalReservationRepository reservations;
  private final CanonicalInvoiceRepository invoices;
  private final CanonicalInvoiceLineRepository invoiceLines;
  private final CanonicalLabourEntryRepository labour;
  private final CanonicalStockCountRepository stockCounts;
  private final CanonicalWastageRepository wastage;
  private final CanonicalSaleItemRepository saleItems;
  private final CanonicalShiftRepository shifts;
  private final CanonicalPaymentRepository payments;
  private final CanonicalDeletedSaleRepository deletedSales;

  public CanonicalBrowseQuery(
      CanonicalDailySalesRepository dailySales,
      CanonicalProductSalesRepository productSales,
      CanonicalReservationRepository reservations,
      CanonicalInvoiceRepository invoices,
      CanonicalInvoiceLineRepository invoiceLines,
      CanonicalLabourEntryRepository labour,
      CanonicalStockCountRepository stockCounts,
      CanonicalWastageRepository wastage,
      CanonicalSaleItemRepository saleItems,
      CanonicalShiftRepository shifts,
      CanonicalPaymentRepository payments,
      CanonicalDeletedSaleRepository deletedSales) {
    this.dailySales = dailySales;
    this.productSales = productSales;
    this.reservations = reservations;
    this.invoices = invoices;
    this.invoiceLines = invoiceLines;
    this.labour = labour;
    this.stockCounts = stockCounts;
    this.wastage = wastage;
    this.saleItems = saleItems;
    this.shifts = shifts;
    this.payments = payments;
    this.deletedSales = deletedSales;
  }

  public List<EntityDescriptor> entities() {
    return List.of(
        new EntityDescriptor("daily_sales", "Daily sales", false),
        new EntityDescriptor("product_sales", "Product sales", false),
        new EntityDescriptor("reservation", "Reservations", false),
        new EntityDescriptor("invoice", "Invoices", false),
        new EntityDescriptor("invoice_line", "Invoice lines", false),
        new EntityDescriptor("labour_entry", "Labour entries", false),
        new EntityDescriptor("stock_count", "Stock counts", false),
        new EntityDescriptor("wastage", "Wastage", false),
        new EntityDescriptor("sale_item", "Sale items", false),
        new EntityDescriptor("payment", "Payments", false),
        new EntityDescriptor("deleted_sale", "Deleted orders", false),
        new EntityDescriptor("shift", "Shifts", true));
  }

  public DataPage<GenericRow> listRows(String entityId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pr =
        PageRequest.of(
            Math.max(page, 0),
            safeSize,
            Sort.by(Sort.Direction.DESC, "recordedAt").and(Sort.by("id")));
    return switch (entityId) {
      case "daily_sales" ->
          page(dailySales.findAll(pr), page, safeSize, CanonicalBrowseQuery::dailySalesColumns);
      case "product_sales" ->
          page(productSales.findAll(pr), page, safeSize, CanonicalBrowseQuery::productSalesColumns);
      case "reservation" ->
          page(reservations.findAll(pr), page, safeSize, CanonicalBrowseQuery::reservationColumns);
      case "invoice" ->
          page(invoices.findAll(pr), page, safeSize, CanonicalBrowseQuery::invoiceColumns);
      case "invoice_line" ->
          page(invoiceLines.findAll(pr), page, safeSize, CanonicalBrowseQuery::invoiceLineColumns);
      case "labour_entry" ->
          page(labour.findAll(pr), page, safeSize, CanonicalBrowseQuery::labourColumns);
      case "stock_count" ->
          page(stockCounts.findAll(pr), page, safeSize, CanonicalBrowseQuery::stockCountColumns);
      case "wastage" ->
          page(wastage.findAll(pr), page, safeSize, CanonicalBrowseQuery::wastageColumns);
      case "sale_item" ->
          page(saleItems.findAll(pr), page, safeSize, CanonicalBrowseQuery::saleItemColumns);
      case "payment" ->
          page(payments.findAll(pr), page, safeSize, CanonicalBrowseQuery::paymentColumns);
      case "deleted_sale" ->
          page(deletedSales.findAll(pr), page, safeSize, CanonicalBrowseQuery::deletedSaleColumns);
      case "shift" -> page(shifts.findAll(pr), page, safeSize, CanonicalBrowseQuery::shiftColumns);
      default ->
          throw new java.util.NoSuchElementException("Unknown canonical entity: " + entityId);
    };
  }

  public DataPage<GenericRow> listRows(
      String entityId, int page, int size, com.goldys.platform.semantic.RecordSort sort) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pr =
        PageRequest.of(
            Math.max(page, 0),
            safeSize,
            Sort.by(Sort.Direction.DESC, "recordedAt").and(Sort.by("id")));
    return switch (entityId) {
      case "daily_sales" ->
          page(
              sortedPage(
                  CanonicalDailySales.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("trading_date", "e.tradingDate"),
                      Map.entry("total_sales", "e.totalSales"),
                      Map.entry("gst_total", "e.gstTotal"),
                      Map.entry("net_total", "e.netTotal")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::dailySalesColumns);
      case "product_sales" ->
          page(
              sortedPage(
                  CanonicalProductSales.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("trading_date", "e.tradingDate"),
                      Map.entry("product_name_key", "e.productNameKey"),
                      Map.entry("quantity_sold", "e.quantitySold"),
                      Map.entry("amount", "e.amount")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::productSalesColumns);
      case "reservation" ->
          page(
              sortedPage(
                  CanonicalReservation.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("reservation_at", "e.reservationAt"),
                      Map.entry("party_size", "e.partySize"),
                      Map.entry("status", "e.status")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::reservationColumns);
      case "invoice" ->
          page(
              sortedPage(
                  CanonicalInvoice.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("supplier_name", "e.supplierName"),
                      Map.entry("invoice_number", "e.invoiceNumber"),
                      Map.entry("invoice_date", "e.invoiceDate"),
                      Map.entry("due_date", "e.dueDate"),
                      Map.entry("total_amount", "e.totalAmount"),
                      Map.entry("purchase_number", "e.purchaseNumber"),
                      Map.entry("pdf_filename", "e.pdfFilename"),
                      Map.entry("account_number", "e.accountNumber"),
                      Map.entry("tax_code", "e.taxCode"),
                      Map.entry("amount_ex_tax", "e.amountExTax"),
                      Map.entry("gst_amount", "e.gstAmount"),
                      Map.entry("freight_amount", "e.freightAmount"),
                      Map.entry("freight_gst_amount", "e.freightGstAmount")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::invoiceColumns);
      case "invoice_line" ->
          page(
              sortedPage(
                  CanonicalInvoiceLine.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("invoice_number", "e.invoiceNumber"),
                      Map.entry("invoice_date", "e.invoiceDate"),
                      Map.entry("product_name_key", "e.productNameKey"),
                      Map.entry("stock_code", "e.stockCode"),
                      Map.entry("quantity", "e.quantity"),
                      Map.entry("unit_cost", "e.unitCost"),
                      Map.entry("line_total", "e.lineTotal"),
                      Map.entry("category", "e.category"),
                      Map.entry("uom", "e.uom"),
                      Map.entry("unit_quantity", "e.unitQuantity"),
                      Map.entry("pack_size", "e.packSize"),
                      Map.entry("wet_amount", "e.wetAmount")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::invoiceLineColumns);
      case "labour_entry" ->
          page(
              sortedPage(
                  CanonicalLabourEntry.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("staff_ref", "e.staffRef"),
                      Map.entry("department", "e.department"),
                      Map.entry("labour_date", "e.labourDate"),
                      Map.entry("scheduled_hours", "e.scheduledHours"),
                      Map.entry("actual_hours", "e.actualHours"),
                      Map.entry("scheduled_cost", "e.scheduledCost"),
                      Map.entry("actual_cost", "e.actualCost"),
                      Map.entry("shift_start", "e.shiftStart"),
                      Map.entry("shift_end", "e.shiftEnd")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::labourColumns);
      case "stock_count" ->
          page(
              sortedPage(
                  CanonicalStockCount.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("counted_date", "e.countedDate"),
                      Map.entry("product_name_key", "e.productNameKey"),
                      Map.entry("quantity_on_hand", "e.quantityOnHand")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::stockCountColumns);
      case "wastage" ->
          page(
              sortedPage(
                  CanonicalWastage.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("wastage_date", "e.wastageDate"),
                      Map.entry("product_name_key", "e.productNameKey"),
                      Map.entry("quantity", "e.quantity"),
                      Map.entry("reason", "e.reason")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::wastageColumns);
      case "sale_item" ->
          page(
              sortedPage(
                  CanonicalSaleItem.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("item_name", "e.itemName"),
                      Map.entry("quantity_sold", "e.quantitySold"),
                      Map.entry("amount", "e.amount"),
                      Map.entry("sale_number", "e.saleNumber"),
                      Map.entry("category_name", "e.categoryName"),
                      Map.entry("product_number", "e.productNumber"),
                      Map.entry("sku", "e.sku"),
                      Map.entry("sold_price_inc_tax", "e.soldPriceIncTax"),
                      Map.entry("total_tax", "e.totalTax"),
                      Map.entry("cost_inc_tax", "e.costIncTax"),
                      Map.entry("order_type", "e.orderType"),
                      Map.entry("sale_type", "e.saleType"),
                      Map.entry("staff_name", "e.staffName"),
                      Map.entry("register_name", "e.registerName"),
                      Map.entry("table_number", "e.tableNumber")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::saleItemColumns);
      case "payment" ->
          page(
              sortedPage(
                  CanonicalPayment.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("trading_date", "e.tradingDate"),
                      Map.entry("sale_number", "e.saleNumber"),
                      Map.entry("payment_type_name", "e.paymentTypeName"),
                      Map.entry("amount", "e.amount"),
                      Map.entry("tip", "e.tip"),
                      Map.entry("payment_count", "e.paymentCount")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::paymentColumns);
      case "deleted_sale" ->
          page(
              sortedPage(
                  CanonicalDeletedSale.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("trading_date", "e.tradingDate"),
                      Map.entry("sale_number", "e.saleNumber"),
                      Map.entry("total_inc_tax", "e.totalIncTax"),
                      Map.entry("total_tax", "e.totalTax")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::deletedSaleColumns);
      case "shift" ->
          page(
              sortedPage(
                  CanonicalShift.class,
                  pr,
                  sort,
                  Map.ofEntries(
                      Map.entry("id", "e.id"),
                      Map.entry("logical_entity_id", "e.logicalEntityId"),
                      Map.entry("source_system", "e.sourceSystem"),
                      Map.entry("source_record_ref", "e.sourceRecordRef"),
                      Map.entry("raw_record_id", "e.rawRecordId"),
                      Map.entry("valid_from", "e.validFrom"),
                      Map.entry("valid_to", "e.validTo"),
                      Map.entry("recorded_at", "e.recordedAt"),
                      Map.entry("superseded_at", "e.supersededAt"),
                      Map.entry("staff_member_ref", "e.staffMemberRef"),
                      Map.entry("shift_start", "e.shiftStart"),
                      Map.entry("shift_end", "e.shiftEnd")),
                  "e.id asc"),
              page,
              safeSize,
              CanonicalBrowseQuery::shiftColumns);
      default ->
          throw new java.util.NoSuchElementException("Unknown canonical entity: " + entityId);
    };
  }

  private static <T extends BitemporalEntity> DataPage<GenericRow> page(
      Page<T> result, int page, int size, Function<T, Map<String, String>> columns) {
    return new DataPage<>(
        result.getContent().stream().map(e -> row(e, columns)).toList(),
        result.getTotalElements(),
        page,
        size);
  }

  private static <T extends BitemporalEntity> GenericRow row(
      T entity, Function<T, Map<String, String>> columns) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("id", entity.id().toString());
    c.put("logical_entity_id", entity.logicalEntityId().toString());
    c.put("source_system", entity.sourceSystem());
    c.put("source_record_ref", entity.sourceRecordRef());
    c.put("raw_record_id", entity.rawRecordId().toString());
    c.put("valid_from", entity.validFrom().toString());
    if (entity.validTo() != null) {
      c.put("valid_to", entity.validTo().toString());
    }
    c.put("recorded_at", entity.recordedAt().toString());
    if (entity.supersededAt() != null) {
      c.put("superseded_at", entity.supersededAt().toString());
    }
    c.putAll(columns.apply(entity));
    return new GenericRow(entity.id().toString(), c);
  }

  private static Map<String, String> dailySalesColumns(CanonicalDailySales s) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", s.tradingDate().toString());
    c.put("total_sales", s.totalSales() == null ? null : s.totalSales().toPlainString());
    c.put("gst_total", s.gstTotal() == null ? null : s.gstTotal().toPlainString());
    c.put("net_total", s.netTotal() == null ? null : s.netTotal().toPlainString());
    return c;
  }

  private static Map<String, String> productSalesColumns(CanonicalProductSales s) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", s.tradingDate().toString());
    c.put("product_name_key", s.productNameKey());
    c.put("quantity_sold", s.quantitySold().toPlainString());
    c.put("amount", s.amount().toPlainString());
    return c;
  }

  private static Map<String, String> reservationColumns(CanonicalReservation r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("reservation_at", r.reservationAt().toString());
    c.put("party_size", String.valueOf(r.partySize()));
    c.put("status", r.status());
    return c;
  }

  private static Map<String, String> invoiceColumns(CanonicalInvoice i) {
    Map<String, String> c = new LinkedHashMap<>();
    if (i.supplierName() != null) {
      c.put("supplier_name", i.supplierName());
    }
    c.put("invoice_number", i.invoiceNumber());
    c.put("invoice_date", i.invoiceDate().toString());
    if (i.dueDate() != null) {
      c.put("due_date", i.dueDate().toString());
    }
    if (i.totalAmount() != null) {
      c.put("total_amount", i.totalAmount().toPlainString());
    }
    if (i.purchaseNumber() != null) {
      c.put("purchase_number", i.purchaseNumber());
    }
    if (i.pdfFilename() != null) {
      c.put("pdf_filename", i.pdfFilename());
    }
    if (i.accountNumber() != null) {
      c.put("account_number", i.accountNumber());
    }
    if (i.taxCode() != null) {
      c.put("tax_code", i.taxCode());
    }
    if (i.amountExTax() != null) {
      c.put("amount_ex_tax", i.amountExTax().toPlainString());
    }
    if (i.gstAmount() != null) {
      c.put("gst_amount", i.gstAmount().toPlainString());
    }
    if (i.freightAmount() != null) {
      c.put("freight_amount", i.freightAmount().toPlainString());
    }
    if (i.freightGstAmount() != null) {
      c.put("freight_gst_amount", i.freightGstAmount().toPlainString());
    }
    return c;
  }

  private static Map<String, String> invoiceLineColumns(CanonicalInvoiceLine l) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("invoice_number", l.invoiceNumber());
    c.put("invoice_date", l.invoiceDate().toString());
    c.put("product_name_key", l.productNameKey());
    if (l.stockCode() != null) {
      c.put("stock_code", l.stockCode());
    }
    c.put("quantity", l.quantity() == null ? null : l.quantity().toPlainString());
    c.put("unit_cost", l.unitCost() == null ? null : l.unitCost().toPlainString());
    c.put("line_total", l.lineTotal().toPlainString());
    if (l.category() != null) {
      c.put("category", l.category());
    }
    if (l.uom() != null) {
      c.put("uom", l.uom());
    }
    if (l.unitQuantity() != null) {
      c.put("unit_quantity", l.unitQuantity().toPlainString());
    }
    if (l.packSize() != null) {
      c.put("pack_size", l.packSize().toPlainString());
    }
    if (l.wetAmount() != null) {
      c.put("wet_amount", l.wetAmount().toPlainString());
    }
    return c;
  }

  private static Map<String, String> labourColumns(CanonicalLabourEntry e) {
    Map<String, String> c = new LinkedHashMap<>();
    if (e.staffRef() != null) {
      c.put("staff_ref", e.staffRef());
    }
    c.put("department", e.department());
    c.put("labour_date", e.labourDate().toString());
    c.put("scheduled_hours", e.scheduledHours().toPlainString());
    c.put("actual_hours", e.actualHours().toPlainString());
    if (e.scheduledCost() != null) {
      c.put("scheduled_cost", e.scheduledCost().toPlainString());
    }
    if (e.actualCost() != null) {
      c.put("actual_cost", e.actualCost().toPlainString());
    }
    if (e.shiftStart() != null) {
      c.put("shift_start", e.shiftStart().toString());
    }
    if (e.shiftEnd() != null) {
      c.put("shift_end", e.shiftEnd().toString());
    }
    return c;
  }

  private static Map<String, String> stockCountColumns(CanonicalStockCount s) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("counted_date", s.countedDate().toString());
    c.put("product_name_key", s.productNameKey());
    c.put(
        "quantity_on_hand", s.quantityOnHand() == null ? null : s.quantityOnHand().toPlainString());
    return c;
  }

  private static Map<String, String> wastageColumns(CanonicalWastage w) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("wastage_date", w.wastageDate().toString());
    c.put("product_name_key", w.productNameKey());
    c.put("quantity", w.quantity().toPlainString());
    if (w.reason() != null) {
      c.put("reason", w.reason());
    }
    return c;
  }

  static Map<String, String> saleItemColumns(CanonicalSaleItem i) {
    Map<String, String> c = new LinkedHashMap<>();
    if (i.itemName() != null) {
      c.put("item_name", i.itemName());
    }
    if (i.quantitySold() != null) {
      c.put("quantity_sold", i.quantitySold().toPlainString());
    }
    if (i.amount() != null) {
      c.put("amount", i.amount().toPlainString());
    }
    if (i.saleNumber() != null) {
      c.put("sale_number", i.saleNumber());
    }
    if (i.categoryName() != null) {
      c.put("category_name", i.categoryName());
    }
    if (i.productNumber() != null) {
      c.put("product_number", i.productNumber());
    }
    if (i.sku() != null) {
      c.put("sku", i.sku());
    }
    if (i.soldPriceIncTax() != null) {
      c.put("sold_price_inc_tax", i.soldPriceIncTax().toPlainString());
    }
    if (i.totalTax() != null) {
      c.put("total_tax", i.totalTax().toPlainString());
    }
    if (i.costIncTax() != null) {
      c.put("cost_inc_tax", i.costIncTax().toPlainString());
    }
    if (i.orderType() != null) {
      c.put("order_type", i.orderType());
    }
    if (i.saleType() != null) {
      c.put("sale_type", i.saleType());
    }
    if (i.staffName() != null) {
      c.put("staff_name", i.staffName());
    }
    if (i.registerName() != null) {
      c.put("register_name", i.registerName());
    }
    if (i.tableNumber() != null) {
      c.put("table_number", i.tableNumber());
    }
    return c;
  }

  static Map<String, String> paymentColumns(CanonicalPayment p) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", p.tradingDate().toString());
    c.put("sale_number", p.saleNumber());
    if (p.paymentTypeName() != null) {
      c.put("payment_type_name", p.paymentTypeName());
    }
    c.put("amount", p.amount().toPlainString());
    c.put("tip", p.tip().toPlainString());
    c.put("payment_count", String.valueOf(p.paymentCount()));
    return c;
  }

  static Map<String, String> deletedSaleColumns(CanonicalDeletedSale d) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", d.tradingDate().toString());
    c.put("sale_number", d.saleNumber());
    c.put("total_inc_tax", d.totalIncTax().toPlainString());
    c.put("total_tax", d.totalTax().toPlainString());
    return c;
  }

  private static Map<String, String> shiftColumns(CanonicalShift s) {
    Map<String, String> c = new LinkedHashMap<>();
    if (s.staffMemberRef() != null) {
      c.put("staff_member_ref", s.staffMemberRef().toString());
    }
    if (s.shiftStart() != null) {
      c.put("shift_start", s.shiftStart().toString());
    }
    if (s.shiftEnd() != null) {
      c.put("shift_end", s.shiftEnd().toString());
    }
    return c;
  }

  private <T> Page<T> sortedPage(
      Class<T> type,
      PageRequest page,
      com.goldys.platform.semantic.RecordSort sort,
      Map<String, String> fields,
      String ties) {
    String order = sort.orderBy(fields, ties);
    var rows =
        entityManager
            .createQuery("select e from " + type.getSimpleName() + " e order by " + order, type)
            .setFirstResult(Math.toIntExact(page.getOffset()))
            .setMaxResults(page.getPageSize())
            .getResultList();
    long count =
        entityManager
            .createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class)
            .getSingleResult();
    return new org.springframework.data.domain.PageImpl<>(rows, page, count);
  }
}
