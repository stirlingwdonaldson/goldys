"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useApiData } from "@/lib/use-api-data";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { PermissionDenied } from "@/components/states/permission-denied";
import type { Provenance } from "@/lib/api/types";
import { buildProvenanceGraph } from "./provenance-graph";
import { TrustIndicator } from "./trust-indicator";

interface ProvenanceViewProps {
  metricId: string;
  /** Human name for the metric, e.g. "Gross sales". */
  metricLabel: string;
  /** ISO-8601 date (YYYY-MM-DD). */
  date: string;
}

/** Fetches and draws one metric/date's provenance: the node graph plus its raw-record evidence. */
export function ProvenanceView({ metricId, metricLabel, date }: ProvenanceViewProps) {
  const result = useApiData((api) => api.getProvenance(metricId, date), [metricId, date]);

  if (result.loading) return <Skeleton className="h-[280px] w-full" />;
  if (result.error) {
    if (result.error.code === "NOT_PERMITTED") return <PermissionDenied subject="this figure's sources" />;
    return (
      <p role="alert" className="text-sm text-destructive">
        Couldn&apos;t load where this figure came from. {result.error.message}
      </p>
    );
  }
  if (!result.data) return null;
  return <ProvenanceDetail provenance={result.data} metricLabel={metricLabel} />;
}

function ProvenanceDetail({ provenance, metricLabel }: { provenance: Provenance; metricLabel: string }) {
  const graph = useMemo(() => buildProvenanceGraph(provenance, metricLabel), [provenance, metricLabel]);
  const rows = Math.max(1, provenance.sources.length);
  return (
    <div className="flex flex-col gap-4">
      <TrustIndicator trust={provenance.trust} value={provenance.resolvedValue} />
      <FlowCanvas
        graph={graph}
        ariaLabel={`How ${metricLabel} for ${provenance.date} was resolved`}
        height={Math.max(240, rows * 84 + 96)}
      />
      <ul className="sr-only">
        {provenance.sources.map((s) => (
          <li key={s.sourceSystem}>
            {s.sourceSystem}: {s.value == null ? "no data" : s.value}
          </li>
        ))}
      </ul>
      <div>
        <h3 className="text-sm font-medium">Raw records</h3>
        {provenance.rawRecordIds.length ? (
          <ul className="mt-1 space-y-1 text-xs">
            {provenance.rawRecordIds.map((id) => (
              <li key={id} className="break-all">
                <Link
                  href={`/data?layer=raw&id=${encodeURIComponent(id)}`}
                  className="font-mono text-muted-foreground underline underline-offset-2 hover:text-foreground"
                >
                  {id}
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-1 text-xs text-muted-foreground">
            No raw records to show — none were linked, or your role can&apos;t view ingestion data.
          </p>
        )}
      </div>
    </div>
  );
}
