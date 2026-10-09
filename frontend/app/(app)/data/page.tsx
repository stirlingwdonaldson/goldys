"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { useApiData } from "@/lib/use-api-data";
import type { DataPage, GenericRow, RawRecordSummary } from "@/lib/api/types";
import { GenericTable } from "@/components/data-explorer/generic-table";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

type Section = "raw" | "canonical" | "resolved";

const PAGE_SIZE = 50;

const emptyPage = (): DataPage<GenericRow> => ({ items: [], total: 0, page: 0, size: PAGE_SIZE });

/** Which existing screen each resolved domain jumps to. */
const RESOLVED_SCREENS: Record<string, string> = {
  resolved_daily_sales: "/sales",
  resolved_product_sales: "/sales",
  resolved_reservation_day: "/reservations",
  resolved_labour_day: "/staff",
  resolved_inventory_day: "/kitchen",
};

export default function DataPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <DataExplorer />
    </Suspense>
  );
}

function DataExplorer() {
  const searchParams = useSearchParams();
  const layer = searchParams.get("layer");
  const deepLinkId = searchParams.get("id");
  const [section, setSection] = useState<Section>(
    layer === "canonical" ? "canonical" : layer === "resolved" ? "resolved" : "raw",
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Data explorer" description="Browse raw ingested records, canonical entities, and resolved views." />

      <Tabs value={section} onValueChange={(v) => setSection(v as Section)}>
        <TabsList aria-label="Data layer">
          <TabsTrigger value="raw">Raw</TabsTrigger>
          <TabsTrigger value="canonical">Canonical</TabsTrigger>
          <TabsTrigger value="resolved">Resolved</TabsTrigger>
        </TabsList>
      </Tabs>

      {section === "raw" ? <RawSection initialExpandedId={deepLinkId} /> : null}
      {section === "canonical" ? <CanonicalSection /> : null}
      {section === "resolved" ? <ResolvedSection /> : null}
    </div>
  );
}

function Pager({
  page,
  total,
  onPage,
}: {
  page: number;
  total: number;
  onPage: (p: number) => void;
}) {
  const hasPrev = page > 0;
  const hasNext = (page + 1) * PAGE_SIZE < total;
  if (!hasPrev && !hasNext) return null;
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Button variant="outline" size="sm" disabled={!hasPrev} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span>
        {total > 0 ? `${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, total)}` : "0"} of{" "}
        {total}
      </span>
      <Button variant="outline" size="sm" disabled={!hasNext} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </div>
  );
}

function RawSection({ initialExpandedId }: { initialExpandedId?: string | null }) {
  const [source, setSource] = useState("");
  const [fetcher, setFetcher] = useState("");
  const [method, setMethod] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(initialExpandedId ?? null);

  const rows = useApiData(
    (api) =>
      api.listRawRecords(
        {
          source: source || undefined,
          fetcher: fetcher || undefined,
          method: method || undefined,
          from: from || undefined,
          to: to || undefined,
        },
        page,
        PAGE_SIZE,
      ),
    [source, fetcher, method, from, to, page],
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-3">
        <Input
          aria-label="Source"
          placeholder="Source (e.g. CTB)"
          value={source}
          onChange={(e) => setSource(e.target.value)}
          className="w-44"
        />
        <Input
          aria-label="Fetcher"
          placeholder="Fetcher (e.g. ctb-invoices-ajax)"
          value={fetcher}
          onChange={(e) => setFetcher(e.target.value)}
          className="w-60"
        />
        <select
          aria-label="Method"
          className="rounded-md border bg-background px-2 py-1 text-sm"
          value={method}
          onChange={(e) => setMethod(e.target.value)}
        >
          <option value="">All methods</option>
          <option value="API">API</option>
          <option value="FILE_EXPORT">File export</option>
          <option value="SCRAPE">Scrape</option>
          <option value="MANUAL">Manual</option>
        </select>
        <Input
          aria-label="From"
          placeholder="From (ISO, e.g. 2026-10-01T00:00:00Z)"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          className="w-56"
        />
        <Input
          aria-label="To"
          placeholder="To (ISO)"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          className="w-56"
        />
      </div>

      {rows.loading ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState
          title="Couldn't load raw records"
          message={rows.error.message}
          onRetry={rows.reload}
        />
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

      {rows.data ? (
        <Pager page={page} total={rows.data.total} onPage={setPage} />
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
        {detail.data.isJson ? "" : " · non-JSON payload (base64)"}
      </p>
      <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded bg-muted p-2 text-xs">
        {body}
      </pre>
    </div>
  );
}

function CanonicalSection() {
  const entities = useApiData((api) => api.listCanonicalEntities(), []);
  const [entity, setEntity] = useState("");
  const [page, setPage] = useState(0);
  const selected = entity || entities.data?.[0]?.id || "";
  const rows = useApiData(
    (api) => (selected ? api.listCanonicalRows(selected, page, PAGE_SIZE) : Promise.resolve(emptyPage())),
    [selected, page],
  );
  const descriptor = entities.data?.find((e) => e.id === selected);

  if (entities.loading) return <LoadingState rows={3} />;
  if (entities.error) {
    return (
      <ErrorState
        title="Couldn't load entities"
        message={entities.error.message}
        onRetry={entities.reload}
      />
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
          onChange={(e) => {
            setEntity(e.target.value);
            setPage(0);
          }}
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

      {rows.data ? <Pager page={page} total={rows.data.total} onPage={setPage} /> : null}
    </div>
  );
}

function ResolvedSection() {
  const domains = useApiData((api) => api.listResolvedDomains(), []);
  const [domain, setDomain] = useState("");
  const [page, setPage] = useState(0);
  const selected = domain || domains.data?.[0]?.id || "";
  const rows = useApiData(
    (api) => (selected ? api.listResolvedRows(selected, page, PAGE_SIZE) : Promise.resolve(emptyPage())),
    [selected, page],
  );
  const screen = RESOLVED_SCREENS[selected];

  if (domains.loading) return <LoadingState rows={3} />;
  if (domains.error) {
    return (
      <ErrorState
        title="Couldn't load domains"
        message={domains.error.message}
        onRetry={domains.reload}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <label className="text-sm text-muted-foreground">
          Domain
          <select
            aria-label="Domain"
            className="ml-2 rounded-md border bg-background px-2 py-1 text-sm"
            value={selected}
            onChange={(e) => {
              setDomain(e.target.value);
              setPage(0);
            }}
          >
            {(domains.data ?? []).map((d) => (
              <option key={d.id} value={d.id}>
                {d.label}
              </option>
            ))}
          </select>
        </label>
        {screen ? (
          <Link
            href={screen}
            className="text-sm text-muted-foreground underline underline-offset-2"
          >
            Open in screen
          </Link>
        ) : null}
      </div>

      {rows.loading ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load rows" message={rows.error.message} onRetry={rows.reload} />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState title="No rows" description="No resolved rows for this domain yet." />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? <GenericTable rows={rows.data.items} /> : null}

      {rows.data ? <Pager page={page} total={rows.data.total} onPage={setPage} /> : null}
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
