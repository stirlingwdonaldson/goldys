package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalBrowseQuery;
import com.goldys.platform.ingestion.RawRecordBrowseQuery;
import com.goldys.platform.semantic.DataExplorerQuery;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * {@link DataExplorerQuery} implementation: the explorer's registry. Raw and canonical browsing are
 * delegated to their package facades; the resolved half reads this package's own projection
 * repositories directly and maps to generic rows. This is the one place a new resolved domain is
 * registered.
 */
@Service
public class DataExplorerQueryImpl implements DataExplorerQuery {
  private static final int MAX_PAGE_SIZE = 200;

  private final RawRecordBrowseQuery raw;
  private final CanonicalBrowseQuery canonical;
  private final ResolvedDailySalesRepository dailySales;
  private final ResolvedProductSalesRepository productSales;
  private final ResolvedReservationDayRepository reservations;
  private final ResolvedLabourDayRepository labour;
  private final ResolvedInventoryDayRepository inventory;
  private final ResolvedPaymentDayRepository payments;
  private final ResolvedDeletedSaleDayRepository deletedSales;
  private final ResolvedSaleItemDayRepository saleItems;

  public DataExplorerQueryImpl(
      RawRecordBrowseQuery raw,
      CanonicalBrowseQuery canonical,
      ResolvedDailySalesRepository dailySales,
      ResolvedProductSalesRepository productSales,
      ResolvedReservationDayRepository reservations,
      ResolvedLabourDayRepository labour,
      ResolvedInventoryDayRepository inventory,
      ResolvedPaymentDayRepository payments,
      ResolvedDeletedSaleDayRepository deletedSales,
      ResolvedSaleItemDayRepository saleItems) {
    this.raw = raw;
    this.canonical = canonical;
    this.dailySales = dailySales;
    this.productSales = productSales;
    this.reservations = reservations;
    this.labour = labour;
    this.inventory = inventory;
    this.payments = payments;
    this.deletedSales = deletedSales;
    this.saleItems = saleItems;
  }

  @Override
  public DataPage<RawRecordSummary> listRaw(RawFilter filter, int page, int size) {
    return raw.list(filter, page, size);
  }

  @Override
  public RawRecordDetail rawDetail(UUID id) {
    return raw.byId(id);
  }

  @Override
  public List<EntityDescriptor> canonicalEntities() {
    return canonical.entities();
  }

  @Override
  public DataPage<GenericRow> canonicalRows(String entityId, int page, int size) {
    return canonical.listRows(entityId, page, size);
  }

  @Override
  public List<EntityDescriptor> resolvedDomains() {
    return List.of(
        new EntityDescriptor("resolved_daily_sales", "Daily sales", false),
        new EntityDescriptor("resolved_product_sales", "Product sales", false),
        new EntityDescriptor("resolved_reservation_day", "Reservation day", false),
        new EntityDescriptor("resolved_labour_day", "Labour day", false),
        new EntityDescriptor("resolved_inventory_day", "Inventory day", false),
        new EntityDescriptor("resolved_payment_day", "Payments", false),
        new EntityDescriptor("resolved_deleted_sale_day", "Deleted orders", false),
        new EntityDescriptor("resolved_sale_item_day", "Sale items", false));
  }

  @Override
  public DataPage<GenericRow> resolvedRows(String domainId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pr =
        PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "tradingDate"));
    return switch (domainId) {
      case "resolved_daily_sales" ->
          page(dailySales.findAll(pr), page, safeSize, DataExplorerQueryImpl::dailySalesRow);
      case "resolved_product_sales" ->
          page(productSales.findAll(pr), page, safeSize, DataExplorerQueryImpl::productSalesRow);
      case "resolved_reservation_day" ->
          page(reservations.findAll(pr), page, safeSize, DataExplorerQueryImpl::reservationRow);
      case "resolved_labour_day" ->
          page(labour.findAll(pr), page, safeSize, DataExplorerQueryImpl::labourRow);
      case "resolved_inventory_day" ->
          page(inventory.findAll(pr), page, safeSize, DataExplorerQueryImpl::inventoryRow);
      case "resolved_payment_day" ->
          page(payments.findAll(pr), page, safeSize, DataExplorerQueryImpl::paymentRow);
      case "resolved_deleted_sale_day" ->
          page(deletedSales.findAll(pr), page, safeSize, DataExplorerQueryImpl::deletedSaleRow);
      case "resolved_sale_item_day" ->
          page(saleItems.findAll(pr), page, safeSize, DataExplorerQueryImpl::saleItemRow);
      default -> throw new java.util.NoSuchElementException("Unknown resolved domain: " + domainId);
    };
  }

  private static <T> DataPage<GenericRow> page(
      Page<T> result, int page, int size, Function<T, GenericRow> mapper) {
    return new DataPage<>(
        result.getContent().stream().map(mapper).toList(), result.getTotalElements(), page, size);
  }

  private static GenericRow row(String id, Map<String, String> columns) {
    return new GenericRow(id, columns);
  }

  private static void resolvedCommon(
      Map<String, String> c, String type, String source, boolean conflict, java.time.Instant at) {
    c.put("resolution_type", type);
    if (source != null) {
      c.put("authoritative_source", source);
    }
    c.put("has_conflict", String.valueOf(conflict));
    c.put("resolved_at", at.toString());
  }

  private static GenericRow dailySalesRow(ResolvedDailySales r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    if (r.totalSales() != null) {
      c.put("total_sales", r.totalSales().toPlainString());
    }
    if (r.netSales() != null) {
      c.put("net_sales", r.netSales().toPlainString());
    }
    if (r.gst() != null) {
      c.put("gst", r.gst().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate().toString(), c);
  }

  private static GenericRow productSalesRow(ResolvedProductSales r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("product_name_key", r.productNameKey());
    if (r.quantitySold() != null) {
      c.put("quantity_sold", r.quantitySold().toPlainString());
    }
    if (r.amount() != null) {
      c.put("amount", r.amount().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate() + "|" + r.productNameKey(), c);
  }

  private static GenericRow reservationRow(ResolvedReservationDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("service_period", r.servicePeriod());
    c.put("bookings", String.valueOf(r.bookings()));
    c.put("attended", String.valueOf(r.attended()));
    c.put("covers", String.valueOf(r.covers()));
    c.put("cancelled", String.valueOf(r.cancelled()));
    c.put("no_shows", String.valueOf(r.noShows()));
    c.put("walk_ins", String.valueOf(r.walkIns()));
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate() + "|" + r.servicePeriod(), c);
  }

  private static GenericRow labourRow(ResolvedLabourDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("department", r.department());
    if (r.scheduledHours() != null) {
      c.put("scheduled_hours", r.scheduledHours().toPlainString());
    }
    if (r.actualHours() != null) {
      c.put("actual_hours", r.actualHours().toPlainString());
    }
    if (r.scheduledCost() != null) {
      c.put("scheduled_cost", r.scheduledCost().toPlainString());
    }
    if (r.actualCost() != null) {
      c.put("actual_cost", r.actualCost().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate() + "|" + r.department(), c);
  }

  private static GenericRow inventoryRow(ResolvedInventoryDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    if (r.purchases() != null) {
      c.put("purchases", r.purchases().toPlainString());
    }
    if (r.wastage() != null) {
      c.put("wastage", r.wastage().toPlainString());
    }
    if (r.stockOnHand() != null) {
      c.put("stock_on_hand", r.stockOnHand().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate().toString(), c);
  }

  static GenericRow paymentRow(ResolvedPaymentDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("payment_type_name", r.paymentTypeName());
    if (r.amount() != null) {
      c.put("amount", r.amount().toPlainString());
    }
    if (r.tip() != null) {
      c.put("tip", r.tip().toPlainString());
    }
    c.put("payment_count", String.valueOf(r.paymentCount()));
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate() + "|" + r.paymentTypeName(), c);
  }

  static GenericRow deletedSaleRow(ResolvedDeletedSaleDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("deleted_count", String.valueOf(r.deletedCount()));
    if (r.totalIncTax() != null) {
      c.put("total_inc_tax", r.totalIncTax().toPlainString());
    }
    if (r.totalTax() != null) {
      c.put("total_tax", r.totalTax().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate().toString(), c);
  }

  static GenericRow saleItemRow(ResolvedSaleItemDay r) {
    Map<String, String> c = new LinkedHashMap<>();
    c.put("trading_date", r.tradingDate().toString());
    c.put("category_name", r.categoryName());
    if (r.quantity() != null) {
      c.put("quantity", r.quantity().toPlainString());
    }
    if (r.amount() != null) {
      c.put("amount", r.amount().toPlainString());
    }
    resolvedCommon(c, r.resolutionType(), r.authoritativeSource(), r.hasConflict(), r.resolvedAt());
    return row(r.tradingDate() + "|" + r.categoryName(), c);
  }
}
