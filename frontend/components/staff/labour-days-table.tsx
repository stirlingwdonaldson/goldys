import type { LabourSummary } from "@/lib/api";
import { Section } from "@/components/layout/section";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatCurrency, formatHours } from "@/lib/format";

export function LabourDaysTable({ summary, showWages }: { summary: LabourSummary | null; showWages: boolean }) {
  if (!summary?.days?.length) return null;
  return (
    <Section title="Daily team figures">
      <p className="mb-3 max-w-prose text-sm text-muted-foreground">Recorded rosters and timesheets for the last 30 days and next 14 days. A dash means unavailable, including shifts still open. Teams use their Tanda names.</p>
      <Table>
        <TableHeader><TableRow><TableHead>Date</TableHead><TableHead>Team</TableHead><TableHead className="text-right">Rostered hours</TableHead><TableHead className="text-right">Worked hours</TableHead>{showWages && <TableHead className="text-right">Rostered wages</TableHead>}{showWages && <TableHead className="text-right">Recorded wages</TableHead>}</TableRow></TableHeader>
        <TableBody>{summary.days.map((day) => (
          <TableRow key={`${day.date}-${day.department}`}>
            <TableCell>{day.date}{day.date > summary.to ? " · Upcoming" : ""}</TableCell>
            <TableCell>{day.department}</TableCell>
            <TableCell className="text-right tabular-nums">{formatHours(day.scheduledHours)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatHours(day.actualHours)}</TableCell>
            {showWages && <TableCell className="text-right tabular-nums">{formatCurrency(day.scheduledCost)}</TableCell>}{showWages && <TableCell className="text-right tabular-nums">{formatCurrency(day.actualCost)}</TableCell>}
          </TableRow>
        ))}</TableBody>
      </Table>
    </Section>
  );
}
