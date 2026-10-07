import { Badge } from "@/components/ui/badge";
import type { FreshnessState, MissingDataStatus, TrustState, TrustSummary } from "@/lib/api/types";

/** Venue-friendly labels for each trust state (mirrors the trust vocabulary in the design spec). */
export const TRUST_LABEL: Record<TrustState, string> = {
  VERIFIED: "Verified",
  RESOLVED_BY_RULE: "Resolved by rule",
  MANUALLY_OVERRIDDEN: "Manual resolution",
  SINGLE_SOURCE: "Single source",
  CONFLICTED: "Unresolved conflict",
  INCOMPLETE: "Incomplete",
  NOT_RECEIVED: "No data",
};

/** Venue-friendly labels for each reason a value is absent. `ZERO` is a genuine zero. */
export const MISSING_LABEL: Record<MissingDataStatus, string> = {
  ZERO: "0",
  UNKNOWN: "Unknown",
  NOT_RECEIVED: "No data",
  UNRESOLVED: "Unresolved",
  NOT_APPLICABLE: "Not applicable",
  NOT_PERMITTED: "Not permitted",
};

const TRUST_CLASS: Record<TrustState, string> = {
  VERIFIED: "border-transparent bg-status-success text-status-success-foreground",
  RESOLVED_BY_RULE: "border-transparent bg-status-info text-status-info-foreground",
  MANUALLY_OVERRIDDEN: "border-transparent bg-status-info text-status-info-foreground",
  SINGLE_SOURCE: "border-transparent bg-status-warning text-status-warning-foreground",
  CONFLICTED: "border-transparent bg-destructive text-destructive-foreground",
  INCOMPLETE: "border-transparent bg-status-warning text-status-warning-foreground",
  NOT_RECEIVED: "border-transparent bg-status-missing text-status-missing-foreground",
};

const FRESHNESS_LABEL: Record<Exclude<FreshnessState, "FRESH">, string> = {
  STALE: "stale",
  SOURCE_FAILURE: "source failed",
  UNKNOWN: "no data",
};

/** A compact "7 min ago" style relative time for an ISO-8601 instant. */
export function formatAgo(iso: string, now: Date = new Date()): string {
  const then = new Date(iso).getTime();
  const minutes = Math.max(0, Math.floor((now.getTime() - then) / 60_000));
  if (minutes < 1) return "just now";
  if (minutes === 1) return "1 min ago";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours === 1) return "1 hour ago";
  if (hours < 24) return `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "1 day ago";
  return `${days} days ago`;
}

function freshnessText(trust: TrustSummary): string {
  if (trust.freshness === "FRESH") {
    const at = trust.lastIngestionAt ?? trust.resolvedAt;
    return at ? `updated ${formatAgo(at)}` : "updated recently";
  }
  return FRESHNESS_LABEL[trust.freshness];
}

interface TrustIndicatorProps {
  /** The trust + freshness summary for the metric. */
  trust: TrustSummary;
  /** The resolved value, or null when there is no value for this period. */
  value?: number | null;
  /** Why the value is absent; only meaningful when `value` is null. */
  status?: MissingDataStatus | null;
}

/**
 * A compact trust badge: the venue-friendly trust label plus either the freshness
 * ("updated 7 min ago" / "stale" / "source failed" / "no data") or, for a null value,
 * the missing-data reason ("No data" / "Unresolved" / …). A null value is never shown as "0".
 */
export function TrustIndicator({ trust, value = null, status = null }: TrustIndicatorProps) {
  const missing = value == null;
  const secondary = missing ? (status ? MISSING_LABEL[status] : "No data") : freshnessText(trust);
  return (
    <span className="inline-flex items-center gap-1.5">
      <Badge className={TRUST_CLASS[trust.state]}>{TRUST_LABEL[trust.state]}</Badge>
      <span className="text-xs text-muted-foreground">{secondary}</span>
    </span>
  );
}
