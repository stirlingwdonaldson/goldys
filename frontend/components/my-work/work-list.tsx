"use client";

import Link from "next/link";
import { ChevronRight, FileWarning, Plug, RefreshCcw, Scale, type LucideIcon } from "lucide-react";
import { IconTile } from "@/components/ui/icon-tile";
import { Badge } from "@/components/ui/badge";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatAgo, formatDay } from "@/lib/format";
import { FOCUS_OPTIONS, type Focus, type WorkCategory, type WorkItem, type WorkSeverity } from "@/lib/work-queue";

const CATEGORY_ICON: Record<WorkCategory, LucideIcon> = {
  decision: Scale,
  source: Plug,
  invoice: FileWarning,
  recompute: RefreshCcw,
};

const SEVERITY: Record<WorkSeverity, { label: string; tone: "failed" | "conflict" | "neutral" }> = {
  high: { label: "Urgent", tone: "failed" },
  medium: { label: "Soon", tone: "conflict" },
  low: { label: "When you can", tone: "neutral" },
};

function when(at: string | null): string | null {
  if (!at) return null;
  // Business dates read as a day; instants read as "3 hours ago".
  return /^\d{4}-\d{2}-\d{2}$/.test(at) ? formatDay(at) : formatAgo(at);
}

/** One actionable row: what it is, how urgent, and a link to where it gets resolved. */
export function WorkItemRow({ item }: { item: WorkItem }) {
  const Icon = CATEGORY_ICON[item.category];
  const severity = SEVERITY[item.severity];
  const at = when(item.at);
  return (
    <li>
      <Link
        href={item.href}
        className="group flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <IconTile tone={severity.tone === "neutral" ? "neutral" : severity.tone}>
          <Icon />
        </IconTile>
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 text-sm font-medium sm:truncate">{item.title}</p>
          <p className="truncate text-xs text-muted-foreground">{item.detail}</p>
        </div>
        <div className="hidden shrink-0 flex-col items-end gap-1 sm:flex">
          <Badge variant={severity.tone}>{severity.label}</Badge>
          {at ? <span className="text-[11px] text-muted-foreground">{at}</span> : null}
        </div>
        <ChevronRight
          className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5"
          aria-hidden="true"
        />
      </Link>
    </li>
  );
}

export function WorkList({ items, label }: { items: WorkItem[]; label: string }) {
  return (
    <ul aria-label={label} className="-mx-3 flex flex-col">
      {items.map((item) => (
        <WorkItemRow key={item.id} item={item} />
      ))}
    </ul>
  );
}

/** Pick the perspective Home and My work are read from. Changes emphasis, never access. */
export function FocusSwitcher({ value, onChange }: { value: Focus; onChange: (f: Focus) => void }) {
  return (
    <ToggleGroup
      type="single"
      size="sm"
      variant="outline"
      attached
      value={value}
      onValueChange={(v) => v && onChange(v as Focus)}
      aria-label="View as"
    >
      {FOCUS_OPTIONS.map((o) => (
        <ToggleGroupItem key={o.value} value={o.value} title={o.description}>
          {o.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  );
}
