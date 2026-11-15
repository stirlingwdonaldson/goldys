package com.goldys.platform.canonical;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.DeletedSaleRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical deleted sales, so modules outside this package never touch
 * the package-private entity or repository directly.
 */
@Service
public class CanonicalDeletedSaleQuery {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  private static final int MAX_PAGE_SIZE = 200;

  private final CanonicalDeletedSaleRepository repository;

  public CanonicalDeletedSaleQuery(CanonicalDeletedSaleRepository repository) {
    this.repository = repository;
  }

  /** All current deleted sales, mapped to views. */
  public List<DeletedSaleView> currentDeletedSales() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current deleted sales whose trading date falls in {@code dates}. */
  public List<DeletedSaleView> currentDeletedSalesForDates(Collection<LocalDate> dates) {
    return repository.findCurrentByTradingDateIn(dates).stream().map(this::toView).toList();
  }

  /**
   * Paged, filterable listing of current deleted sales over the inclusive date range. A null sale
   * number matches anything; rows come back newest first.
   */
  public DataPage<DeletedSaleRow> page(
      String saleNumber, LocalDate from, LocalDate to, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    var result =
        repository.findCurrent(from, to, saleNumber, PageRequest.of(Math.max(page, 0), safeSize));
    return new DataPage<>(
        result.getContent().stream().map(this::toRow).toList(),
        result.getTotalElements(),
        Math.max(page, 0),
        safeSize);
  }

  public DataPage<DeletedSaleRow> page(
      String saleNumber,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      com.goldys.platform.semantic.RecordSort sort) {
    int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    var fields =
        java.util.Map.ofEntries(
            java.util.Map.entry("tradingDate", "p.tradingDate"),
            java.util.Map.entry("saleNumber", "p.saleNumber"),
            java.util.Map.entry("orderType", "p.orderType"),
            java.util.Map.entry("note", "p.note"),
            java.util.Map.entry("staffName", "p.staffName"),
            java.util.Map.entry("deletedByStaffName", "p.deletedByStaffName"),
            java.util.Map.entry("deletedRegisterName", "p.deletedRegisterName"),
            java.util.Map.entry("tableNumber", "p.tableNumber"),
            java.util.Map.entry("totalIncTax", "p.totalIncTax"),
            java.util.Map.entry("totalExTax", "p.totalExTax"),
            java.util.Map.entry("totalTax", "p.totalTax"),
            java.util.Map.entry("totalCost", "p.totalCost"));
    String where =
        " where p.supersededAt is null and p.tradingDate >= :from and p.tradingDate <= :to and (:saleNumber is null or p.saleNumber = :saleNumber)";
    var query =
        entityManager.createQuery(
            "select p from CanonicalDeletedSale p"
                + where
                + " order by "
                + sort.orderBy(fields, "p.id asc"),
            CanonicalDeletedSale.class);
    var count =
        entityManager.createQuery(
            "select count(p) from CanonicalDeletedSale p" + where, Long.class);
    query.setParameter("from", from).setParameter("to", to).setParameter("saleNumber", saleNumber);
    count.setParameter("from", from).setParameter("to", to).setParameter("saleNumber", saleNumber);
    var rows =
        query
            .setFirstResult(Math.toIntExact((long) safePage * safeSize))
            .setMaxResults(safeSize)
            .getResultList();
    return new DataPage<>(
        rows.stream().map(this::toRow).toList(), count.getSingleResult(), safePage, safeSize);
  }

  private DeletedSaleView toView(CanonicalDeletedSale d) {
    return new DeletedSaleView(d.tradingDate(), d.totalIncTax(), d.totalTax());
  }

  private DeletedSaleRow toRow(CanonicalDeletedSale d) {
    return new DeletedSaleRow(
        d.tradingDate(),
        d.saleNumber(),
        d.orderType(),
        d.note(),
        d.totalIncTax(),
        d.totalExTax(),
        d.totalTax(),
        d.totalCost(),
        d.openedRegisterName(),
        d.deletedRegisterName(),
        d.staffName(),
        d.deletedByStaffName(),
        d.tableNumber(),
        d.siteId(),
        d.customerName());
  }
}
