"use client";

import { useState } from "react";
import { ArrowUpRight, BookOpen, ClipboardList, ListFilter } from "lucide-react";
import type { WeeklyReportWeek } from "@/lib/api/types";
import { formatDay } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";
import { WastageReasons, StockCounts } from "./inventory-panels";
import { reportValue, SourceNotes } from "./report-sections";
import { useReportPanel } from "./use-report-panel";

export function FieldCatalogue({ week }: { week: WeeklyReportWeek }) {
  const panel = useReportPanel("details", week.start);
  const [notesOpen, setNotesOpen] = useState(false);
  const sections = [...new Set(week.rows.map(row => row.section))];
  const options = [...sections, "Wastage reasons", "Stock counts"];
  const selected = options.includes(panel.tab ?? "") ? panel.tab! : sections[0];
  const missing = (section: string) => week.rows.filter(row => row.section === section && (row.status === "UNAVAILABLE" || row.total === null)).length;
  return <section aria-label="All report fields" className="space-y-4 border-t pt-6">
    <div className="flex flex-wrap items-baseline justify-between gap-2">
      <h2 className="text-sm font-semibold">Report detail</h2>
      <span className="text-xs tabular-nums text-muted-foreground">{week.rows.length} fields · {week.rows.filter(row => row.status === "UNAVAILABLE" || row.total === null).length} unavailable</span>
    </div>
    <p className="text-sm text-muted-foreground">Explore the full report, or follow the recorded inventory evidence for this week.</p>
    <div className="grid gap-3 sm:grid-cols-3">
      {[
        { title: "All report fields", description: `${week.rows.length} weekly and daily values with source notes`, tab: sections[0], icon: BookOpen },
        { title: "Wastage reasons", description: `${week.wastage.lines} recorded lines · reasons and value coverage`, tab: "Wastage reasons", icon: ListFilter },
        { title: "Stock counts", description: `${week.stockCounts.length} stock-count observations · source units and costs`, tab: "Stock counts", icon: ClipboardList },
      ].map(item => <Button key={item.title} variant="outline" data-report-panel-trigger="details" className="h-auto justify-start gap-3 whitespace-normal p-4 text-left" onClick={() => { setNotesOpen(false); panel.open(item.tab); }}>
        <item.icon className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span className="min-w-0 flex-1"><span className="block text-sm font-medium">{item.title}</span><span className="mt-1 block text-xs font-normal text-muted-foreground">{item.description}</span></span>
        <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      </Button>)}
    </div>
    <Sheet open={panel.isOpen} onOpenChange={open => { if (!open) panel.close(); }}>
      {/* An open source-note popover owns Escape; keep the report panel behind it. */}
      <SheetContent onEscapeKeyDown={event => { if (notesOpen) event.preventDefault(); }} className="flex flex-col gap-0 overflow-hidden p-0 sm:max-w-6xl" onCloseAutoFocus={panel.restoreFocus}>
        <SheetHeader className="border-b p-6 pr-12 text-left"><SheetTitle>Report detail</SheetTitle><SheetDescription>{week.start} – {week.end} · {week.rows.length} fields. Missing values stay unavailable; all source qualifications are retained.</SheetDescription></SheetHeader>
        <Tabs value={selected} onValueChange={tab => { setNotesOpen(false); panel.select(tab); }} orientation="vertical" className="flex min-h-0 flex-1 flex-col md:flex-row">
          <div className="shrink-0 border-b bg-muted/30 p-3 md:w-56 md:border-b-0 md:border-r">
            <p className="mb-2 px-2 text-xs font-medium text-muted-foreground">BROWSE THIS WEEK</p>
            <TabsList aria-label="Report detail sections" className="grid h-auto w-full grid-cols-2 gap-1 bg-transparent p-0 md:flex md:flex-col md:items-stretch">
              {options.map(section => <TabsTrigger key={section} value={section} className="h-10 justify-between gap-2 px-3 text-left">
                <span>{section}</span><span className="text-xs tabular-nums text-muted-foreground">{sections.includes(section) ? week.rows.filter(row => row.section === section).length : section === "Wastage reasons" ? week.wastage.reasons.length : week.stockCounts.length}</span>
              </TabsTrigger>)}
            </TabsList>
          </div>
          <div className="min-h-0 min-w-0 flex-1 overflow-y-auto p-4 sm:p-6">
            {sections.map(section => <TabsContent key={section} value={section} className="m-0 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-lg font-semibold">{section}</h3><Badge variant={missing(section) ? "missing" : "neutral"}>{missing(section)} unavailable</Badge></div>
              <p className="text-xs text-muted-foreground">Weekly totals and Monday–Sunday detail. Use the information icons for basis and source notes. Purchases, theoretical estimates and actual COGS remain distinct measures.</p>
              <Table aria-label={`${section} report fields`}>
                <TableHeader><TableRow><TableHead scope="col">Field</TableHead><TableHead scope="col">Unit</TableHead><TableHead scope="col" className="text-right">Weekly value</TableHead><TableHead scope="col">Status & source</TableHead>{week.dates.map(date => <TableHead scope="col" className="text-right" key={date}>{formatDay(date)}</TableHead>)}</TableRow></TableHeader>
                <TableBody>{week.rows.filter(row => row.section === section).map(row => <TableRow key={row.id}>
                  <TableHead scope="row" className="font-medium text-foreground">{row.label}</TableHead><TableCell>{row.unit}</TableCell><TableCell className="text-right font-medium tabular-nums">{reportValue(row, row.total)}</TableCell>
                  <TableCell><SourceNotes row={row} week={week} onOpenChange={setNotesOpen} target={week.targets.some(target => target.id === row.id)} /></TableCell>
                  {row.days.map((value, i) => <TableCell key={i} className="text-right tabular-nums">{reportValue(row, value)}</TableCell>)}
                </TableRow>)}</TableBody>
              </Table>
            </TabsContent>)}
            <TabsContent value="Wastage reasons" className="m-0"><WastageReasons week={week} /></TabsContent>
            <TabsContent value="Stock counts" className="m-0"><StockCounts week={week} /></TabsContent>
          </div>
        </Tabs>
      </SheetContent>
    </Sheet>
  </section>;
}
