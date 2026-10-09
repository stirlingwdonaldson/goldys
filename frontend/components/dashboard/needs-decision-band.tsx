import Link from "next/link";
import { ArrowRight, CircleCheck, Scale } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface NeedsDecisionBandProps {
  openCount: number;
  href?: string;
}

/**
 * The dashboard's one live signal: how many numbers still need a decision.
 * When there is work, it carries the screen's single gold action.
 */
export function NeedsDecisionBand({ openCount, href = "/reconciliation" }: NeedsDecisionBandProps) {
  const hasConflicts = openCount > 0;
  const Icon = hasConflicts ? Scale : CircleCheck;
  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-center",
        hasConflicts
          ? "border-status-conflict/20 bg-status-conflict-soft"
          : "border-status-success/15 bg-status-success-soft",
      )}
    >
      <div className="flex items-center gap-3">
        <span
          className={cn(
            "flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-background",
            hasConflicts ? "text-status-conflict" : "text-status-success",
          )}
        >
          <Icon className="size-[18px]" aria-hidden="true" />
        </span>
        <div>
          <p className="text-sm font-semibold">
            {hasConflicts
              ? `${openCount} ${openCount === 1 ? "number needs" : "numbers need"} a decision`
              : "All numbers reconcile"}
          </p>
          <p className="text-sm text-muted-foreground">
            {hasConflicts
              ? "These figures will show as disputed in your reports until resolved."
              : "Every reported figure currently agrees across your sources."}
          </p>
        </div>
      </div>
      <Button asChild variant={hasConflicts ? "brand" : "outline"} size="sm" className="sm:ml-auto">
        <Link href={href}>
          {hasConflicts ? "Review in Reconciliation" : "Open Reconciliation"}
          <ArrowRight aria-hidden="true" />
        </Link>
      </Button>
    </div>
  );
}
