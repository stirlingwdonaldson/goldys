"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import { formatRecentTime } from "@/lib/format";
import { StatusDot } from "@/components/ui/status-dot";
import { isFailing, useShellStatus } from "./shell-status";

/** Collection activity is separate from the age and completeness of business evidence. */
export function FreshnessChip() {
  const { connectors } = useShellStatus();
  if (!connectors || connectors.length === 0) return null;

  const failing = connectors.filter(isFailing).length;
  const completed = connectors.filter((c) =>
    c.lastRunAt != null && (c.status === "success" || c.status === "no_new_data")
  ).length;
  const statuses = connectors.map((c) => `${c.source}: ${c.status.replaceAll("_", " ")}`).join("; ");
  const latest = connectors
    .map((c) => c.lastRunAt)
    .filter((t): t is string => t != null)
    .sort()
    .at(-1);
  const asOf = latest ? `Last collection run ${formatRecentTime(latest)}` : "No collection runs recorded";
  const health = `${completed} of ${connectors.length} latest runs completed without errors`;
  const short = `${completed}/${connectors.length} runs completed`;

  return (
    <Link
      href="/data-health"
      title={`${asOf} · ${health}. ${statuses}`}
      aria-label={`${asOf}. ${health}. ${statuses}. Evidence freshness and completeness are separate. Open Data health.`}
      className="inline-flex h-8 shrink-0 items-center gap-2 whitespace-nowrap rounded-full border pl-2.5 pr-3 text-xs text-muted-foreground transition-colors hover:text-foreground"
    >
      <StatusDot tone={failing ? "failed" : completed < connectors.length ? "missing" : "success"} />
      <span className="hidden xl:inline">{asOf}</span>
      <span aria-hidden="true" className="hidden xl:inline">
        ·
      </span>
      <span className={cn(failing && "font-medium text-destructive")}>
        <span className="hidden xl:inline">{health}</span>
        <span className="xl:hidden">{short}</span>
      </span>
    </Link>
  );
}
