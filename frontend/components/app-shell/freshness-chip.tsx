"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import { isFailing, useShellStatus } from "./shell-status";

function formatTime(iso: string): string {
  const d = new Date(iso);
  const time = d.toLocaleTimeString("en-AU", { hour: "numeric", minute: "2-digit" });
  if (d.toDateString() === new Date().toDateString()) return time;
  return `${d.toLocaleDateString("en-AU", { day: "numeric", month: "short" })}, ${time}`;
}

/**
 * "Data as of 6:15 am · All sources healthy" in every page header, so nobody
 * has to visit Data health to know whether the numbers are current (heuristic 1).
 * Turns red when a source has failed and links to the page that explains it.
 * Never wraps: on narrower headers it collapses to the dot plus "3/4 healthy",
 * with the full sentence kept in the tooltip and accessible name.
 */
export function FreshnessChip() {
  const { connectors } = useShellStatus();
  if (!connectors || connectors.length === 0) return null;

  const failing = connectors.filter(isFailing).length;
  const healthy = connectors.length - failing;
  const latest = connectors
    .map((c) => c.lastRunAt)
    .filter((t): t is string => t != null)
    .sort()
    .at(-1);
  const asOf = latest ? `Data as of ${formatTime(latest)}` : "No runs yet";
  const health = failing ? `${healthy} of ${connectors.length} sources healthy` : "All sources healthy";
  const short = failing ? `${healthy}/${connectors.length} healthy` : "Healthy";

  return (
    <Link
      href="/data-health"
      title={`${asOf} · ${health}`}
      aria-label={`${asOf}. ${health}. Open Data health.`}
      className="inline-flex h-8 shrink-0 items-center gap-2 whitespace-nowrap rounded-full border pl-2.5 pr-3 text-xs text-muted-foreground transition-colors hover:text-foreground"
    >
      <span
        aria-hidden="true"
        className={cn(
          "size-2 shrink-0 rounded-full",
          failing ? "bg-destructive ring-[3px] ring-destructive-soft" : "bg-status-success ring-[3px] ring-status-success-soft",
        )}
      />
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
