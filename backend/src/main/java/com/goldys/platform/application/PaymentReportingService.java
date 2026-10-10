package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
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
  private final PermissionService permissions;

  public PaymentReportingService(PaymentMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** Resolved payment mix over the inclusive range. */
  public List<PaymentMix> dailyMix(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyMix(from, to);
  }
}
