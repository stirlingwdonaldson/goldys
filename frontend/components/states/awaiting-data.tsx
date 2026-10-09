import type { LucideIcon } from "lucide-react";
import { Hourglass } from "lucide-react";
import { IconTile } from "@/components/ui/icon-tile";
import { Card } from "@/components/ui/card";

interface AwaitingDataProps {
  label: string;
  description: string;
  reason: string;
  icon?: LucideIcon;
}

/**
 * A forward-looking placeholder for a surface that is not wired to data yet.
 * Deliberately renders no values — see docs/design/design-system.md ("never present
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
    <Card className="flex flex-col gap-3 border-dashed p-4">
      <div className="flex items-center gap-2.5 text-sm font-medium">
        <IconTile>
          <Icon />
        </IconTile>
        <span>{label}</span>
      </div>
      <p className="text-sm text-muted-foreground">{description}</p>
      <p className="mt-auto text-xs text-muted-foreground/80">{reason}</p>
    </Card>
  );
}
