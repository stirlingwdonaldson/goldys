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
import { SignupRecovery } from "./signup-recovery";

function uncertainCreation(error: unknown): boolean {
  if (!isApiError(error)) return false;
  if (error.code === "NETWORK_ERROR") return true;
  // Followed redirects also expose a final 2xx; they do not confirm account creation.
  return error.status !== undefined && error.status >= 200 && error.status < 300 &&
    error.code !== "AUTH_REQUIRED" && error.code !== "UNEXPECTED_REDIRECT";
}

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [creationOutcome, setCreationOutcome] = useState<"editing" | "created" | "uncertain">("editing");
  const busy = useRef(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (busy.current || creationOutcome !== "editing") return;
    busy.current = true;
    setError(null);
    setSubmitting(true);
    let created = false;
    try {
      await signup({ email, displayName, password });
      created = true;
      setCreationOutcome("created");
      // Auto-login with the same credentials, then land in the app.
      await login({ email, password });
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setError(err);
      if (!created && uncertainCreation(err)) setCreationOutcome("uncertain");
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
          {creationOutcome !== "editing" ? (
            <SignupRecovery outcome={creationOutcome} submitting={submitting} error={error} />
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
            <AuthFeedback error={error} />
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Creating account…" : "Sign up"}
            </Button>
          </form>}
          {creationOutcome === "editing" ? <p className="mt-4 text-center text-sm text-muted-foreground">
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
