import { AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * A compact error for one part of a page that failed while the rest loaded.
 * Say what failed and what to do ("Refresh the page to try again"), per heuristic 9.
 * Use `ErrorState` instead when the whole screen failed.
 */
export function InlineError({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <p role="alert" className={cn("flex items-start gap-2 rounded-xl bg-destructive-soft p-4 text-sm text-destructive", className)}>
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
