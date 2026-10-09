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
import { AuthFeedback } from "@/components/auth/auth-feedback";
import { AccountCreated } from "./account-created";

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [accountCreated, setAccountCreated] = useState(false);
  const busy = useRef(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (busy.current || accountCreated) return;
    busy.current = true;
    setError(null);
    setSubmitting(true);
    try {
      await signup({ email, displayName, password });
      setAccountCreated(true);
      // Auto-login with the same credentials, then land in the app.
      await login({ email, password });
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setError(err);
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  const uncertainCreation = !accountCreated && isApiError(error) && error.code === "NETWORK_ERROR";

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
            <AccountCreated submitting={submitting} error={error} />
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
            <AuthFeedback error={error} message={uncertainCreation ? "Couldn't confirm account creation. If you already created an account, sign in." : undefined} />
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
