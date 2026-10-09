"use client";

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ProvenanceView } from "./provenance-view";

export interface ProvenanceTarget {
  metricId: string;
  metricLabel: string;
  date: string;
}

/**
 * A side sheet that traces one figure back to its sources. Screens hold the `target` in state
 * and set it from a table row's "Trace" action; `null` closes the sheet.
 */
export function ProvenanceSheet({
  target,
  onClose,
}: {
  target: ProvenanceTarget | null;
  onClose: () => void;
}) {
  return (
    <Sheet open={target != null} onOpenChange={(open) => (open ? undefined : onClose())}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-2xl">
        {target ? (
          <>
            <SheetHeader>
              <SheetTitle>
                {target.metricLabel} · {target.date}
              </SheetTitle>
              <SheetDescription>
                What each source reported, how the difference was settled, and the figure we show.
              </SheetDescription>
            </SheetHeader>
            <div className="mt-6">
              <ProvenanceView {...target} />
            </div>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
