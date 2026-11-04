import { Info } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { WeeklyReportWeek } from "@/lib/api/types";
import { formatCurrency } from "@/lib/format";
import { reportPercent as formatPercent } from "@/lib/weekly-report-targets";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";

export function WastageReasons({ week }: { week: WeeklyReportWeek }) {
  const waste = week.wastage;
  return <section aria-label="Wastage reasons" className="space-y-3">
      <h3 className="font-semibold">Wastage reasons</h3>
      <p className="text-sm text-muted-foreground">All recorded reasons are included in {formatCurrency(waste.value)} of known wastage. {waste.valuedLines} of {waste.lines} lines have values. Reasons recorded for {formatPercent(waste.reasonCoverageLines)} of lines and {formatPercent(waste.reasonCoverageValue)} of known value. These percentages describe received records, not capture completeness.</p>
      {waste.lines === 0 ? <p className="text-sm">No wastage observations received for this week. This does not establish zero wastage.</p> : <Table className="tabular-nums">
        <TableHeader><TableRow><TableHead>Reason</TableHead><TableHead>Lines</TableHead><TableHead>Valued lines</TableHead><TableHead>Recorded value</TableHead><TableHead>Share of known value</TableHead></TableRow></TableHeader>
        <TableBody>{waste.reasons.map(reason => <TableRow key={reason.reason}>
          <TableCell>{reason.reason}<TooltipProvider delayDuration={200}><Tooltip><TooltipTrigger asChild><Button variant="ghost" size="icon" className="ml-1 h-8 w-8 rounded-full text-muted-foreground" aria-label={`Original labels for ${reason.reason}`}><Info className="h-4 w-4" aria-hidden="true" /></Button></TooltipTrigger><TooltipContent className="max-w-xs whitespace-normal">{reason.originalReasons.join(" · ") || "Blank or missing"}</TooltipContent></Tooltip></TooltipProvider></TableCell>
          <TableCell>{reason.lines}</TableCell><TableCell>{reason.valuedLines}</TableCell><TableCell>{formatCurrency(reason.value)}</TableCell><TableCell>{formatPercent(reason.shareOfValue)}</TableCell>
        </TableRow>)}</TableBody>
      </Table>}
    </section>;
}

export function StockCounts({ week }: { week: WeeklyReportWeek }) {
  return <section aria-label="Stock counts" className="space-y-3">
      <h3 className="font-semibold">Stock counts</h3>
      <p className="text-sm text-muted-foreground">Dated CTB observations. Values use source units and costs; tax, valuation and count completeness are unverified. Counts are not weekly stock variances, and separate stocktakes are not summed into a balance.</p>
      {week.stockCounts.length === 0 ? <p className="text-sm">No stock-count observations received for this week.</p> : <Table className="tabular-nums">
        <TableHeader><TableRow><TableHead>Count date</TableHead><TableHead>Stocktake</TableHead><TableHead>Lines</TableHead><TableHead>Valued lines</TableHead><TableHead>Recorded value</TableHead><TableHead>Partial</TableHead><TableHead>Locked</TableHead></TableRow></TableHeader>
        <TableBody>{week.stockCounts.map(count => <TableRow key={`${count.date}:${count.stocktakeId}`}>
          <TableCell>{count.date}</TableCell><TableCell>{count.stocktakeId}</TableCell><TableCell>{count.lines}</TableCell><TableCell>{count.valuedLines}</TableCell><TableCell>{formatCurrency(count.value)}</TableCell><TableCell>{count.partial === null ? "Unknown" : count.partial ? "Yes" : "No"}</TableCell><TableCell>{count.locked === null ? "Unknown" : count.locked ? "Yes" : "No"}</TableCell>
        </TableRow>)}</TableBody>
      </Table>}
    </section>;
}
