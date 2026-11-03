import fields from "./weekly-report-fields.json";
import { weeklyTargetIds } from "../weekly-report-targets";
import type { WeeklyTarget, WeeklyWastage, WeeklyReport, WeeklyReportRow } from "./types";
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
    const targets = demoWeeklyTargets(dates[0]);
    applyDemoTargets(rows, targets);
    applyDemoWastage(rows);
    result.weeks.push({ start: dates[0], end: dates[6], dates, rows, targets,
      wastage: structuredClone(demoWastage),
      stockCounts: [{ date: dates[0], stocktakeId: 40+w, lines: 20, valuedLines: 19, value: 1500, partial: null, locked: null }],
    });
  }
  result.to = result.weeks.at(-1)!.end;
  return result;
}


function applyDemoTargets(rows: WeeklyReportRow[], targets: WeeklyTarget[]) {
  for (const target of targets) {
    const row = rows.find(r => r.id === target.id)!;
    row.total = target.value; row.status = "QUALIFIED";
    row.notes = [`DEMO target effective ${target.effectiveFrom}; ${target.source}. Carried forward until changed.`];
  }
  const actual = rows.find(r => r.id === "net_sales")!.total!;
  const target = targets.find(t => t.id === "sales_target")?.value;
  const difference = target === undefined ? null : actual-target;
  const totals = { sales_difference: difference, sales_difference_percent: target && difference !== null ? difference/target : null };
  for (const [id,total] of Object.entries(totals)) {
    const row = rows.find(r => r.id === id)!;
    row.total = total; row.status = total === null ? "UNAVAILABLE" : "QUALIFIED";
    row.notes = ["DEMO actual minus carried-forward target; actuals may be partial."];
  }
}

const demoWastage: WeeklyWastage = { lines: 4, valuedLines: 4, value: 100, reasonCoverageLines: 0.75, reasonCoverageValue: 0.6, reasons: [
  { reason: "No reason recorded", originalReasons: [], lines: 1, valuedLines: 1, value: 40, shareOfValue: 0.4 },
  { reason: "knock off", originalReasons: ["Knockoff", "Knock Off"], lines: 2, valuedLines: 2, value: 40, shareOfValue: 0.4 },
  { reason: "dropped/broken", originalReasons: ["Dropped/Broken"], lines: 1, valuedLines: 1, value: 20, shareOfValue: 0.2 },
] };
function applyDemoWastage(rows: WeeklyReportRow[]) {
  const wasteDays = [40, null, 10, null, 30, null, 20];
  const waste = rows.find(r => r.id === "total_wastage")!;
  waste.total = 100; waste.days = wasteDays; waste.status = "QUALIFIED";
  waste.notes = ["DEMO recorded wastage, including missing reasons. Missing dates are not zero."];
  const net = rows.find(r => r.id === "net_sales")!;
  const percentage = rows.find(r => r.id === "total_wastage_percent")!;
  percentage.total = 100/net.total!; percentage.status = "QUALIFIED";
  percentage.days = wasteDays.map((v,i) => v === null || !net.days[i] ? null : v/net.days[i]!);
  percentage.notes = ["DEMO recorded wastage / sales ex GST; cost tax basis unverified."];
  rows.push(...["lines", "value"].map(basis => ({ id: `wastage_reason_coverage_${basis}`, section: "Wastage", label: `Wastage with reasons (by ${basis === "value" ? "recorded value" : "lines"})`, unit: "PERCENT" as const, total: basis === "lines" ? 0.75 : 0.6, days: [0,null,1,null,1,null,1], status: "QUALIFIED" as const, notes: ["DEMO coverage among recorded observations only."] })));
}

const demoTargetValues: Record<string, number> = { food_target: 45600, beverage_target: 74400, sales_target: 120000, beverage_variance_target_min: -0.0025, beverage_variance_target_max: 0.0025, food_variance_target_min: -0.0025, food_variance_target_max: 0.0025, beverage_wastage_target_percent: 0.0025, food_wastage_target_percent: 0.005, total_wastage_target_percent: 0.003, food_cogs_target_percent: 0.25, beverage_cogs_target_percent: 0.22 };
const targetEdits: WeeklyTarget[] = [];
export function demoWeeklyTargets(week: string): WeeklyTarget[] {
  demoValidateWeek(week);
  const values = new Map(Object.entries(demoTargetValues).map(([id,value]) => [id,{ id,value,effectiveFrom: "2026-06-29",source: "DEMO workbook baseline" }]));
  for (const target of [...targetEdits].sort((a,b) => a.effectiveFrom.localeCompare(b.effectiveFrom))) if (target.effectiveFrom <= week) values.set(target.id, target);
  return [...values.values()].filter(t => t.effectiveFrom <= week);
}
function demoValidateWeek(week: string) {
  const date = new Date(`${week}T00:00:00Z`);
  if (!Number.isFinite(date.getTime()) || iso(date) !== week || date.getUTCDay() !== 1 || date.getUTCFullYear() < 2000 || date.getUTCFullYear() > 2100) throw new ApiError("VALIDATION_FAILED", "Select a Monday between 2000 and 2100.");
}
export function saveDemoWeeklyTargets(input: { effectiveFrom: string; values: Record<string,number> }): WeeklyTarget[] {
  demoValidateWeek(input.effectiveFrom);
  if (!Object.keys(input.values).length || Object.entries(input.values).some(([id,v]) => !weeklyTargetIds.includes(id) || !Number.isFinite(v) || Math.abs(v) > (id === "food_target" || id === "beverage_target" || id === "sales_target" ? 9999999999 : 1) || (v < 0 && !id.endsWith("_min")))) throw new ApiError("VALIDATION_FAILED", "Invalid target value.");
  const merged = Object.fromEntries(demoWeeklyTargets(input.effectiveFrom).map(t => [t.id,t.value]));
  Object.assign(merged,input.values);
  for (const prefix of ["food_variance_target", "beverage_variance_target"]) if (merged[`${prefix}_min`] > merged[`${prefix}_max`]) throw new ApiError("VALIDATION_FAILED", "Minimum target must not exceed maximum.");
  targetEdits.push(...Object.entries(input.values).map(([id,value]) => ({id,value,effectiveFrom: input.effectiveFrom,source: "Edited in DEMO weekly report"})));
  return demoWeeklyTargets(input.effectiveFrom);
}
