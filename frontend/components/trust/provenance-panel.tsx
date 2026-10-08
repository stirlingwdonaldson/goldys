"use client";

import { useState } from "react";
import { getProvenance } from "@/lib/api/provenance";
import type { Provenance } from "@/lib/api/types";

interface ProvenancePanelProps {
  /** The metric's catalogue id (e.g. "sales.gross"). */
  metricId: string;
  /** The period the value applies to, in ISO-8601 (YYYY-MM-DD). */
  date: string;
}

/**
 * Progressive-disclosure provenance detail: a collapsed link that, on first expansion, fetches the
 * per-source / resolution / raw-record drill-down for one metric on one date. It fetches only once
 * (cached after the first successful load) and only when the operator asks to see it.
 */
export function ProvenancePanel({ metricId, date }: ProvenancePanelProps) {
  const [open, setOpen] = useState(false);
  const [provenance, setProvenance] = useState<Provenance | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next && provenance == null && !loading) {
      setLoading(true);
      setError(null);
      getProvenance(metricId, date)
        .then(setProvenance)
        .catch((e: unknown) =>
          setError(e instanceof Error ? e.message : "Couldn't load provenance."),
        )
        .finally(() => setLoading(false));
    }
  }

  return (
    <div className="text-sm">
      <button
        type="button"
        onClick={toggle}
        className="text-xs text-muted-foreground underline underline-offset-2"
      >
        {open ? "Hide provenance" : "Why do I trust this?"}
      </button>

      {open ? (
        <div className="mt-2 rounded-md border p-3 text-xs">
          {loading ? <p className="text-muted-foreground">Loading…</p> : null}
          {error ? (
            <p role="alert" className="text-destructive">
              {error}
            </p>
          ) : null}
          {provenance ? <ProvenanceDetail provenance={provenance} /> : null}
        </div>
      ) : null}
    </div>
  );
}

function ProvenanceDetail({ provenance }: { provenance: Provenance }) {
  return (
    <dl className="space-y-2">
      <div>
        <dt className="font-medium text-foreground">Resolved value</dt>
        <dd className="text-muted-foreground">
          {provenance.resolvedValue == null ? "No value" : String(provenance.resolvedValue)}
        </dd>
      </div>

      {provenance.sources.length ? (
        <div>
          <dt className="font-medium text-foreground">Sources</dt>
          <dd>
            <ul className="list-inside list-disc text-muted-foreground">
              {provenance.sources.map((s, i) => (
                <li key={i}>
                  {s.sourceSystem}: {s.value == null ? "no data" : String(s.value)}
                  {s.recordedAt ? ` (recorded ${s.recordedAt})` : ""}
                </li>
              ))}
            </ul>
          </dd>
        </div>
      ) : null}

      <div>
        <dt className="font-medium text-foreground">Resolution</dt>
        <dd className="text-muted-foreground">
          {provenance.resolution.kind ?? "none"}
          {provenance.resolution.reason ? ` — ${provenance.resolution.reason}` : ""}
          {provenance.resolution.actor ? ` by ${provenance.resolution.actor}` : ""}
        </dd>
      </div>

      {provenance.rawRecordIds.length ? (
        <div>
          <dt className="font-medium text-foreground">Raw records</dt>
          <dd className="break-all text-muted-foreground">{provenance.rawRecordIds.join(", ")}</dd>
        </div>
      ) : null}
    </dl>
  );
}
