"use client";

import { Lock } from "lucide-react";

interface PermissionDeniedProps {
  /** What is restricted, e.g. "wage and labor-cost data". */
  subject?: string;
  message?: string;
}

export function PermissionDenied({ subject = "this data", message }: PermissionDeniedProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border px-6 py-16 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-muted">
        <Lock className="h-6 w-6 text-muted-foreground" aria-hidden="true" />
      </div>
      <h3 className="mt-4 text-sm font-semibold">You don&apos;t have access to {subject}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">
        {message ??
          "This view is restricted by your role. Ask an owner if you believe this is a mistake."}
      </p>
    </div>
  );
}
