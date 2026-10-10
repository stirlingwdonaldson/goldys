"use client";

import { Suspense, useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { LoadingState } from "@/components/states/loading-state";
import { FlowLabView } from "@/components/flow-lab/flow-lab-view";
import { findDiagram } from "@/components/flow-lab/diagrams";

export default function FlowLabPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <FlowLabContent />
    </Suspense>
  );
}

/** The chosen diagram lives in the URL (`?d=`), so each one can be linked to and shared. */
function FlowLabContent() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const diagram = findDiagram(params.get("d"));

  const select = useCallback(
    (id: string) => router.replace(`${pathname}?d=${encodeURIComponent(id)}`, { scroll: false }),
    [router, pathname],
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Flow lab"
        description="Concept diagrams for dependencies, automations and data lineage across the venue."
        status={<Badge variant="neutral">Concepts · illustrative data</Badge>}
      />
      <FlowLabView diagram={diagram} onSelect={select} />
    </div>
  );
}
