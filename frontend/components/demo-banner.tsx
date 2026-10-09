"use client";

import { FlaskConical } from "lucide-react";
import { useDemoMode } from "@/lib/demo-mode";

export function DemoBanner() {
  const { demo } = useDemoMode();
  if (!demo) return null;
  return (
    <div className="flex items-center gap-2 whitespace-nowrap border-b bg-brand/20 px-4 py-1.5 text-xs font-medium text-foreground md:rounded-t-xl">
      <FlaskConical className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span className="truncate">
        Demo data<span className="hidden sm:inline"> — not production. Toggle demo mode in Settings.</span>
      </span>
    </div>
  );
}
