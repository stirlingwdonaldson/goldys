"use client";

import { FlaskConical } from "lucide-react";
import { useDemoMode } from "@/lib/demo-mode";

export function DemoBanner() {
  const { demo } = useDemoMode();
  if (!demo) return null;
  return (
    <div className="flex items-center gap-2 border-b bg-status-warning/15 px-4 py-1.5 text-xs font-medium text-status-warning-foreground">
      <FlaskConical className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>Demo data — not production. Toggle demo mode in Settings.</span>
    </div>
  );
}
