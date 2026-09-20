"use client";

import { ErrorState } from "@/components/states/error-state";

/**
 * Next.js segment error boundary for the `(app)` group. Catches errors thrown
 * during server rendering of a page (which the client-side ScreenErrorBoundary
 * cannot reach) and renders the same calm, retryable inline state.
 */
export default function AppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorState
      title="This view failed to load"
      message={error.message || "An unexpected error occurred."}
      onRetry={reset}
    />
  );
}
