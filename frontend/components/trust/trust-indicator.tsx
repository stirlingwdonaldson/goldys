import { Badge, type BadgeProps } from "@/components/ui/badge";
import { formatAgo } from "@/lib/format";
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

// A conflict is an expected, resolvable state, so it uses the conflict tone rather
// than destructive (which is reserved for genuine system failures).
export const TRUST_VARIANT: Record<TrustState, BadgeProps["variant"]> = {
  VERIFIED: "success",
  RESOLVED_BY_RULE: "info",
  MANUALLY_OVERRIDDEN: "info",
  SINGLE_SOURCE: "neutral",
  CONFLICTED: "conflict",
  INCOMPLETE: "conflict",
  NOT_RECEIVED: "missing",
};

const FRESHNESS_LABEL: Record<Exclude<FreshnessState, "FRESH">, string> = {
  STALE: "stale",
  SOURCE_FAILURE: "source failed",
  UNKNOWN: "no data",
};

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
      <Badge variant={TRUST_VARIANT[trust.state]}>{TRUST_LABEL[trust.state]}</Badge>
      <span className="text-xs text-muted-foreground">{secondary}</span>
    </span>
  );
}

// Re-exported for existing callers; the implementation lives with the other formatters.
export { formatAgo };
