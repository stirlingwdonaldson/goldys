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
      <div className="flex items-center gap-2 rounded-xl border border-transparent bg-status-info-soft p-3 text-sm text-status-info">
        <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />
        <span>Rules changed — recomputing resolved views…</span>
      </div>
    );
  }

  if (status.state === "failed") {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-transparent bg-destructive-soft p-3 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        <span>Recompute failed. Resolved views may be stale.</span>
      </div>
    );
  }

  if (status.state === "complete" && status.lastChangedAt) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-transparent bg-status-success-soft p-3 text-sm text-status-success">
        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
        <span>
          Rules apply immediately · last changed{" "}
          {new Date(status.lastChangedAt).toLocaleString()}
        </span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-xl border p-3 text-sm text-muted-foreground">
      <RefreshCw className="h-4 w-4" aria-hidden="true" />
      <span>Resolved views have never been recomputed.</span>
    </div>
  );
}
