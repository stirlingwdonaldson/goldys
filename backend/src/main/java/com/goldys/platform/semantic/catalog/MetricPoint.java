package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.MissingDataStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bucket's value. {@code value} is null when the bucket has no resolved data; {@code status}
 * then says why, so a gap is never silently rendered as zero. For a non-null value {@code status}
 * is {@link MissingDataStatus#ZERO} for a genuine zero and {@code null} for an ordinary value.
 */
public record MetricPoint(LocalDate bucketStart, BigDecimal value, MissingDataStatus status) {

  /** Convenience constructor deriving {@code status} from {@code value}. */
  public MetricPoint(LocalDate bucketStart, BigDecimal value) {
    this(bucketStart, value, statusFor(value));
  }

  private static MissingDataStatus statusFor(BigDecimal value) {
    if (value == null) {
      return MissingDataStatus.UNKNOWN;
    }
    return value.signum() == 0 ? MissingDataStatus.ZERO : null;
  }
}
