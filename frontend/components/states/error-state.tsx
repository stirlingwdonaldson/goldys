"use client";

import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ErrorStateProps {
  title?: string;
  message?: string;
  correlationId?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = "Something went wrong",
  message,
  correlationId,
  onRetry,
}: ErrorStateProps) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border p-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-destructive">
        <AlertTriangle className="h-4 w-4" aria-hidden="true" />
        {title}
      </div>
      {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
      {correlationId ? (
        <p className="text-xs text-muted-foreground">Reference: {correlationId}</p>
      ) : null}
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </div>
  );
}
