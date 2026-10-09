"use client";

import { useState } from "react";
import { AlertTriangle, Copy, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ErrorStateProps {
  title?: string;
  message?: string;
  correlationId?: string;
  onRetry?: () => void;
}

/**
 * Says what went wrong and offers the way out (heuristic 9). The correlation ID
 * stays available for support but sits behind a copy button instead of being
 * the most prominent thing on screen.
 */
export function ErrorState({
  title = "Something went wrong",
  message,
  correlationId,
  onRetry,
}: ErrorStateProps) {
  const [copied, setCopied] = useState(false);

  async function copyId() {
    if (!correlationId) return;
    try {
      await navigator.clipboard.writeText(correlationId);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      // Clipboard can be unavailable (insecure context); the ID is still visible below.
    }
  }

  return (
    <div role="alert" className="flex gap-3 rounded-xl bg-destructive-soft p-5">
      <AlertTriangle className="mt-0.5 size-5 shrink-0 text-destructive" aria-hidden="true" />
      <div className="flex min-w-0 flex-col gap-1.5">
        <p className="text-sm font-semibold">{title}</p>
        {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
        {correlationId ? (
          <p className="font-mono text-xs text-muted-foreground">Reference: {correlationId}</p>
        ) : null}
        <div className="mt-2 flex flex-wrap gap-2">
          {onRetry ? (
            <Button size="sm" onClick={onRetry}>
              <RotateCw aria-hidden="true" />
              Retry
            </Button>
          ) : null}
          {correlationId ? (
            <Button size="sm" variant="ghost" onClick={copyId}>
              <Copy aria-hidden="true" />
              {copied ? "Copied" : "Copy reference"}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
