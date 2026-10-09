"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { isApiError, login, signup } from "@/lib/api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [correlationId, setCorrelationId] = useState<string | undefined>();
  const [accountCreated, setAccountCreated] = useState(false);
  const busy = useRef(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (busy.current || accountCreated) return;
    busy.current = true;
    setError(null);
    setCorrelationId(undefined);
    setSubmitting(true);
    let created = false;
    try {
      await signup({ email, displayName, password });
      created = true;
      setAccountCreated(true);
      // Auto-login with the same credentials, then land in the app.
      await login({ email, password });
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      const uncertain = !created && isApiError(err) && err.code === "NETWORK_ERROR";
      setError(uncertain ? "Couldn't confirm account creation. If you already created an account, sign in."
        : isApiError(err) ? err.message : "Something went wrong. Try again.");
      setCorrelationId(isApiError(err) ? err.correlationId : undefined);
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>
            New accounts start with basic access; an administrator can grant more later.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {accountCreated ? (
            <div className="space-y-4">
              <p role="status">{submitting ? "Signing in…" : "Account created. Sign in to continue."}</p>
              {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
              {correlationId ? <p className="break-words text-xs text-muted-foreground">Reference: {correlationId}</p> : null}
              {!submitting ? <Link href="/login" className="underline">Sign in</Link> : null}
            </div>
          ) : <form onSubmit={onSubmit} className="space-y-4">
            <Input
              aria-label="Full name"
              autoComplete="name"
              placeholder="Full name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
            />
            <Input
              type="email"
              aria-label="Work email"
              autoComplete="email"
              placeholder="Work email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <Input
              type="password"
              aria-label="Password"
              autoComplete="new-password"
              placeholder="Password (at least 8 characters)"
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
            {correlationId ? <p className="break-words text-xs text-muted-foreground">Reference: {correlationId}</p> : null}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Creating account…" : "Sign up"}
            </Button>
          </form>}
          {!accountCreated ? <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link href="/login" className="underline">
              Sign in
            </Link>
          </p> : null}
        </CardContent>
      </Card>
    </main>
  );
}
