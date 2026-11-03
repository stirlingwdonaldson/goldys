import fields from "./weekly-report-fields.json";
import type { WeeklyReport, WeeklyReportRow } from "./types";
import { ApiError } from "./errors";

function iso(date: Date): string { return date.toISOString().slice(0, 10); }

export function demoWeeklyReport(from?: string, weeks = 4): WeeklyReport {
  const now = new Date();
  const parts = new Intl.DateTimeFormat("en-AU", { timeZone: "Australia/Melbourne", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const part = (type: string) => parts.find(p => p.type === type)?.value;
  const start = new Date(from ? `${from}T00:00:00Z` : `${part("year")}-${part("month")}-${part("day")}T00:00:00Z`);
  if (!from) start.setUTCDate(start.getUTCDate() - (start.getUTCDay() + 6) % 7 - weeks * 7);
  if (!Number.isFinite(start.getTime()) || (from && iso(start) !== from) || start.getUTCFullYear() < 2000 || start.getUTCFullYear() > 2100 || start.getUTCDay() !== 1 || weeks < 1 || weeks > 13 || !Number.isInteger(weeks)) {
    throw new ApiError("VALIDATION_FAILED", "Select a Monday and between 1 and 13 weeks.");
  }
  const result: WeeklyReport = { from: iso(start), to: "", generatedAt: now.toISOString(), timezone: "Australia/Melbourne", weeks: [] };
  for (let w = 0; w < weeks; w++) {
    const dates = Array.from({ length: 7 }, (_, day) => iso(new Date(start.getTime() + (w * 7 + day) * 86400000)));
    const rows = fields.map(field => {
      const row: WeeklyReportRow = { ...field, unit: field.unit as WeeklyReportRow["unit"], status: "UNAVAILABLE", days: Array(7).fill(null), total: null, notes: [...field.notes] };
      const amounts: Record<string, number[]> = {
        net_sales: [6000, 7000, 9000, 10000, 20000, 30000, 15000],
        gst: [600, 700, 900, 1000, 2000, 3000, 1500],
        gross_sales: [6600, 7700, 9900, 11000, 22000, 33000, 16500],
        payments: [6550, 7750, 9900, 11000, 22000, 33000, 16500],
        purchases: [300, 400, 200, 1000, 600, 500, 200],
      };
      if (amounts[row.id]) {
        row.days = amounts[row.id].map((v, day) => w === 0 && day === 1 ? null : v);
        row.total = row.days.reduce<number>((sum, v) => sum + (v ?? 0), 0);
        row.status = "QUALIFIED";
        row.notes.push("DEMO data. Known subtotal; source completeness unverified.");
        if (w === 0) row.notes.push(`Unavailable date: ${dates[1]}. Missing days are not zero.`);
      }
      if (row.id === "review_average") { row.total = 4.5; row.status = "QUALIFIED"; }
      return row;
    });
    result.weeks.push({ start: dates[0], end: dates[6], dates, rows });
  }
  result.to = result.weeks.at(-1)!.end;
  return result;
}
