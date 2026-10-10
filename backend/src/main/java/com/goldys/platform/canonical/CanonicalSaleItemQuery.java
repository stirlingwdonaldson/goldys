package com.goldys.platform.canonical;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.SaleItemRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical sale items, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalSaleItemQuery {
  private static final int MAX_PAGE_SIZE = 200;

  private final CanonicalSaleItemRepository repository;

  public CanonicalSaleItemQuery(CanonicalSaleItemRepository repository) {
    this.repository = repository;
  }

  /** All current sale items, mapped to views. */
  public List<SaleItemView> currentSaleItems() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current sale items whose trading date falls in {@code dates}. */
  public List<SaleItemView> currentSaleItemsForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByTradingDateIn(dates).stream().map(this::toView).toList();
  }

  /**
   * Paged, filterable listing of current sale line items over the inclusive date range. Null
   * filters match anything; rows come back newest first, ordered by the unique receipt-line id
   * within a date.
   */
  public DataPage<SaleItemRow> page(
      String category, String saleNumber, LocalDate from, LocalDate to, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    var result =
        repository.findCurrent(
            from, to, category, saleNumber, PageRequest.of(Math.max(page, 0), safeSize));
    return new DataPage<>(
        result.getContent().stream().map(this::toRow).toList(),
        result.getTotalElements(),
        Math.max(page, 0),
        safeSize);
  }

  private SaleItemView toView(CanonicalSaleItem s) {
    return new SaleItemView(s.tradingDate(), s.categoryName(), s.quantitySold(), s.amount());
  }

  private SaleItemRow toRow(CanonicalSaleItem s) {
    return new SaleItemRow(
        s.tradingDate(),
        s.saleNumber(),
        s.sourceRecordRef(),
        s.itemName(),
        s.productNumber(),
        s.sku(),
        s.categoryName(),
        s.quantitySold(),
        s.amount(),
        s.soldPriceIncTax(),
        s.totalTax(),
        s.costIncTax(),
        s.orderType(),
        s.saleType(),
        s.staffName(),
        s.registerName(),
        s.tableNumber());
  }
}
