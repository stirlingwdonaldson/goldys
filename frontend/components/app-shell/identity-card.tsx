"use client";

import { ShieldCheck } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentUser } from "./current-user-provider";

export function IdentityCard() {
  const { user, status } = useCurrentUser();

  if (status === "loading") {
    return (
      <div className="rounded-lg border p-4">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="mt-3 h-3 w-64" />
      </div>
    );
  }

  if (status === "authenticated" && user) {
    return (
      <div className="rounded-lg border p-4">
        <div className="flex items-center gap-2 text-sm font-medium">
          <ShieldCheck className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          Signed in
        </div>
        <dl className="mt-3 space-y-1 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Name</dt>
            <dd>{user.displayName}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Department</dt>
            <dd>{user.department}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Seniority</dt>
            <dd>{user.seniority}</dd>
          </div>
        </dl>
      </div>
    );
  }

  return (
    <div className="rounded-lg border p-4">
      <p className="text-sm font-medium">Not signed in</p>
      <p className="mt-1 text-sm text-muted-foreground">
        Sign in to see your staff profile and role-based access.
      </p>
    </div>
  );
}
