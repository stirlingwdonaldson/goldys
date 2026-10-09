"use client";

import { useState } from "react";
import { ProvenanceView } from "./provenance-view";

interface ProvenancePanelProps {
  /** The metric's catalogue id (e.g. "sales.gross"). */
  metricId: string;
  /** Human name for the metric, e.g. "Gross sales". */
  metricLabel?: string;
  /** The period the value applies to, in ISO-8601 (YYYY-MM-DD). */
  date: string;
}

/**
 * Progressive-disclosure provenance detail: a collapsed link that, when expanded, fetches and
 * draws the per-source → resolution → resolved-value graph for one metric on one date. Nothing
 * is fetched until the operator asks to see it.
 */
export function ProvenancePanel({ metricId, metricLabel = metricId, date }: ProvenancePanelProps) {
  const [open, setOpen] = useState(false);
  return (
    <div className="text-sm">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="text-xs text-muted-foreground underline underline-offset-2"
      >
        {open ? "Hide provenance" : "Why do I trust this?"}
      </button>
      {open ? (
        <div className="mt-2">
          <ProvenanceView metricId={metricId} metricLabel={metricLabel} date={date} />
        </div>
      ) : null}
    </div>
  );
}
