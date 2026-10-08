"use client";

import { useState } from "react";
import { useApiData } from "@/lib/use-api-data";
import type { DataPage, GenericRow, RawRecordSummary } from "@/lib/api/types";
import { GenericTable } from "@/components/data-explorer/generic-table";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Section = "raw" | "canonical" | "resolved";

const emptyPage = (): DataPage<GenericRow> => ({ items: [], total: 0, page: 0, size: 50 });

export default function DataPage() {
  const [section, setSection] = useState<Section>("raw");

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Data explorer</h1>
        <p className="text-sm text-muted-foreground">
          Browse raw ingested records, canonical entities, and resolved views.
        </p>
      </div>

      <div className="flex gap-2">
        {(
          [
            ["raw", "Raw"],
            ["canonical", "Canonical"],
            ["resolved", "Resolved"],
          ] as [Section, string][]
        ).map(([id, label]) => (
          <Button
            key={id}
            variant={section === id ? "default" : "outline"}
            size="sm"
            onClick={() => setSection(id)}
          >
            {label}
          </Button>
        ))}
      </div>

      {section === "raw" ? <RawSection /> : null}
      {section === "canonical" ? <CanonicalSection /> : null}
      {section === "resolved" ? <ResolvedSection /> : null}
    </div>
  );
}

function RawSection() {
  const [source, setSource] = useState("");
  const [fetcher, setFetcher] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const rows = useApiData(
    (api) =>
      api.listRawRecords(
        { source: source || undefined, fetcher: fetcher || undefined },
        0,
        50,
      ),
    [source, fetcher],
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-3">
        <Input
          aria-label="Source"
          placeholder="Source (e.g. CTB)"
          value={source}
          onChange={(e) => setSource(e.target.value)}
          className="w-48"
        />
        <Input
          aria-label="Fetcher"
          placeholder="Fetcher (e.g. ctb-invoices-ajax)"
          value={fetcher}
          onChange={(e) => setFetcher(e.target.value)}
          className="w-64"
        />
      </div>

      {rows.loading ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load raw records" message={rows.error.message} onRetry={rows.reload} />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState title="No raw records" description="No ingested payloads match this filter." />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? (
        <div className="rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
                <th className="px-3 py-2 font-medium">Source</th>
                <th className="px-3 py-2 font-medium">Fetcher</th>
                <th className="px-3 py-2 font-medium">Method</th>
                <th className="px-3 py-2 font-medium">Fetched at</th>
                <th className="px-3 py-2 font-medium">Bytes</th>
              </tr>
            </thead>
            <tbody>
              {rows.data.items.map((r: RawRecordSummary) => (
                <RawRow
                  key={r.id}
                  row={r}
                  isOpen={expandedId === r.id}
                  onToggle={() => setExpandedId(expandedId === r.id ? null : r.id)}
                />
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}

function RawRow({
  row,
  isOpen,
  onToggle,
}: {
  row: RawRecordSummary;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <tr className="cursor-pointer border-b last:border-0 hover:bg-muted/40" onClick={onToggle}>
        <td className="px-3 py-2 font-medium">{row.sourceSystem}</td>
        <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{row.fetcherIdentity}</td>
        <td className="px-3 py-2 text-muted-foreground">{row.fetchMethod}</td>
        <td className="px-3 py-2 text-muted-foreground">
          {row.fetchedAt ? new Date(row.fetchedAt).toLocaleString() : "—"}
        </td>
        <td className="px-3 py-2 text-muted-foreground">{row.byteLength}</td>
      </tr>
      {isOpen ? (
        <tr className="border-b bg-muted/20">
          <td colSpan={5} className="px-3 py-3">
            <RawPayload id={row.id} />
          </td>
        </tr>
      ) : null}
    </>
  );
}

function RawPayload({ id }: { id: string }) {
  const detail = useApiData((api) => api.getRawRecord(id), [id]);
  if (detail.loading) return <p className="text-xs text-muted-foreground">Loading payload…</p>;
  if (detail.error) return <p className="text-xs text-destructive">{detail.error.message}</p>;
  if (!detail.data) return null;
  const body = detail.data.isJson ? pretty(detail.data.payload) : detail.data.payload;
  return (
    <div className="space-y-1">
      <p className="text-xs text-muted-foreground">
        sha256 {detail.data.sha256}
        {detail.data.isJson ? "" : " · non-JSON payload"}
      </p>
      <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded bg-muted p-2 text-xs">{body}</pre>
    </div>
  );
}

function CanonicalSection() {
  const entities = useApiData((api) => api.listCanonicalEntities(), []);
  const [entity, setEntity] = useState("");
  const selected = entity || entities.data?.[0]?.id || "";
  const rows = useApiData(
    (api) => (selected ? api.listCanonicalRows(selected, 0, 50) : Promise.resolve(emptyPage())),
    [selected],
  );
  const descriptor = entities.data?.find((e) => e.id === selected);

  if (entities.loading) return <LoadingState rows={3} />;
  if (entities.error) {
    return (
      <ErrorState title="Couldn't load entities" message={entities.error.message} onRetry={entities.reload} />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <label className="text-sm text-muted-foreground">
        Entity
        <select
          aria-label="Entity"
          className="ml-2 rounded-md border bg-background px-2 py-1 text-sm"
          value={selected}
          onChange={(e) => setEntity(e.target.value)}
        >
          {(entities.data ?? []).map((e) => (
            <option key={e.id} value={e.id}>
              {e.label}
            </option>
          ))}
        </select>
      </label>

      {rows.loading ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load rows" message={rows.error.message} onRetry={rows.reload} />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState
          title={descriptor?.placeholder ? "Model placeholder" : "No rows"}
          description={
            descriptor?.placeholder
              ? "This entity's model exists but has no ingestion source yet."
              : "No canonical rows for this entity yet."
          }
        />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? <GenericTable rows={rows.data.items} /> : null}
    </div>
  );
}

function ResolvedSection() {
  const domains = useApiData((api) => api.listResolvedDomains(), []);
  const [domain, setDomain] = useState("");
  const selected = domain || domains.data?.[0]?.id || "";
  const rows = useApiData(
    (api) => (selected ? api.listResolvedRows(selected, 0, 50) : Promise.resolve(emptyPage())),
    [selected],
  );

  if (domains.loading) return <LoadingState rows={3} />;
  if (domains.error) {
    return (
      <ErrorState title="Couldn't load domains" message={domains.error.message} onRetry={domains.reload} />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <label className="text-sm text-muted-foreground">
        Domain
        <select
          aria-label="Domain"
          className="ml-2 rounded-md border bg-background px-2 py-1 text-sm"
          value={selected}
          onChange={(e) => setDomain(e.target.value)}
        >
          {(domains.data ?? []).map((d) => (
            <option key={d.id} value={d.id}>
              {d.label}
            </option>
          ))}
        </select>
      </label>

      {rows.loading ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load rows" message={rows.error.message} onRetry={rows.reload} />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState title="No rows" description="No resolved rows for this domain yet." />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? <GenericTable rows={rows.data.items} /> : null}
    </div>
  );
}

function pretty(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}
