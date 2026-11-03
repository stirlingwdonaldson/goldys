"use client";

import { TargetsEditor } from "./targets-editor";
import { InventoryPanels } from "./inventory-panels";
import { reportPercent } from "@/lib/weekly-report-targets";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download, RotateCw } from "lucide-react";
import { useApi, useDemoMode } from "@/lib/demo-mode";
import { ApiError } from "@/lib/api/errors";
import type { WeeklyReportRow, WeeklyReportWeek } from "@/lib/api/types";
import { formatCurrency, formatDay, formatNumber } from "@/lib/format";
import { weeklyReportWorkbook } from "@/lib/weekly-report-export";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableCaption } from "@/components/ui/table";

function value(row: WeeklyReportRow, amount: number | null) {
  if (row.unit === "AUD") return formatCurrency(amount);
  if (row.unit === "PERCENT") return reportPercent(amount);
  return formatNumber(amount, 2);
}

function DailyTables({ week }: { week: WeeklyReportWeek }) {
  return [...new Set(week.rows.map(r => r.section))].map(section => <section key={section} aria-label={section}>
    <h3 className="mb-2 font-semibold">{section}</h3>
    <Table><TableHeader><TableRow><TableHead>Field</TableHead><TableHead className="text-right">Weekly value</TableHead><TableHead>Status and basis</TableHead>{week.dates.map(d => <TableHead className="text-right" key={d}>{formatDay(d)}</TableHead>)}</TableRow></TableHeader>
      <TableBody>{week.rows.filter(r => r.section === section).map(row => <TableRow key={row.id}>
        <TableCell className="font-medium">{row.label}</TableCell>
        <TableCell className="text-right font-medium tabular-nums">{value(row, row.total)}</TableCell>
        <TableCell className="min-w-48 !whitespace-normal"><details><summary className="cursor-pointer text-sm">{row.status === "UNAVAILABLE" ? "Unavailable" : "Qualified"}</summary><ul className="mt-2 space-y-1 text-sm text-muted-foreground">{row.notes.map((note, i) => <li key={i}>{note}</li>)}</ul></details></TableCell>
        {row.days.map((amount, i) => <TableCell className="text-right tabular-nums" key={i}>{value(row, amount)}</TableCell>)}
      </TableRow>)}</TableBody>
    </Table>
  </section>);
}

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
    <PageHeader title="Weekly report" description="Sales, takings, wastage, costs and reviews. Monday–Sunday reporting, without labour." actions={<>
      <Button variant="outline" onClick={() => query.refetch()} disabled={query.isFetching}><RotateCw aria-hidden="true" />Refresh</Button>
      <Button onClick={download} disabled={!report || query.isFetching || !!query.error}><Download aria-hidden="true" />Download Excel</Button>
    </>} />
    <div className="flex flex-wrap items-end gap-4">
      <div className="space-y-1.5"><Label htmlFor="report-from">First week (Monday)</Label><Input id="report-from" type="date" value={from ?? report?.from ?? ""} onChange={e => update("from", e.target.value)} /></div>
      <div className="space-y-1.5"><Label htmlFor="report-weeks">Number of weeks</Label><select id="report-weeks" className="h-9 rounded-md border bg-background px-3 text-sm" value={count} onChange={e => update("weeks", e.target.value)}>{Array.from({ length: 13 }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}</select></div>
    </div>
    {query.isPending ? <LoadingState rows={8} /> : query.error instanceof ApiError && query.error.code === "NOT_PERMITTED" ? <PermissionDenied subject="the weekly report" message={query.error.message} /> : query.error ? <ErrorState title="Couldn't load weekly report" message={query.error.message} onRetry={() => query.refetch()} /> : report && week ? <>
      <p className="text-sm text-muted-foreground">{demo ? "Demo snapshot. " : ""}{report.from} to {report.to} · {report.timezone} · Generated {new Date(report.generatedAt).toLocaleString("en-AU", { timeZone: report.timezone })}. Excel contains this exact snapshot. Refresh to include new source data.</p>
      <div className="rounded-lg border p-4 text-sm"><p className="font-medium">Values remain source-qualified</p><p className="mt-1 text-muted-foreground">Missing values are unavailable, not zero. Known totals can be incomplete. Targets carry forward from the workbook or subsequent edits. Wastage includes missing reasons. Food/beverage splits, stock variances and COGS still need verified inputs. Invoice purchases are separate from COGS.</p></div>
      {exportError ? <p role="alert" className="text-sm text-destructive">{exportError}</p> : null}
      <Table><TableCaption>Weekly history. Select a week for daily figures and every report field.</TableCaption>
        <TableHeader><TableRow><TableHead>Week</TableHead><TableHead className="text-right">Sales ex GST</TableHead><TableHead className="text-right">Recorded payments</TableHead><TableHead className="text-right">Review rating</TableHead></TableRow></TableHeader>
        <TableBody>{report.weeks.map(w => <TableRow key={w.start} data-state={w.start === week.start ? "selected" : undefined}>
          <TableCell><Button variant="ghost" aria-pressed={w.start === week.start} onClick={() => setSelected(w.start)}>{w.start} – {w.end}</Button></TableCell>
          {["net_sales", "payments", "review_average"].map(id => { const row = w.rows.find(r => r.id === id)!; return <TableCell className="text-right tabular-nums" key={id}>{value(row, row.total)}</TableCell>; })}
        </TableRow>)}</TableBody>
      </Table>
      <div aria-live="polite"><h2 className="text-lg font-semibold">{week.start} – {week.end}</h2><p className="text-sm text-muted-foreground">Weekly values use the metric catalogue. Review ratings are weekly averages, not sums. Scroll tables horizontally for daily columns.</p></div>
      <TargetsEditor key={`${demo}:${week.start}`} week={week} onSaved={() => query.refetch()} />
      <InventoryPanels week={week} />
      <DailyTables week={week} />
    </> : null}
  </div>;
}
