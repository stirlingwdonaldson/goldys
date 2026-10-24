"use client";

import { Table } from "@/components/ui/table";

import { Suspense, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { RESOLVED_SCREENS } from "@/lib/data-layers";
import type { DataPage, GenericRow, RawRecordSummary } from "@/lib/api/types";
import { GenericTable } from "@/components/data-explorer/generic-table";
import { QueryBuilder } from "@/components/query-builder/query-builder";
import { DEFAULT_PAGE_SIZE, TablePagination } from "@/components/data-table/table-pagination";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { EmptyState } from "@/components/states/empty-state";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

import { explorerPage, explorerUrl, rawScope, validRawPeriod } from "@/components/data-explorer/url-state";
import { Button } from "@/components/ui/button";

type Section = "raw" | "canonical" | "resolved" | "query";

const emptyPage = (): DataPage<GenericRow> => ({ items: [], total: 0, page: 0, size: DEFAULT_PAGE_SIZE });


export default function DataPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <DataExplorer />
    </Suspense>
  );
}

function useExplorerUrl() {
  const search = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const params = new URLSearchParams(search.toString());
  const update = (patch: Record<string, string | null>) => router.push(`${pathname}?${explorerUrl(params, patch)}`, { scroll: false });
  const { page, pageSize } = explorerPage(params);
  return { params, update, page, pageSize,
    setPage: (value: number) => update({ page: String(value) }),
    setPageSize: (value: number) => update({ size: String(value), page: "0" }),
  };
}

function DataExplorer() {
  const { params, update } = useExplorerUrl();
  const layer = params.get("layer");
  const section: Section = layer === "canonical" || layer === "resolved" || layer === "query" ? layer : "raw";
  const deepLinkId = params.get("id");
  const deepLinkEntity = params.get("entity");
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Data explorer" description="Browse raw ingested records, canonical entities, and resolved views." />

      <Tabs value={section} onValueChange={(v) => update({ layer: v, page: "0", entity: null, id: null })}>
        <TabsList aria-label="Data layer">
          <TabsTrigger value="raw">Raw</TabsTrigger>
          <TabsTrigger value="canonical">Canonical</TabsTrigger>
          <TabsTrigger value="resolved">Resolved</TabsTrigger>
          <TabsTrigger value="query">Query</TabsTrigger>
        </TabsList>
      </Tabs>

      {section === "raw" ? <RawSection initialExpandedId={deepLinkId} /> : null}
      {section === "canonical" ? (
        <CanonicalSection initialEntity={layer === "canonical" ? deepLinkEntity : null} />
      ) : null}
      {section === "resolved" ? (
        <ResolvedSection initialDomain={layer === "resolved" ? deepLinkEntity : null} />
      ) : null}
      {section === "query" ? <QueryBuilder /> : null}
    </div>
  );
}

function RawSection({ initialExpandedId }: { initialExpandedId?: string | null }) {
  const queryApi = useApi();
  const { params, update, page, setPage, pageSize, setPageSize } = useExplorerUrl();
  const { source, fetcher, method, from, to } = rawScope(params);
  const [draft, setDraft] = useState({ source, fetcher, method, from, to });
  useEffect(() => { setDraft({ source, fetcher, method, from, to }); }, [source, fetcher, method, from, to]);
  const [filterError, setFilterError] = useState<string | null>(null);
  function apply() {
    if (!validRawPeriod(draft)) {
      setFilterError("Use valid ISO date-times with From before To."); return;
    }
    setFilterError(null);
    update({ ...draft, page: "0" });
  }
  const [expandedId, setExpandedId] = useState<string | null>(initialExpandedId ?? null);

  const rows = useQuery({ queryKey: ["listRawRecords", {
          source: source || undefined,
          fetcher: fetcher || undefined,
          method: method || undefined,
          from: from || undefined,
          to: to || undefined,
        }, page, pageSize], queryFn: ({ signal }) => queryApi.listRawRecords({
          source: source || undefined,
          fetcher: fetcher || undefined,
          method: method || undefined,
          from: from || undefined,
          to: to || undefined,
        }, page, pageSize, { signal }
      ) });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-3">
        <Input
          aria-label="Source"
          placeholder="Source (e.g. CTB)"
          value={draft.source}
          onChange={(e) => setDraft({ ...draft, source: e.target.value })}
          className="w-44"
        />
        <Input
          aria-label="Fetcher"
          placeholder="Fetcher (e.g. ctb-invoices-ajax)"
          value={draft.fetcher}
          onChange={(e) => setDraft({ ...draft, fetcher: e.target.value })}
          className="w-60"
        />
        <Select value={draft.method || "all"} onValueChange={(v) => setDraft({ ...draft, method: v === "all" ? "" : v })}>
          <SelectTrigger aria-label="Method" className="w-40">
            <SelectValue placeholder="All methods" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All methods</SelectItem>
            <SelectItem value="API">API</SelectItem>
            <SelectItem value="FILE_EXPORT">File export</SelectItem>
            <SelectItem value="SCRAPE">Scrape</SelectItem>
            <SelectItem value="MANUAL">Manual</SelectItem>
          </SelectContent>
        </Select>
        <Input
          aria-label="From"
          placeholder="From (ISO, e.g. 2026-10-01T00:00:00Z)"
          value={draft.from}
          onChange={(e) => setDraft({ ...draft, from: e.target.value })}
          className="w-56"
        />
        <Input
          aria-label="To"
          placeholder="To (ISO)"
          value={draft.to}
          onChange={(e) => setDraft({ ...draft, to: e.target.value })}
          className="w-56"
        />
      </div>

      {filterError ? <p role="alert">{filterError}</p> : null}
      <Button onClick={apply} className="w-fit">Apply filters</Button>
      {rows.isPending ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState
          title="Couldn't load raw records"
          message={rows.error.message}
          onRetry={rows.refetch}
        />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState title="No raw records" description="No ingested payloads match this filter." />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? (
        <div className="rounded-lg border">
          <Table className="w-full text-sm">
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
          </Table>
        </div>
      ) : null}

      {rows.data ? (
        <TablePagination
          page={page}
          pageSize={pageSize}
          total={rows.data.total}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
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
  const queryApi = useApi();
  const detail = useQuery({ queryKey: ["getRawRecord", id], queryFn: ({ signal }) => queryApi.getRawRecord(id, { signal }) });
  if (detail.isPending) return <p className="text-xs text-muted-foreground">Loading payload…</p>;
  if (detail.isLoadingError) return <p className="text-xs text-destructive">{detail.error.message}</p>;
  if (!detail.data) return null;
  const body = detail.data.isJson ? pretty(detail.data.payload) : detail.data.payload;
  return (
    <div className="space-y-1">
      <p className="text-xs text-muted-foreground">
        sha256 {detail.data.sha256}
        {detail.data.isJson ? "" : " · non-JSON payload (base64)"}
      </p>
      <pre className="max-h-96 overflow-auto whitespace-pre rounded bg-muted p-2 text-xs">
        {body}
      </pre>
    </div>
  );
}

function CanonicalSection({ initialEntity }: { initialEntity?: string | null }) {
  const queryApi = useApi();
  const entities = useQuery({ queryKey: ["listCanonicalEntities"], queryFn: ({ signal }) => queryApi.listCanonicalEntities({ signal }) });
  const { params, update, page, setPage, pageSize, setPageSize } = useExplorerUrl();
  const entity = params.get("entity") ?? initialEntity ?? "";
  const selected = entity || entities.data?.[0]?.id || "";
  const rows = useQuery({ queryKey: ["listCanonicalRows", selected, page, pageSize], queryFn: ({ signal }) => (selected ? queryApi.listCanonicalRows(selected, page, pageSize, { signal }) : Promise.resolve(emptyPage())) });
  const descriptor = entities.data?.find((e) => e.id === selected);

  if (entities.isPending) return <LoadingState rows={3} />;
  if (entities.isLoadingError) {
    return (
      <ErrorState
        title="Couldn't load entities"
        message={entities.error.message}
        onRetry={entities.refetch}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <label className="text-sm text-muted-foreground">
        Entity
        <Select
          value={selected}
          onValueChange={(v) => {
            update({ entity: v, page: "0" });
          }}
        >
          <SelectTrigger aria-label="Entity" className="ml-2 w-56">
            <SelectValue placeholder="Choose entity" />
          </SelectTrigger>
          <SelectContent>
            {(entities.data ?? []).map((e) => (
              <SelectItem key={e.id} value={e.id}>
                {e.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </label>

      {rows.isPending ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load rows" message={rows.error.message} onRetry={rows.refetch} />
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

      {rows.data ? (
        <TablePagination
          page={page}
          pageSize={pageSize}
          total={rows.data.total}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
      ) : null}
    </div>
  );
}

function ResolvedSection({ initialDomain }: { initialDomain?: string | null }) {
  const queryApi = useApi();
  const domains = useQuery({ queryKey: ["listResolvedDomains"], queryFn: ({ signal }) => queryApi.listResolvedDomains({ signal }) });
  const { params, update, page, setPage, pageSize, setPageSize } = useExplorerUrl();
  const domain = params.get("entity") ?? initialDomain ?? "";
  const selected = domain || domains.data?.[0]?.id || "";
  const rows = useQuery({ queryKey: ["listResolvedRows", selected, page, pageSize], queryFn: ({ signal }) => (selected ? queryApi.listResolvedRows(selected, page, pageSize, { signal }) : Promise.resolve(emptyPage())) });
  const screen = RESOLVED_SCREENS[selected]?.href;

  if (domains.isPending) return <LoadingState rows={3} />;
  if (domains.isLoadingError) {
    return (
      <ErrorState
        title="Couldn't load domains"
        message={domains.error.message}
        onRetry={domains.refetch}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <label className="text-sm text-muted-foreground">
          Domain
          <Select
            value={selected}
            onValueChange={(v) => {
              update({ entity: v, page: "0" });
            }}
          >
            <SelectTrigger aria-label="Domain" className="ml-2 w-56">
              <SelectValue placeholder="Choose domain" />
            </SelectTrigger>
            <SelectContent>
              {(domains.data ?? []).map((d) => (
                <SelectItem key={d.id} value={d.id}>
                  {d.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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

      {rows.isPending ? <LoadingState rows={4} /> : null}
      {rows.error ? (
        <ErrorState title="Couldn't load rows" message={rows.error.message} onRetry={rows.refetch} />
      ) : null}

      {rows.data && rows.data.items.length === 0 ? (
        <EmptyState title="No rows" description="No resolved rows for this domain yet." />
      ) : null}

      {rows.data && rows.data.items.length > 0 ? <GenericTable rows={rows.data.items} /> : null}

      {rows.data ? (
        <TablePagination
          page={page}
          pageSize={pageSize}
          total={rows.data.total}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
      ) : null}
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
