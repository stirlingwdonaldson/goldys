"use client";

import { useIncompleteNotices } from "@/lib/incomplete-notices";
import { Switch } from "@/components/ui/switch";

export function IncompleteNoticesToggle() {
  const { showIncomplete, setShowIncomplete } = useIncompleteNotices();
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border p-4">
      <div>
        <p id="incomplete-notices-label" className="text-sm font-medium">Show incomplete data</p>
        <p id="incomplete-notices-description" className="text-sm text-muted-foreground">
          Show affected observations and expanded explanations. When off, a compact issue control remains available.
        </p>
      </div>
      <Switch checked={showIncomplete} onCheckedChange={setShowIncomplete}
        aria-labelledby="incomplete-notices-label" aria-describedby="incomplete-notices-description" />
    </div>
  );
}
