import { describe, expect, it } from "vitest";
import { weeklyReportWorkbook } from "./weekly-report-export";
import { demoWeeklyReport } from "./api/weekly-report-demo";

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
    expect(cell(target, "J")?.getAttribute("s")).toBe("11");
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
    expect(totalStyle.getAttribute("numFmtId")).toBe("10");
    expect(totalStyle.getAttribute("fillId")).toBe("5");
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

  it("stacks at most four weeks per comparison table and retains every total, keyed by field", () => {
    const report = demoWeeklyReport("2026-08-03", 13);
    report.weeks[1].rows.reverse();
    const files = entries(weeklyReportWorkbook(report));
    expect((files.get("xl/workbook.xml")!.match(/<sheet /g) ?? []).length).toBe(18);
    const summary = new DOMParser().parseFromString(files.get("xl/worksheets/sheet1.xml")!, "application/xml");
    expect(summary.querySelectorAll("col")).toHaveLength(6);
    for (const metric of report.weeks[0].rows) {
      const rows = [...summary.querySelectorAll("row")].filter(r => r.querySelector("c t")?.textContent === metric.label);
      expect(rows).toHaveLength(4);
      report.weeks.forEach((week, i) => {
        const actual = cell(rows[Math.floor(i / 4)], ["C", "D", "E", "F"][i % 4])?.querySelector("v")?.textContent;
        const expected = week.rows.find(r => r.id === metric.id)!.total;
        expect(actual).toBe(expected === null ? undefined : String(expected));
      });
    }
  });
});
