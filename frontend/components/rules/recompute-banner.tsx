import { AlertCircle, CheckCircle2, RefreshCw } from "lucide-react";
import type { RecomputeStatus } from "@/lib/api";

interface RecomputeBannerProps {
  status: RecomputeStatus;
  onRetry?: () => void;
}

/** The recompute-state banner: a manager must not read a partially-recomputed view. */
export function RecomputeBanner({ status }: RecomputeBannerProps) {
  if (status.state === "recomputing") {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-status-warning/15 p-3 text-sm">
        <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />
        <span>Rules changed — recomputing resolved views…</span>
      </div>
    );
  }

  if (status.state === "failed") {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-destructive/10 p-3 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        <span>Recompute failed. Resolved views may be stale.</span>
      </div>
    );
  }

  if (status.state === "complete" && status.lastCompletedAt) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-status-success/10 p-3 text-sm">
        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
        <span>
          Last recomputed {new Date(status.lastCompletedAt).toLocaleString()}
        </span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-lg border p-3 text-sm text-muted-foreground">
      <RefreshCw className="h-4 w-4" aria-hidden="true" />
      <span>Resolved views have never been recomputed.</span>
    </div>
  );
}
