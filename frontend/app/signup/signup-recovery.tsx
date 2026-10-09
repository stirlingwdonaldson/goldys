import Link from "next/link";
import { AuthFeedback } from "@/components/auth/auth-feedback";
import { isApiError } from "@/lib/api";

/** A failed response must never turn a possibly committed creation into another POST. */
export function SignupRecovery({ outcome, submitting, error }: {
  outcome: "created" | "uncertain";
  submitting: boolean;
  error: unknown;
}) {
  return (
    <div className="space-y-4">
      <p role="status">{submitting ? "Signing in…" : outcome === "created"
        ? "Account created. Sign in to continue."
        : "Account creation could not be confirmed. Sign in before trying again."}</p>
      <AuthFeedback error={error} message={outcome === "uncertain" && isNetworkFailure(error)
        ? "Couldn't confirm account creation. If you already created an account, sign in." : undefined} />
      {!submitting ? <Link href="/login" className="underline">Sign in</Link> : null}
    </div>
  );
}

function isNetworkFailure(error: unknown): boolean {
  return isApiError(error) && error.code === "NETWORK_ERROR";
}
