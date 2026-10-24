"use client";

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import Link from "next/link";
import { PlatformMapView } from "@/components/platform-map/platform-map-view";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ProvenanceView } from "./provenance-view";

export interface ProvenanceTarget {
  metricId: string;
  metricLabel: string;
  date: string;
}

/**
 * A side sheet that traces one figure back to its sources. Screens provide a `target` from local or URL state
 * and set it from a table row's "Trace" action; `null` closes the sheet.
 */
export function ProvenanceSheet({
  target,
  onClose,
  onRestoreFocus,
}: {
  target: ProvenanceTarget | null;
  onClose: () => void;
  onRestoreFocus?: () => void;
}) {
  return (
    <Sheet open={target != null} onOpenChange={(open) => (open ? undefined : onClose())}>
      <SheetContent onCloseAutoFocus={onRestoreFocus ? (event) => { event.preventDefault(); onRestoreFocus(); } : undefined} side="right" className="w-full overflow-y-auto sm:max-w-2xl">
        {target ? (
          <>
            <SheetHeader>
              <SheetTitle>
                {target.metricLabel} · {target.date}
              </SheetTitle>
              <SheetDescription>
                What each source reported, how the difference was settled, and the figure we show. Missing evidence is not zero. If access is denied, ask the person responsible for your account permissions.
              </SheetDescription>
            </SheetHeader>
            <Tabs defaultValue="sources" className="mt-6">
              <TabsList>
                <TabsTrigger value="sources">Sources</TabsTrigger>
                <TabsTrigger value="related">Related</TabsTrigger>
              </TabsList>
              <TabsContent value="sources" className="mt-4">
                <ProvenanceView {...target} />
              </TabsContent>
              <TabsContent value="related" className="mt-4 flex flex-col gap-3">
                <p className="text-xs text-muted-foreground">
                  What this metric is built from and what depends on it.{" "}
                  <Link
                    href={`/data-health?metric=${encodeURIComponent(target.metricId)}#dependencies`}
                    className="font-medium text-foreground underline underline-offset-2"
                  >
                    See the full map
                  </Link>
                </p>
                <PlatformMapView initialFocusId={`metric:${target.metricId}`} initialLocal height={420} compact />
              </TabsContent>
            </Tabs>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
