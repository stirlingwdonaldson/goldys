"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { changePassword } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthFeedback } from "@/components/auth/auth-feedback";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";

const MIN_LENGTH = 8;

export function ChangePasswordForm() {
  const router = useRouter();
  const { refresh } = useCurrentUser();
  const [error, setError] = useState<unknown>(null);
  const [localMessage, setLocalMessage] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const data = new FormData(event.currentTarget);
    const currentPassword = String(data.get("currentPassword") ?? "");
    const newPassword = String(data.get("newPassword") ?? "");
    const confirmation = String(data.get("confirmation") ?? "");

    setError(null);
    setLocalMessage(undefined);
    if (newPassword.length < MIN_LENGTH) {
      setError({});
      setLocalMessage(`Password must be at least ${MIN_LENGTH} characters`);
      return;
    }
    if (newPassword !== confirmation) {
      setError({});
      setLocalMessage("The new passwords do not match");
      return;
    }

    setSubmitting(true);
    try {
      await changePassword({ currentPassword, newPassword });
      refresh();
      router.push("/dashboard");
    } catch (failure) {
      setError(failure);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Choose a new password before continuing. Your owner set a temporary one.
      </p>
      <div className="space-y-1.5">
        <Label htmlFor="current-password">Current password</Label>
        <Input
          id="current-password"
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          required
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="new-password">New password</Label>
        <Input
          id="new-password"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          minLength={MIN_LENGTH}
          required
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="confirm-password">Confirm new password</Label>
        <Input
          id="confirm-password"
          name="confirmation"
          type="password"
          autoComplete="new-password"
          minLength={MIN_LENGTH}
          required
        />
      </div>
      <AuthFeedback error={error} message={localMessage} />
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? "Changing…" : "Change password"}
      </Button>
    </form>
  );
}
