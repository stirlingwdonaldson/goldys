import Link from "next/link";
import { AuthFeedback } from "@/components/auth/auth-feedback";

/** Creation is confirmed even when the following automatic sign-in fails. */
export function AccountCreated({ submitting, error }: { submitting: boolean; error: unknown }) {
  return (
    <div className="space-y-4">
      <p role="status">{submitting ? "Signing in…" : "Account created. Sign in to continue."}</p>
      <AuthFeedback error={error} />
      {!submitting ? <Link href="/login" className="underline">Sign in</Link> : null}
    </div>
  );
}
