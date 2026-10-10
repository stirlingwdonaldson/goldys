"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { WorkList } from "@/components/my-work/work-list";
import type { WorkItem } from "@/lib/work-queue";
import { NeedsDecisionBand } from "./needs-decision-band";

const PREVIEW = 4;

interface AttentionPanelProps {
  /** Queue items already filtered to the person's focus; null while loading or unavailable. */
  items: WorkItem[] | null;
  loading: boolean;
  /** Fallback when the queue can't be read: the dashboard summary's open-conflict count. */
  openConflicts: number;
}

/**
 * Home's first block: the few things that most need this person, with a way into the
 * full queue. When the queue can't be read it falls back to the original decision band,
 * so Home never loses its one live signal.
 */
export function AttentionPanel({ items, loading, openConflicts }: AttentionPanelProps) {
  if (loading) return <Skeleton className="h-40 rounded-xl" />;
  if (!items || items.length === 0) return <NeedsDecisionBand openCount={openConflicts} />;

  const urgent = items.filter((i) => i.severity === "high").length;
  return (
    <section className="flex flex-col gap-2 rounded-xl border border-status-conflict/20 bg-card p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-sm font-semibold">Needs attention</h2>
        <Badge variant="conflict">{items.length} open</Badge>
        {urgent > 0 ? <Badge variant="failed">{urgent} urgent</Badge> : null}
        <Button asChild variant="brand" size="sm" className="ml-auto">
          <Link href="/my-work">
            Open My work
            <ArrowRight aria-hidden="true" />
          </Link>
        </Button>
      </div>
      <WorkList items={items.slice(0, PREVIEW)} label="Top items needing attention" />
      {items.length > PREVIEW ? (
        <p className="text-xs text-muted-foreground">
          And {items.length - PREVIEW} more in{" "}
          <Link href="/my-work" className="underline underline-offset-2 hover:text-foreground">
            My work
          </Link>
          .
        </p>
      ) : null}
    </section>
  );
}
