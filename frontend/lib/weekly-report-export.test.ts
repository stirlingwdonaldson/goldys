import { describe, expect, it } from "vitest";
import { weeklyReportWorkbook } from "./weekly-report-export";
import { demoWeeklyReport } from "./api/weekly-report-demo";
import { createElement } from "react";
import { render, screen, within } from "@testing-library/react";
import { TargetComparison } from "@/app/(app)/weekly-report/report-sections";
import type { WeeklyReportAssessment } from "./api/types";

function entries(bytes: Uint8Array): Map<string, string> {
  const files = new Map<string, string>();
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const decoder = new TextDecoder();
  let offset = 0;
  while (view.getUint32(offset, true) === 0x04034b50) {
    const size = view.getUint32(offset + 18, true);
    const length = view.getUint16(offset + 26, true);
    const extra = view.getUint16(offset + 28, true);
    const name = decoder.decode(bytes.slice(offset + 30, offset + 30 + length));
    const start = offset + 30 + length + extra;
    files.set(name, decoder.decode(bytes.slice(start, start + size)));
    offset = start + size;
  }
  return files;
}

function fieldRow(sheet: Document, label: string): Element {
  const row = [...sheet.querySelectorAll("row")].find(r => r.querySelector("c t")?.textContent === label);
  expect(row, `row for ${label}`).toBeDefined();
  return row!;
}

function cell(row: Element, col: string): Element | null {
  return row.querySelector(`c[r="${col}${row.getAttribute("r")}"]`);
}

describe("weekly Excel snapshot", () => {
  it("includes every week and field with numeric totals and missing cells, without changing the report", () => {
    const report = demoWeeklyReport("2026-10-19", 2);
    const snapshot = JSON.stringify(report);
    const files = entries(weeklyReportWorkbook(report));
    const workbook = files.get("xl/workbook.xml")!;
    expect(workbook).toContain('name="Weekly summary"');
    expect(workbook).toContain('name="2026-10-19"');
    expect(workbook).toContain('name="2026-10-26"');
    expect(workbook).toContain('name="Field notes"');
    expect(workbook).toContain('name="Wastage reasons"');
    expect(workbook).toContain('name="Stock counts"');
    expect(workbook).toContain('name="Targets"');
    expect(files.get("xl/worksheets/sheet5.xml")).toContain("No reason recorded");
    expect(files.get("xl/worksheets/sheet5.xml")).toContain("Knockoff | Knock Off");
    expect(files.get("xl/worksheets/sheet6.xml")).toContain("Unknown");
    expect(files.get("xl/worksheets/sheet7.xml")).toContain("DEMO workbook baseline");
    const sheet = new DOMParser().parseFromString(files.get("xl/worksheets/sheet2.xml")!, "application/xml");
    expect(sheet.querySelector("parsererror")).toBeNull();
    for (const row of report.weeks[0].rows) {
      expect(sheet.documentElement.textContent).toContain(row.label);
      expect(sheet.documentElement.textContent).toContain(row.status);
    }
    const sales = fieldRow(sheet, report.weeks[0].rows.find(r => r.id === "net_sales")!.label);
    expect(cell(sales, "J")?.querySelector("v")?.textContent).toBe("90000");
    expect(cell(sales, "D")?.querySelector("v")).toBeNull();
    expect(cell(sales, "C")?.querySelector("v")?.textContent).toBe("6000");
    const target = fieldRow(sheet, report.weeks[0].rows.find(r => r.id === "beverage_wastage_target_percent")!.label);
    expect(cell(target, "J")?.querySelector("v")?.textContent).toBe("0.0025");
    expect(JSON.stringify(report)).toBe(snapshot);
    expect(files.get("xl/worksheets/sheet4.xml")).toContain("Missing days are not zero");
  });

  it("formats sectioned reports and filterable detail tabs with readable numeric styles", () => {
    const report = demoWeeklyReport("2026-10-26", 1);
    const files = entries(weeklyReportWorkbook(report));
    const styles = new DOMParser().parseFromString(files.get("xl/styles.xml")!, "application/xml");
    const formats = styles.querySelectorAll("cellXfs > xf");
    for (const [name, contents] of files) {
      if (!name.startsWith("xl/worksheets/")) continue;
      const sheet = new DOMParser().parseFromString(contents, "application/xml");
      expect(sheet.querySelector("sheetView")?.getAttribute("showGridLines")).toBe("0");
      expect(sheet.querySelector("pane")?.getAttribute("ySplit")).toBe(name.endsWith("sheet1.xml") ? "3" : "4");
      expect(sheet.querySelector("mergeCell")?.getAttribute("ref")).toMatch(/^A1:[A-Z]+1$/);
      if (sheet.querySelector('c[s="12"]')) expect(sheet.querySelector("autoFilter")).toBeNull();
      else expect(sheet.querySelector("autoFilter")?.getAttribute("ref")).toMatch(/^A4:[A-Z]+\d+$/);
      for (const cell of sheet.querySelectorAll("c")) {
        expect(formats[Number(cell.getAttribute("s"))]).toBeDefined();
      }
    }
    const sheet = new DOMParser().parseFromString(files.get("xl/worksheets/sheet2.xml")!, "application/xml");
    expect(sheet.querySelector("pane")?.getAttribute("topLeftCell")).toBe("C5");
    for (const section of new Set(report.weeks[0].rows.map(r => r.section))) {
      const heading = fieldRow(sheet, section);
      const index = Number(heading.getAttribute("r"));
      expect(sheet.querySelector(`mergeCell[ref="A${index}:K${index}"]`)).not.toBeNull();
      expect(sheet.querySelector(`row[r="${index + 1}"]`)?.textContent).toContain("Weekly value");
    }
    const sales = fieldRow(sheet, report.weeks[0].rows[0].label);
    const nextSales = fieldRow(sheet, report.weeks[0].rows[1].label);
    expect(cell(sales, "A")?.getAttribute("s")).not.toBe(cell(nextSales, "A")?.getAttribute("s"));
    const target = fieldRow(sheet, report.weeks[0].rows.find(r => r.id === "beverage_wastage_target_percent")!.label);
    const totalStyle = formats[Number(cell(target, "J")?.getAttribute("s"))];
    expect(totalStyle.getAttribute("numFmtId")).toBe("165");
    expect(totalStyle.getAttribute("fillId")).toBe("8");
    expect(totalStyle.querySelector("alignment")?.getAttribute("wrapText")).toBe("1");
    expect(sheet.querySelectorAll("col")).toHaveLength(11);
  });

  it("keeps formula-looking source text literal, escapes XML and labels demo exports", () => {
    const report = demoWeeklyReport("2026-10-26", 1);
    report.weeks[0].rows[0].label = '=HYPERLINK("https://example.invalid","x")';
    report.weeks[0].rows[0].notes = ["a < b & c\u0001"];
    const files = entries(weeklyReportWorkbook(report, true));
    for (const contents of files.values()) {
      const xml = new DOMParser().parseFromString(contents, "application/xml");
      expect(xml.querySelector("parsererror")).toBeNull();
      expect(xml.querySelector("f")).toBeNull();
    }
    const contents = files.get("xl/worksheets/sheet2.xml")!;
    expect(contents).toContain("DEMO —");
    expect(contents).toContain("=HYPERLINK(&quot;");
    expect(files.get("xl/worksheets/sheet3.xml")).toContain("a &lt; b &amp; c");
    expect(contents).not.toContain("\u0001");
  });

  it("keeps a compact scorecard for every week and all fields and values on dated tabs", () => {
    const report = demoWeeklyReport("2026-08-03", 13);
    report.weeks[1].rows.reverse();
    const files = entries(weeklyReportWorkbook(report));
    expect((files.get("xl/workbook.xml")!.match(/<sheet /g) ?? []).length).toBe(18);
    const summary = new DOMParser().parseFromString(files.get("xl/worksheets/sheet1.xml")!, "application/xml");
    expect(summary.querySelectorAll("col")).toHaveLength(5);
    const sales = [...summary.querySelectorAll("row")].filter(r => r.querySelector("c t")?.textContent === "Total sales ex GST");
    expect(sales).toHaveLength(13);
    report.weeks.forEach((week, i) => {
      expect(cell(sales[i], "B")?.querySelector("v")?.textContent).toBe(String(week.rows.find(r => r.id === "net_sales")!.total));
      const sheet = new DOMParser().parseFromString(files.get(`xl/worksheets/sheet${i + 2}.xml`)!, "application/xml");
      for (const metric of week.rows) {
        const row = fieldRow(sheet, metric.label);
        const total = cell(row, "J")?.querySelector("v")?.textContent;
        expect(total).toBe(metric.total === null ? undefined : String(metric.total));
        metric.days.forEach((value, day) => expect(cell(row, ["C", "D", "E", "F", "G", "H", "I"][day])?.querySelector("v")?.textContent).toBe(value === null ? undefined : String(value)));
      }
    });
  });

  it("styles server verdicts, planning targets and unavailable results without reassessing values", () => {
    const report = demoWeeklyReport("2026-10-19", 2);
    const first = report.weeks[0];
    const set = (week: typeof first, id: string, total: number | null, status = "QUALIFIED") => Object.assign(week.rows.find(r => r.id === id)!, { total, status });
    set(first, "food_sales", 45600); // Exactly on its sales target.
    set(first, "food_wastage_percent", 0.005); // Exactly on its maximum wastage target.
    set(first, "beverage_wastage_percent", 0.01);
    set(first, "beverage_variance_percent", -0.003);
    set(first, "sales_reconciliation_difference", -100); // Has no approved performance threshold.
    set(first, "food_cogs_percent", 0.1, "UNAVAILABLE");
    set(report.weeks[1], "beverage_variance_percent", -0.0025); // Lower bound is inclusive.
    set(report.weeks[1], "food_target", null);
    Object.assign(first.assessments, {
      food_sales: { outcome: "MEETS_TARGET", reason: null },
      beverage_sales: { outcome: "BELOW_TARGET", reason: null },
      food_wastage_percent: { outcome: "MEETS_TARGET", reason: null },
      beverage_wastage_percent: { outcome: "ABOVE_LIMIT", reason: null },
      beverage_variance_percent: { outcome: "OUTSIDE_RANGE", reason: null },
    });
    report.weeks[1].assessments.beverage_variance_percent = { outcome: "MEETS_TARGET", reason: null };
    report.weeks[1].assessments.food_sales = { outcome: "WITHHELD", reason: "NO_EFFECTIVE_TARGET" };
    report.weeks[1].assessments.food_target = { outcome: "UNAVAILABLE", reason: null };
    const files = entries(weeklyReportWorkbook(report));
    const styles = new DOMParser().parseFromString(files.get("xl/styles.xml")!, "application/xml");
    const formats = styles.querySelectorAll("cellXfs > xf");
    const fills = styles.querySelectorAll("fills > fill");
    const fill = (sheet: Document, label: string, col = "J") => {
      const style = Number(cell(fieldRow(sheet, label), col)?.getAttribute("s"));
      return fills[Number(formats[style].getAttribute("fillId"))].querySelector("fgColor")?.getAttribute("rgb");
    };
    const week = new DOMParser().parseFromString(files.get("xl/worksheets/sheet2.xml")!, "application/xml");
    const color = (id: string) => fill(week, first.rows.find(r => r.id === id)!.label);
    expect(color("food_sales")).toBe("FFE3F3E8");
    expect(color("beverage_sales")).toBe("FFFCE8E6");
    expect(color("food_wastage_percent")).toBe("FFE3F3E8");
    expect(color("beverage_wastage_percent")).toBe("FFFCE8E6");
    expect(color("beverage_variance_percent")).toBe("FFFCE8E6");
    expect(color("food_target")).toBe("FFEAF1F8");
    expect(color("food_cogs_percent")).toBe("FFF0F1F3");
    expect(color("sales_reconciliation_difference")).toBe("FFEAF1F8");
    const next = new DOMParser().parseFromString(files.get("xl/worksheets/sheet3.xml")!, "application/xml");
    expect(fill(next, first.rows.find(r => r.id === "beverage_variance_percent")!.label)).toBe("FFE3F3E8");
    const summary = new DOMParser().parseFromString(files.get("xl/worksheets/sheet1.xml")!, "application/xml");
    const food = [...summary.querySelectorAll("row")].filter(r => r.querySelector("c t")?.textContent === "Food sales");
    expect(cell(food[1], "E")?.textContent).toBe("No effective target");
    expect(fill(summary, "Review rating", "E")).toBe("FFFFF3D6");
    expect(fill(summary, "Food COGS rate", "E")).toBe("FFF0F1F3");
    expect(summary.documentElement.textContent).toContain("Colours do not certify completeness");
    expect(styles.querySelectorAll('font name[val="Arial"]')).toHaveLength(styles.querySelectorAll("font").length);
    const countRow = fieldRow(week, first.rows.find(r => r.id === "sales_missing_tax_rows")!.label);
    const countFormat = formats[Number(cell(countRow, "J")?.getAttribute("s"))].getAttribute("numFmtId");
    expect(countFormat).toBe("166");
  });

  it.each([
    [{ outcome: "WITHHELD", reason: "INCOMPLETE_EVIDENCE" }, "Incomplete comparison evidence", "FFFFF3D6"],
    [{ outcome: "WITHHELD", reason: "NO_EFFECTIVE_TARGET" }, "No effective target", "FFFFF3D6"],
    [{ outcome: "WITHHELD", reason: "BASIS_MISMATCH" }, "Basis differs", "FFFFF3D6"],
    [{ outcome: "MEETS_TARGET", reason: null }, "Meets target", "FFE3F3E8"],
    [{ outcome: "BELOW_TARGET", reason: null }, "Below target", "FFFCE8E6"],
    [undefined, "Assessment unavailable", "FFFFF3D6"],
  ] as [WeeklyReportAssessment | undefined, string, string][])("screen and workbook express the same snapshot decision: %s", (assessment, label, color) => {
    const report = demoWeeklyReport("2026-10-19", 1);
    const week = report.weeks[0];
    const actual = week.rows.find(r => r.id === "net_sales")!;
    const difference = week.rows.find(r => r.id === "sales_difference")!;
    // Keep the same positive figures for every verdict: neither renderer may infer success.
    actual.total = 130000;
    difference.total = 10000;
    if (assessment) {
      week.assessments.net_sales = assessment;
      week.assessments.sales_difference = assessment;
    } else {
      delete week.assessments.net_sales;
      delete week.assessments.sales_difference;
    }
    render(createElement(TargetComparison, { week }));
    const ui = within(screen.getByRole("rowheader", { name: "Total sales ex GST (AUD)" }).closest("tr")!);
    expect(ui.getByText(label)).toBeVisible();
    expect(ui.getByText("$10,000.00")).toBeVisible();
    const files = entries(weeklyReportWorkbook(report));
    const summary = new DOMParser().parseFromString(files.get("xl/worksheets/sheet1.xml")!, "application/xml");
    const sales = fieldRow(summary, "Total sales ex GST");
    expect(cell(sales, "E")?.textContent).toBe(label);
    expect(cell(sales, "B")?.querySelector("v")?.textContent).toBe("130000");
    expect(cell(sales, "D")?.querySelector("v")?.textContent).toBe("10000");
    const styles = new DOMParser().parseFromString(files.get("xl/styles.xml")!, "application/xml");
    const format = styles.querySelectorAll("cellXfs > xf")[Number(cell(sales, "E")?.getAttribute("s"))];
    const fill = styles.querySelectorAll("fills > fill")[Number(format.getAttribute("fillId"))];
    expect(fill.querySelector("fgColor")?.getAttribute("rgb")).toBe(color);
  });
});
