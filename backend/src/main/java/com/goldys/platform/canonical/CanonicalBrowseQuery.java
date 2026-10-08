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
      CanonicalShiftRepository shifts) {
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
        new EntityDescriptor("sale_item", "Sale items", true),
        new EntityDescriptor("shift", "Shifts", true));
  }

  public DataPage<GenericRow> listRows(String entityId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pr =
        PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "recordedAt"));
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
      case "shift" -> page(shifts.findAll(pr), page, safeSize, CanonicalBrowseQuery::shiftColumns);
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
    c.put("total_sales", s.totalSales().toPlainString());
    c.put("gst_total", s.gstTotal().toPlainString());
    c.put("net_total", s.netTotal().toPlainString());
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
    return c;
  }

  private static Map<String, String> invoiceLineColumns(CanonicalInvoiceLine l) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("invoice_number", l.invoiceNumber());
    c.put("invoice_date", l.invoiceDate().toString());
    c.put("product_name_key", l.productNameKey());
    c.put("quantity", l.quantity().toPlainString());
    c.put("unit_cost", l.unitCost().toPlainString());
    c.put("line_total", l.lineTotal().toPlainString());
    if (l.category() != null) {
      c.put("category", l.category());
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
    c.put("quantity_on_hand", s.quantityOnHand().toPlainString());
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

  private static Map<String, String> saleItemColumns(CanonicalSaleItem i) {
    Map<String, String> c = new LinkedHashMap<>();
    if (i.itemName() != null) {
      c.put("item_name", i.itemName());
    }
    if (i.quantitySold() != null) {
      c.put("quantity_sold", String.valueOf(i.quantitySold()));
    }
    if (i.amount() != null) {
      c.put("amount", i.amount().toPlainString());
    }
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
}
