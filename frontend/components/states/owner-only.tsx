import { Lock } from "lucide-react";
import { IconTile } from "@/components/ui/icon-tile";
import { Card } from "@/components/ui/card";

/**
 * Stands in for an Owner-only surface (wage and labour cost) for every other role.
 * An explicit locked state, never a blank or partial tile (docs/design/design-system.md).
 */
export function OwnerOnlyTile({
  label,
  reason = "Wage and labour-cost figures are restricted.",
}: {
  label: string;
  reason?: string;
}) {
  return (
    <Card className="flex flex-col gap-3 border-dashed bg-muted/40 p-4">
      <div className="flex items-center gap-2.5 text-sm font-medium">
        <IconTile>
          <Lock />
        </IconTile>
        <span>{label}</span>
      </div>
      <p className="text-sm text-muted-foreground">Owner only.</p>
      <p className="mt-auto text-xs text-muted-foreground/80">{reason}</p>
    </Card>
  );
}
