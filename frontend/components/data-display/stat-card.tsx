import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { ArrowDownRight, ArrowUpRight, CircleHelp } from "lucide-react";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Sparkline } from "./sparkline";

export interface StatDelta {
  /** e.g. "6.2% vs Sat 27 Sep". */
  label: string;
  direction: "up" | "down" | "flat";
  /**
   * Whether the change is good for the venue. Colour follows meaning, not arrow
   * direction: labour % going down is green (heuristic 2).
   */
  tone: "good" | "bad" | "neutral";
}

interface StatCardProps {
  label: string;
  value: string;
  hint?: string;
  icon?: LucideIcon;
  /** Metric definition, shown in a hover card behind a "?" (heuristic 10). */
  help?: string;
  delta?: StatDelta;
  spark?: (number | null)[];
  /** Source and trust information, pinned to the bottom of the tile. */
  footer?: ReactNode;
}

export function StatCard({ label, value, hint, icon: Icon, help, delta, spark, footer }: StatCardProps) {
  return (
    <Card className="flex min-w-0 flex-col gap-1.5 p-4">
      <div className="flex items-center gap-2 text-ui text-muted-foreground">
        {Icon ? <Icon className="size-4" aria-hidden="true" /> : null}
        <span>{label}</span>
        {help ? (
          <HoverCard openDelay={150}>
            <HoverCardTrigger asChild>
              <button
                type="button"
                className="ml-auto rounded-full text-muted-foreground/70 hover:text-foreground"
                aria-label={`What is ${label}?`}
              >
                <CircleHelp className="size-3.5" aria-hidden="true" />
              </button>
            </HoverCardTrigger>
            <HoverCardContent className="w-72 text-sm">
              <p className="font-medium">{label}</p>
              <p className="mt-1 text-muted-foreground">{help}</p>
            </HoverCardContent>
          </HoverCard>
        ) : null}
      </div>
      <p className="text-kpi tabular-nums">{value}</p>
      {delta || hint ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          {delta ? <DeltaPill delta={delta} /> : null}
          {hint ? <span>{hint}</span> : null}
        </div>
      ) : null}
      {spark ? <Sparkline values={spark} className="mt-1 h-9 w-full" /> : null}
      {footer ? <div className="mt-auto flex items-center justify-between gap-2 pt-1.5">{footer}</div> : null}
    </Card>
  );
}

const DELTA_VARIANT = { good: "success", bad: "failed", neutral: "neutral" } as const;

export function DeltaPill({ delta }: { delta: StatDelta }) {
  const Arrow = delta.direction === "up" ? ArrowUpRight : delta.direction === "down" ? ArrowDownRight : null;
  return (
    <Badge variant={DELTA_VARIANT[delta.tone]} className="gap-0.5 px-1.5 py-px tabular-nums">
      {Arrow ? <Arrow aria-hidden="true" /> : null}
      {delta.label}
    </Badge>
  );
}
