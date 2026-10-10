package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalPaymentQuery;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
import com.goldys.platform.semantic.PaymentRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Payments screen's read model. Authorizes the read and delegates to the resolved payment
 * semantic query; business metrics come only from the resolved projection.
 */
@Service
public class PaymentReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("payments.metrics");

  private final PaymentMetricsQuery metrics;
  private final CanonicalPaymentQuery payments;
  private final PermissionService permissions;

  public PaymentReportingService(
      PaymentMetricsQuery metrics, CanonicalPaymentQuery payments, PermissionService permissions) {
    this.metrics = metrics;
    this.payments = payments;
    this.permissions = permissions;
  }

  /** Resolved payment mix over the inclusive range. */
  public List<PaymentMix> dailyMix(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyMix(from, to);
  }

  /** Paged, filterable listing of current payment tenders (canonical browse surface). */
  public DataPage<PaymentRow> list(
      UserRole role,
      String paymentType,
      String saleNumber,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return payments.page(paymentType, saleNumber, from, to, page, size);
  }
}
