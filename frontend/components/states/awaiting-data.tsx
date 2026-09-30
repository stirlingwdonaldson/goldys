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
    <div className="rounded-lg border border-dashed bg-card p-4">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <span>{label}</span>
      </div>
      <p className="mt-3 text-sm text-muted-foreground">{description}</p>
      <p className="mt-2 text-xs text-muted-foreground/70">{reason}</p>
    </div>
  );
}
