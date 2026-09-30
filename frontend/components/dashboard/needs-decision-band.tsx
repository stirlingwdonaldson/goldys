import Link from "next/link";
import { Scale } from "lucide-react";
import { Button } from "@/components/ui/button";

interface NeedsDecisionBandProps {
  openCount: number;
  href?: string;
}

/** The dashboard's one live signal: how many numbers still need a decision. */
export function NeedsDecisionBand({ openCount, href = "/reconciliation" }: NeedsDecisionBandProps) {
  const hasConflicts = openCount > 0;
  return (
    <div
      className={`flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between ${
        hasConflicts
          ? "border-transparent bg-status-warning/15"
          : "border-transparent bg-status-success/10"
      }`}
    >
      <div className="flex items-start gap-3">
        <Scale className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div>
          <p className="text-sm font-semibold">
            {hasConflicts
              ? `${openCount} ${openCount === 1 ? "number needs" : "numbers need"} a decision`
              : "All numbers reconcile"}
          </p>
          <p className="text-sm text-muted-foreground">
            {hasConflicts
              ? "These figures will disagree in your reports until resolved."
              : "Every reported figure currently agrees across your sources."}
          </p>
        </div>
      </div>
      <Button asChild variant={hasConflicts ? "default" : "outline"} size="sm">
        <Link href={href}>
          {hasConflicts ? "Review in Reconciliation" : "Open Reconciliation"}
        </Link>
      </Button>
    </div>
  );
}
