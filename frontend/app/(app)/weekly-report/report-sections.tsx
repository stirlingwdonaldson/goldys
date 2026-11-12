import { useState, type ReactNode } from "react";
import { Info } from "lucide-react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { WeeklyReport, WeeklyReportRow, WeeklyReportWeek } from "@/lib/api/types";
import { formatCurrency, formatDay, formatNumber } from "@/lib/format";
import { reportPercent } from "@/lib/weekly-report-targets";
import { presentWeeklyAssessment } from "@/lib/weekly-report-assessment";
import { StatCard } from "@/components/data-display/stat-card";
import { Section } from "@/components/layout/section";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableCaption } from "@/components/ui/table";

const metric = (week: WeeklyReportWeek, id: string) => week.rows.find(row => row.id === id)!;
const available = (row: WeeklyReportRow) => row.status === "QUALIFIED" && row.total !== null;
const datesWithValues = (row: WeeklyReportRow) => row.days.filter(day => day !== null).length;

export function reportValue(row: WeeklyReportRow, amount: number | null) {
  if (row.unit === "AUD") return formatCurrency(amount);
  if (row.unit === "PERCENT") return reportPercent(amount);
  return `${formatNumber(amount, 2)}${row.unit === "STARS" && amount !== null ? " stars" : ""}`;
}

export function SourceNotes({ row, week, target = false, onOpenChange }: { row: WeeklyReportRow; week: WeeklyReportWeek; target?: boolean; onOpenChange?: (open: boolean) => void }) {
  const [notesOpen, setNotesOpen] = useState(false);
  const [hintOpen, setHintOpen] = useState(false);
  const setting = target ? week.targets.find(t => t.id === row.id) : undefined;
  const firstNote = row.notes[0] ?? "No additional source notes supplied.";
  const preview = firstNote.length > 180 ? `${firstNote.slice(0, 177)}…` : firstNote;
  return <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
    <span>{available(row) ? target ? "Target" : "Qualified" : "Unavailable"}</span>
    <TooltipProvider delayDuration={200}>
      <Tooltip open={!notesOpen && hintOpen} onOpenChange={setHintOpen}><Popover open={notesOpen} onOpenChange={open => { setNotesOpen(open); onOpenChange?.(open); }}>
        <TooltipTrigger asChild><PopoverTrigger asChild>
          <Button variant="ghost" size="icon" aria-label={`Source notes for ${row.label}, week of ${week.start}`} className="h-8 w-8 rounded-full text-muted-foreground hover:text-foreground">
            <Info className="h-4 w-4" aria-hidden="true" />
          </Button>
        </PopoverTrigger></TooltipTrigger>
        <TooltipContent className="max-w-xs whitespace-normal text-left leading-relaxed">{preview}<span className="mt-1 block text-xs text-muted-foreground">Select for full source notes.</span></TooltipContent>
        <PopoverContent onEscapeKeyDown={event => { event.preventDefault(); event.stopPropagation(); setNotesOpen(false); onOpenChange?.(false); }} aria-label={`${row.label} source notes`} className="max-h-80 w-80 max-w-full overflow-y-auto text-sm">
          <p className="font-semibold">{row.label}</p>
          <p className="my-2 text-xs text-muted-foreground">{week.start} – {week.end} · {row.unit}</p>
          {setting ? <p className="mb-2">Effective {setting.effectiveFrom} · {setting.source}. Carried forward until changed.</p> : null}
          <ul className="list-disc space-y-2 pl-4 text-muted-foreground">{row.notes.map((note, i) => <li key={i}>{note}</li>)}</ul>
        </PopoverContent>
      </Popover></Tooltip>
    </TooltipProvider>
  </span>;
}

export function WeekSummary({ week }: { week: WeeklyReportWeek }) {
  return <section aria-label="Selected week summary" className="grid gap-3 sm:grid-cols-3">
    {[
      ["net_sales", "Total sales ex GST", "AUD · recorded sales"],
      ["total_wastage", "Recorded wastage", "AUD · known source costs; tax basis unverified"],
      ["review_average", "Review rating", "Stars · weekly average by review date"],
    ].map(([id, label, basis]) => {
      const row = metric(week, id);
      return <StatCard key={id} label={label} value={reportValue(row, row.total)}
        hint={id === "review_average" ? basis : `${basis} · ${datesWithValues(row)}/7 dates with values`}
        footer={<SourceNotes row={row} week={week} />} />;
    })}
  </section>;
}

export function WeeklyHistory({ report, week, onSelect }: { report: WeeklyReport; week: WeeklyReportWeek; onSelect: (start: string) => void }) {
  return <Section title="Weekly comparison" description="Recorded weekly values, with source qualifications. Select a week to review its targets and daily detail.">
    <Table aria-label="Weekly comparison">
      <TableCaption>All weeks are Monday–Sunday, {report.timezone}. Dates with values do not certify source completeness.</TableCaption>
      <TableHeader><TableRow><TableHead scope="col">Week</TableHead><TableHead scope="col" className="text-right">Sales ex GST (AUD)</TableHead><TableHead scope="col" className="text-right">Recorded wastage (AUD)</TableHead><TableHead scope="col" className="text-right">Review rating (stars)</TableHead></TableRow></TableHeader>
      <TableBody>{report.weeks.map(w => <TableRow key={w.start} data-state={w.start === week.start ? "selected" : undefined}>
        <TableHead scope="row"><Button variant="ghost" aria-label={`${w.start} – ${w.end}`} aria-pressed={w.start === week.start} onClick={() => onSelect(w.start)}>{formatDay(w.start)} – {formatDay(w.end)}</Button></TableHead>
        {["net_sales", "total_wastage", "review_average"].map(id => {
          const row = metric(w, id);
          return <TableCell className="text-right tabular-nums" key={id}>
            {reportValue(row, row.total)}<span className="ml-2 text-xs text-muted-foreground">{available(row) ? id === "review_average" ? "Qualified" : `${datesWithValues(row)}/7 dates · qualified` : "Unavailable"}</span>
          </TableCell>;
        })}
      </TableRow>)}</TableBody>
    </Table>
  </Section>;
}

export function TargetComparison({ week, actions }: { week: WeeklyReportWeek; actions?: ReactNode }) {
  return <Section title="Effective weekly targets" actions={actions} description="Sales minima and recorded wastage rate maxima. Assessments remain source-qualified; incomplete comparison evidence withholds verdicts.">
    <Table aria-label="Effective weekly targets">
      <TableHeader><TableRow><TableHead scope="col">Measure</TableHead><TableHead scope="col" className="text-right">Weekly value</TableHead><TableHead scope="col" className="text-right">Effective target</TableHead><TableHead scope="col">Assessment</TableHead><TableHead scope="col">Basis</TableHead></TableRow></TableHeader>
      <TableBody>{[
        ["net_sales", "sales_target", "sales_difference"],
        ["food_sales", "food_target", "food_difference"],
        ["beverage_sales", "beverage_target", "beverage_difference"],
        ["total_wastage_percent", "total_wastage_target_percent"],
        ["food_wastage_percent", "food_wastage_target_percent"],
        ["beverage_wastage_percent", "beverage_wastage_target_percent"],
      ].map(([id, targetId, differenceId]) => {
        const row = metric(week, id);
        const target = metric(week, targetId);
        const result = presentWeeklyAssessment(week.assessments?.[id]);
        const difference = differenceId ? metric(week, differenceId) : undefined;
        return <TableRow key={id}>
          <TableHead scope="row" className="font-medium text-foreground">{row.label}{row.unit === "AUD" ? " (AUD)" : ""}</TableHead>
          <TableCell className="text-right tabular-nums">{reportValue(row, row.total)}</TableCell>
          <TableCell className="text-right tabular-nums">{reportValue(target, target.total)}<SourceNotes row={target} week={week} target /></TableCell>
          <TableCell><Badge variant={result.tone === "good" ? "success" : result.tone === "bad" ? "conflict" : "missing"}>{result.label}</Badge>{difference ? <span className="ml-2 tabular-nums">{reportValue(difference, difference.total)}</span> : null}</TableCell>
          <TableCell><SourceNotes row={row} week={week} /></TableCell>
        </TableRow>;
      })}</TableBody>
    </Table>
  </Section>;
}

export function DailyDetail({ week }: { week: WeeklyReportWeek }) {
  const rows = ["food_sales", "beverage_sales", "net_sales", "total_wastage"].map(id => metric(week, id));
  return <Section title="Daily detail" description="AUD · sales ex GST and recorded wastage at source cost. — means unavailable; weekly targets are not allocated to days. Review ratings are weekly only.">
    <Table aria-label="Daily detail">
      <TableHeader><TableRow><TableHead scope="col">Day</TableHead>{rows.map(row => <TableHead scope="col" className="text-right" key={row.id}>{row.id === "total_wastage" ? "Recorded wastage" : row.label}</TableHead>)}</TableRow></TableHeader>
      <TableBody>{week.dates.map((date, i) => <TableRow key={date}>
        <TableHead scope="row" className="font-medium text-foreground">{formatDay(date)}</TableHead>
        {rows.map(row => <TableCell key={row.id} className="text-right tabular-nums" aria-label={row.days[i] === null ? `${row.label}: unavailable` : undefined}>{reportValue(row, row.days[i])}</TableCell>)}
      </TableRow>)}</TableBody>
    </Table>
    <div className="flex flex-wrap gap-2">{rows.map(row => <span key={row.id} className="text-xs text-muted-foreground">{row.id === "total_wastage" ? "Wastage" : row.label}: <SourceNotes row={row} week={week} /></span>)}</div>
  </Section>;
}
