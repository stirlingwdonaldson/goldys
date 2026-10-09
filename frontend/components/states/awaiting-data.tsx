import type { LucideIcon } from "lucide-react";
import { Hourglass } from "lucide-react";

interface AwaitingDataProps {
  label: string;
  description: string;
  reason: string;
  icon?: LucideIcon;
}

/**
 * A forward-looking placeholder for a surface that is not wired to data yet.
 * Deliberately renders no values — see docs/design-system.md ("never present
 * mock data as operational"). Distinct from `EmptyState`, which means "the data
 * exists but is empty right now".
 */
export function AwaitingData({
  label,
  description,
  reason,
  icon: Icon = Hourglass,
}: AwaitingDataProps) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-dashed p-4">
      <div className="flex items-center gap-2.5 text-sm font-medium">
        <span className="flex size-8 items-center justify-center rounded-lg bg-muted">
          <Icon className="size-4 text-muted-foreground" aria-hidden="true" />
        </span>
        <span>{label}</span>
      </div>
      <p className="text-sm text-muted-foreground">{description}</p>
      <p className="mt-auto text-xs text-muted-foreground/80">{reason}</p>
    </div>
  );
}
