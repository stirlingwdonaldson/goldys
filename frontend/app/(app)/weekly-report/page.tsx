"use client";

import { TargetsEditor } from "./targets-editor";
import { InventoryPanels } from "./inventory-panels";
import { DailyDetail, FieldCatalogue, TargetComparison, WeekSummary, WeeklyHistory, disclosureClass } from "./report-sections";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download, RotateCw } from "lucide-react";
import { useApi, useDemoMode } from "@/lib/demo-mode";
import { ApiError } from "@/lib/api/errors";
import { weeklyReportWorkbook } from "@/lib/weekly-report-export";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";

export default function WeeklyReportPage() {
  return <Suspense fallback={<LoadingState rows={8} />}><WeeklyReportContent /></Suspense>;
}

function WeeklyReportContent() {
  const api = useApi();
  const { demo } = useDemoMode();
  const params = useSearchParams();
  const router = useRouter();
  const from = params.get("from") || undefined;
  const count = Number(params.get("weeks") ?? 4);
  const [selected, setSelected] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);
  const query = useQuery({ queryKey: ["weeklyReport", from, count, demo], queryFn: ({ signal }) => api.getWeeklyReport(from, count, { signal }) });
  const update = (key: string, next: string) => {
    const search = new URLSearchParams(params.toString());
    if (next) search.set(key, next); else search.delete(key);
    router.replace(`/weekly-report?${search}`);
  };
  const report = query.data;
  const week = report?.weeks.find(w => w.start === selected) ?? report?.weeks.at(-1);

  function download() {
    if (!report) return;
    setExportError(null);
    try {
      const blob = new Blob([weeklyReportWorkbook(report, demo)], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${demo ? "DEMO-" : ""}Goldys-weekly-${report.from}-to-${report.to}.xlsx`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setExportError("Couldn't create the spreadsheet. Try downloading again.");
    }
  }

  return <div className="flex min-w-0 flex-col gap-6">
    <PageHeader title="Weekly report" description="Review Monday–Sunday performance and the evidence behind it." actions={<>
      <Button variant="outline" onClick={() => query.refetch()} disabled={query.isFetching}><RotateCw aria-hidden="true" />{query.isFetching ? "Refreshing…" : "Refresh"}</Button>
      <Button variant="brand" onClick={download} disabled={!report || query.isFetching || !!query.error}><Download aria-hidden="true" />Download Excel</Button>
    </>} />
    <div className="flex flex-wrap items-end gap-4">
      <div className="space-y-1.5"><Label htmlFor="report-from">First week (Monday)</Label><Input id="report-from" type="date" value={from ?? report?.from ?? ""} onChange={e => update("from", e.target.value)} /></div>
      <div className="flex flex-col gap-1.5"><Label htmlFor="report-weeks">Number of weeks</Label><select id="report-weeks" className="h-9 rounded-md border bg-background px-3 text-sm" value={count} onChange={e => update("weeks", e.target.value)}>{Array.from({ length: 13 }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}</select></div>
    </div>
    {query.isPending ? <LoadingState rows={8} /> : query.error instanceof ApiError && query.error.code === "NOT_PERMITTED" ? <PermissionDenied subject="the weekly report" message={query.error.message} /> : query.error ? <ErrorState title="Couldn't load weekly report" message={query.error.message} onRetry={() => query.refetch()} /> : report && week ? <>
      <div className="space-y-1" aria-live="polite">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Reviewing week · Monday–Sunday</p>
        <h2 className="text-xl font-semibold tabular-nums">{week.start} – {week.end}</h2>
        <p className="text-xs text-muted-foreground">{report.timezone} · {demo ? "Demo snapshot · " : ""}Generated {new Date(report.generatedAt).toLocaleString("en-AU", { timeZone: report.timezone })}. Excel contains all {report.weeks.length} selected weeks from this exact snapshot. Refresh to include new source data.</p>
      </div>
      <WeekSummary week={week} />
      <div className="flex flex-col items-start gap-3 rounded-lg border bg-muted/30 p-4 text-sm sm:flex-row">
        <Badge variant="missing">{week.rows.filter(row => row.status === "UNAVAILABLE" || row.total === null).length} / {week.rows.length} fields unavailable</Badge>
        <div className="min-w-0 flex-1 space-y-1">
          <p className="font-medium">Values remain source-qualified</p>
          <p className="text-muted-foreground">Missing values are unavailable, not zero. Known totals can be incomplete. Wastage includes missing reasons. Physical cash, stock variances and actual COGS need verified inputs. All qualifications remain in the field notes below.</p>
        </div>
      </div>
      {exportError ? <p role="alert" className="text-sm text-destructive">{exportError}</p> : null}
      <WeeklyHistory report={report} week={week} onSelect={setSelected} />
      <TargetComparison week={week} />
      <TargetsEditor key={`${demo}:${week.start}`} week={week} onSaved={() => query.refetch()} />
      <DailyDetail week={week} />
      <details className="min-w-0 rounded-lg border p-4">
        <summary className={disclosureClass}>Wastage reasons & stock counts <span className="mt-1 block text-xs font-normal tabular-nums text-muted-foreground sm:ml-2 sm:mt-0 sm:inline">{week.wastage.lines} wastage lines · {week.stockCounts.length} {week.stockCounts.length === 1 ? "stocktake" : "stocktakes"}</span></summary>
        <div className="mt-4 space-y-6"><InventoryPanels week={week} /></div>
      </details>
      <FieldCatalogue key={week.start} week={week} />
    </> : null}
  </div>;
}
