"use client";

import { invalidateBusinessReads } from "@/lib/query/invalidation";

import { Suspense, useCallback, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { History, Search } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { useToast } from "@/components/feedback/toast";
import { useShellStatus } from "@/components/app-shell/shell-status";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { ReconciliationDrillIn } from "@/components/reconciliation/drill-in";
import { ExceptionsList, type StatusFilter } from "@/components/reconciliation/exceptions-list";
import { ReconciliationAudit } from "@/components/reconciliation/reconciliation-audit";
import { TablePagination, usePageState } from "@/components/data-table/table-pagination";
import type { ReconciliationException } from "@/lib/api";

export default function ReconciliationPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <ReconciliationContent />
    </Suspense>
  );
}

type Tab = "daily" | "product";

/**
 * All view state lives in the URL (tab, filter, search, open record), so Back
 * closes the sheet instead of leaving the page, and any view can be bookmarked
 * or shared (heuristics 3 and 7).
 */
function ReconciliationContent() {
  const queryApi = useApi();
  const api = useApi();
  const queryClient = useQueryClient();
  const saveDaily = useMutation({ mutationFn: api.saveOverride });
  const saveProduct = useMutation({ mutationFn: api.saveProductOverride });
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { toast } = useToast();
  const shell = useShellStatus();

  // Exceptions are bounded open conflicts, so one page (capped at the backend's 200) is the whole
  // set; ExceptionsList then filters and pages client-side over it. Audit, by contrast, grows
  // forever, so it pages on the server.
  const { data: exceptions, isPending: loading, error, refetch: reload } = useQuery({ queryKey: ["listReconciliationExceptions", 0, 200], queryFn: ({ signal }) => queryApi.listReconciliationExceptions(0, 200, { signal }) });
  const { data: productExceptions } = useQuery({ queryKey: ["listProductExceptions", 0, 200], queryFn: ({ signal }) => queryApi.listProductExceptions(0, 200, { signal }) });
  const { page: auditPage, setPage: setAuditPage, pageSize: auditPageSize, setPageSize: setAuditPageSize } =
    usePageState("audit");
  const { data: audit } = useQuery({ queryKey: ["listReconciliationAudit", auditPage, auditPageSize], queryFn: ({ signal }) => queryApi.listReconciliationAudit(auditPage, auditPageSize, { signal }) });

  const tab: Tab = params.get("tab") === "product" ? "product" : "daily";
  const statusParam = params.get("status");
  const status: StatusFilter = statusParam === "conflict" || statusParam === "missing" ? statusParam : "all";
  const query = params.get("q") ?? "";
  const recordId = params.get("record");
  const date = params.get("date");
  const product = params.get("product");
  const sheetOpen = Boolean(recordId || (date && product));

  const [saving, setSaving] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  // Whether *we* pushed the open-record entry; if so, closing pops it so Back stays meaningful.
  const pushedRecord = useRef(false);

  const setParams = useCallback(
    (patch: Record<string, string | null>, mode: "push" | "replace" = "replace") => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        if (v == null || v === "") next.delete(k);
        else next.set(k, v);
      }
      const qs = next.toString();
      const url = qs ? `${pathname}?${qs}` : pathname;
      if (mode === "push") router.push(url, { scroll: false });
      else router.replace(url, { scroll: false });
    },
    [params, pathname, router],
  );

  const openRecord = useCallback(
    (ex: ReconciliationException) => {
      pushedRecord.current = true;
      if (tab === "product") setParams({ record: null, date: ex.id.split(":")[0], product: ex.recordId }, "push");
      else setParams({ record: ex.recordId, date: null, product: null }, "push");
    },
    [setParams, tab],
  );

  function closeRecord() {
    if (pushedRecord.current) {
      pushedRecord.current = false;
      router.back();
    } else {
      setParams({ record: null, date: null, product: null });
    }
  }

  async function handleSave(field: string, source: string, reason: string, label: string) {
    setSaving(true);
    try {
      if (recordId) {
        await saveDaily.mutateAsync({ recordId, field, source, reason });
      } else if (date && product) {
        await saveProduct.mutateAsync({ date, product, source, reason });
      }
      const subject = recordId ? label : product;
      toast({
        title: `${subject} now uses ${source}`,
        description: "Saved to the change history.",
        tone: "success",
      });
      await invalidateBusinessReads(queryClient);
      shell.refresh();
      closeRecord();
    } catch (e) {
      toast({
        title: "Couldn't save your decision",
        description: e instanceof Error ? `${e.message} Your choice is still selected; try again.` : undefined,
        tone: "error",
      });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error && !exceptions) {
    if (error.code === "NOT_PERMITTED") return <PermissionDenied subject="reconciliation data" />;
    return (
      <ErrorState
        title="Couldn't load exceptions"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  const daily = exceptions?.items ?? [];
  const products = productExceptions?.items ?? [];
  const total = daily.length + products.length;
  const current = tab === "daily" ? daily : products;
  const counts = {
    all: current.length,
    conflict: current.filter((e) => e.status === "conflict").length,
    missing: current.filter((e) => e.status === "missing").length,
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Reconciliation"
        status={total > 0 ? <Badge variant="conflict">{total} open</Badge> : <Badge variant="success">All clear</Badge>}
        description="Figures where your sources disagree or one is missing. Fields that agree are hidden."
        actions={
          <Button variant="ghost" size="sm" onClick={() => setHistoryOpen(true)}>
            <History aria-hidden="true" />
            Change history
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        <Tabs value={tab} onValueChange={(v) => setParams({ tab: v === "daily" ? null : v, status: null })}>
          <TabsList aria-label="Reconciliation categories">
            <TabsTrigger value="daily">
              Daily sales <span className="tabular-nums text-muted-foreground">{daily.length}</span>
            </TabsTrigger>
            <TabsTrigger value="product">
              Product sales <span className="tabular-nums text-muted-foreground">{products.length}</span>
            </TabsTrigger>
          </TabsList>
        </Tabs>

        <ToggleGroup
          type="single"
          size="sm"
          variant="outline"
          attached
          value={status}
          onValueChange={(v) => v && setParams({ status: v === "all" ? null : v })}
          aria-label="Filter by status"
        >
          <ToggleGroupItem value="all">All {counts.all}</ToggleGroupItem>
          <ToggleGroupItem value="conflict">Conflicts {counts.conflict}</ToggleGroupItem>
          <ToggleGroupItem value="missing">Missing {counts.missing}</ToggleGroupItem>
        </ToggleGroup>

        <div className="relative ml-auto w-full sm:w-64">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input
            type="search"
            value={query}
            onChange={(e) => setParams({ q: e.target.value })}
            placeholder={tab === "daily" ? "Filter by date" : "Filter by product or date"}
            aria-label="Filter exceptions"
            className="h-9 pl-8"
          />
        </div>
      </div>

      <ExceptionsList
        kind={tab}
        exceptions={current}
        status={status}
        query={query}
        selectedId={tab === "daily" ? recordId : product}
        onReview={openRecord}
        keyboardEnabled={!sheetOpen && !historyOpen}
      />

      <Sheet open={sheetOpen} onOpenChange={(o) => !o && closeRecord()}>
        <SheetContent side="right" className="flex flex-col gap-0 p-0 sm:max-w-md">
          {recordId ? (
            <ReconciliationDrillIn
              fetchRecord={(a, signal) => a.getReconciliationRecord(recordId, { signal })}
              deps={[recordId]}
              onSave={handleSave}
              saving={saving}
            />
          ) : date && product ? (
            <ReconciliationDrillIn
              fetchRecord={(a, signal) => a.getProductRecord(date, product, { signal })}
              deps={[date, product]}
              onSave={handleSave}
              saving={saving}
            />
          ) : null}
        </SheetContent>
      </Sheet>

      {/* History is one click away rather than always rendered under the queue (heuristic 8). */}
      <Sheet open={historyOpen} onOpenChange={setHistoryOpen}>
        <SheetContent side="right" className="flex flex-col gap-4 overflow-y-auto sm:max-w-md">
          <SheetHeader className="text-left">
            <SheetTitle>Change history</SheetTitle>
            <SheetDescription>Every rule change and manual decision, newest first.</SheetDescription>
          </SheetHeader>
          <ReconciliationAudit entries={audit?.items ?? []} />
          <TablePagination
            page={auditPage}
            pageSize={auditPageSize}
            total={audit?.total ?? 0}
            onPageChange={setAuditPage}
            onPageSizeChange={setAuditPageSize}
          />
        </SheetContent>
      </Sheet>
    </div>
  );
}
