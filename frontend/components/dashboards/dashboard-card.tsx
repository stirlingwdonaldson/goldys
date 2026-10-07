"use client";

import { Pin, PinOff } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { SavedDashboardSummary, Visibility } from "@/lib/api/types";

const VISIBILITY_LABELS: Record<Visibility, string> = {
  PRIVATE: "Private",
  SHARED: "Shared",
  ORG_WIDE: "Org-wide",
};

/** A stable, short human-readable date for the card footer. */
function formatUpdatedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

interface DashboardCardProps {
  dashboard: SavedDashboardSummary;
  /** Opens the dashboard in the editor. */
  onOpen?: () => void;
  /** Toggles the pinned flag; the parent re-fetches / updates the list. */
  onTogglePin?: () => void;
}

/**
 * One library entry: title, description, creator, last-updated, pinned state and a visibility
 * badge, with a pin toggle. Fields absent from the live list summary degrade gracefully.
 */
export function DashboardCard({ dashboard, onOpen, onTogglePin }: DashboardCardProps) {
  const pinned = dashboard.pinned === true;
  return (
    <Card className="group relative flex flex-col">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-base">
            <button
              type="button"
              onClick={onOpen}
              className="text-left font-semibold hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm"
            >
              {dashboard.title}
            </button>
          </CardTitle>
          <div className="flex items-center gap-1.5">
            {pinned ? (
              <Pin className="h-4 w-4 text-muted-foreground" aria-label="Pinned" />
            ) : null}
            {dashboard.visibility ? (
              <Badge variant="outline">{VISIBILITY_LABELS[dashboard.visibility]}</Badge>
            ) : null}
          </div>
        </div>
        {dashboard.description ? (
          <CardDescription className="line-clamp-2">{dashboard.description}</CardDescription>
        ) : null}
      </CardHeader>
      <CardContent className="pt-0 text-sm text-muted-foreground">
        <div className="flex items-center justify-between">
          <span>
            {dashboard.createdBy ?? "—"} · Updated {formatUpdatedAt(dashboard.updatedAt)}
          </span>
          {onTogglePin ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={onTogglePin}
              aria-label={pinned ? "Unpin dashboard" : "Pin dashboard"}
              aria-pressed={pinned}
            >
              {pinned ? <PinOff className="h-4 w-4" aria-hidden="true" /> : <Pin className="h-4 w-4" aria-hidden="true" />}
              <span className="sr-only">{pinned ? "Unpin" : "Pin"}</span>
            </Button>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
