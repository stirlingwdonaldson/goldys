package com.goldys.platform.canonical;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.PaymentRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    return repository.findCurrentByTradingDateIn(dates).stream().map(this::toView).toList();
  }

  /**
   * Sum of current payment amounts per trading date over an inclusive range, with a representative
   * raw-record id for provenance. Used to derive daily-sales totals for a window the Lightspeed
   * daily report did not cover (e.g. Q1 2026).
   */
  public List<PaymentDailyTotal> dailyTotals(LocalDate from, LocalDate to) {
    Map<LocalDate, MutableDailyTotal> byDate = new LinkedHashMap<>();
    for (CanonicalPayment p : repository.findCurrentByTradingDateBetween(from, to)) {
      byDate
          .computeIfAbsent(p.tradingDate(), d -> new MutableDailyTotal(p.rawRecordId()))
          .add(p.amount());
    }
    return byDate.entrySet().stream()
        .map(e -> new PaymentDailyTotal(e.getKey(), e.getValue().total, e.getValue().rawRecordId))
        .sorted(Comparator.comparing(PaymentDailyTotal::tradingDate))
        .toList();
  }

  /** A trading date's summed payment amount plus a representative raw record for provenance. */
  public record PaymentDailyTotal(
      LocalDate tradingDate, BigDecimal totalAmount, UUID rawRecordId) {}

  private static final class MutableDailyTotal {
    final UUID rawRecordId;
    BigDecimal total = BigDecimal.ZERO;

    MutableDailyTotal(UUID rawRecordId) {
      this.rawRecordId = rawRecordId;
    }

    void add(BigDecimal amount) {
      if (amount != null) {
        total = total.add(amount);
      }
    }
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
