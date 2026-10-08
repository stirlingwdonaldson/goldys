package com.goldys.platform.ingestion;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors ingestion failures into Sentry so a broken connector raises an alert instead of waiting
 * for someone to open the data-health screen.
 *
 * <p>This is a notification channel only. The {@code IngestionFailure} ledger row stays the record
 * of truth (architecture invariant: connector failures go to the ledger, not silence); nothing here
 * replaces or gates that write. With no DSN configured the Sentry calls are no-ops.
 *
 * <p>Privacy: an unclassified exception's message may contain a token-bearing URL or a fragment of
 * payload (see {@link ConnectorRunner}). Only the exception <em>class</em> and its stack frames
 * (class/method/line, never values) are sent for those. A {@code ConnectorFetchException} message
 * is operator-facing text that the ledger contract already forbids from carrying payload or
 * credentials, so it is sent as-is.
 */
final class IngestionFailureReporter {
  private IngestionFailureReporter() {}

  /** A classified connector failure whose detail is safe operator-facing text. */
  static void reportClassified(
      UUID runId, String sourceSystem, String failureType, String detail, Throwable source) {
    report(runId, sourceSystem, failureType, failureType + ": " + detail, source);
  }

  /** An unclassified failure: message withheld, class name and frames kept. */
  static void reportUnexpected(UUID runId, String sourceSystem, Throwable source) {
    report(runId, sourceSystem, "UNEXPECTED", source.getClass().getName(), source);
  }

  private static void report(
      UUID runId, String sourceSystem, String failureType, String message, Throwable source) {
    try {
      SanitizedIngestionFailure event =
          new SanitizedIngestionFailure(
              "Ingestion failure [" + sourceSystem + "] " + message, source.getStackTrace());
      Sentry.captureException(
          event,
          scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("source_system", sourceSystem);
            scope.setTag("failure_type", failureType);
            // Group by connector + failure mode, not by stack trace: "CTB SESSION_EXPIRED" should
            // be one issue with a count, however many code paths produced it.
            scope.setFingerprint(List.of("ingestion-failure", sourceSystem, failureType));
            scope.setContexts("ingestion", Map.of("run_id", String.valueOf(runId)));
          });
    } catch (RuntimeException ignored) {
      // Monitoring must never break ingestion; the ledger write has already happened or will.
    }
  }

  /**
   * Carries the original stack frames under a message we control. No cause is attached, so the
   * original (possibly sensitive) message never leaves the process.
   */
  static final class SanitizedIngestionFailure extends RuntimeException {
    SanitizedIngestionFailure(String message, StackTraceElement[] frames) {
      super(message, null, false, true);
      setStackTrace(frames);
    }
  }
}
