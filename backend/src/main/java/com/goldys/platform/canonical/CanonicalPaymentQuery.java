package com.goldys.platform.canonical;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.PaymentRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical payments, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalPaymentQuery {
  private static final int MAX_PAGE_SIZE = 200;

  private final CanonicalPaymentRepository repository;

  public CanonicalPaymentQuery(CanonicalPaymentRepository repository) {
    this.repository = repository;
  }

  /** All current payments, mapped to views. */
  public List<PaymentView> currentPayments() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  /** Current payments whose trading date falls in {@code dates}. */
  public List<PaymentView> currentPaymentsForDates(Collection<LocalDate> dates) {
    return currentPayments().stream().filter(v -> dates.contains(v.tradingDate())).toList();
  }

  /**
   * Paged, filterable listing of current payment tenders over the inclusive date range. Null
   * filters match anything; rows come back newest first.
   */
  public DataPage<PaymentRow> page(
      String paymentType, String saleNumber, LocalDate from, LocalDate to, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    var result =
        repository.findCurrent(
            from, to, paymentType, saleNumber, PageRequest.of(Math.max(page, 0), safeSize));
    return new DataPage<>(
        result.getContent().stream().map(this::toRow).toList(),
        result.getTotalElements(),
        Math.max(page, 0),
        safeSize);
  }

  private PaymentView toView(CanonicalPayment p) {
    return new PaymentView(
        p.tradingDate(), p.paymentTypeName(), p.amount(), p.tip(), p.paymentCount());
  }

  private PaymentRow toRow(CanonicalPayment p) {
    return new PaymentRow(
        p.tradingDate(),
        p.saleNumber(),
        p.paymentTypeName(),
        p.paymentTypeCode(),
        p.paymentSourceType(),
        p.lspayPaymentMode(),
        p.clearingAccount(),
        p.amount(),
        p.tip(),
        p.tendered(),
        p.surcharge(),
        p.paymentCount(),
        p.tipCount(),
        p.reconciled(),
        p.registerCode(),
        p.registerName(),
        p.staffName(),
        p.staffCode(),
        p.siteId(),
        p.customerName());
  }
}
