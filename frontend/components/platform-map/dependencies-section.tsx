"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Section } from "@/components/layout/section";
import { Skeleton } from "@/components/ui/skeleton";
import { PlatformMapView } from "./platform-map-view";

/**
 * Data health's "What depends on what" section. `?metric=sales.gross` (the provenance sheet's
 * "See the full map" link) opens the map focused on that metric's local graph.
 */
export function DependenciesSection() {
  return (
    <Section
      title="What depends on what"
      description="Sources feed domains, domains feed metrics, metrics feed dashboards. A failing or stale source colours everything downstream of it. Select a node to see what it affects."
      card
    >
      <div id="dependencies">
        <Suspense fallback={<Skeleton className="h-[560px] w-full" />}>
          <FocusedMap />
        </Suspense>
      </div>
    </Section>
  );
}

function FocusedMap() {
  const metric = useSearchParams().get("metric");
  return metric ? (
    <PlatformMapView initialFocusId={`metric:${metric}`} initialLocal />
  ) : (
    <PlatformMapView />
  );
}
