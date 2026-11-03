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
    const sheet = new DOMParser().parseFromString(files.get("xl/worksheets/sheet2.xml")!, "application/xml");
    expect(sheet.querySelector("parsererror")).toBeNull();
    for (const row of report.weeks[0].rows) {
      expect(sheet.documentElement.textContent).toContain(row.label);
      expect(sheet.documentElement.textContent).toContain(row.status);
    }
    const index = report.weeks[0].rows.findIndex(r => r.id === "net_sales") + 5;
    expect(sheet.querySelector(`c[r="K${index}"] v`)?.textContent).toBe("90000");
    expect(sheet.querySelector(`c[r="E${index}"]`)).toBeNull();
    expect(sheet.querySelector(`c[r="D${index}"] v`)?.textContent).toBe("6000");
    expect(JSON.stringify(report)).toBe(snapshot);
    expect(files.get("xl/worksheets/sheet4.xml")).toContain("Missing days are not zero");
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
    expect(contents).toContain("a &lt; b &amp; c");
    expect(contents).not.toContain("\u0001");
  });

  it("handles the largest bounded report without losing week tabs", () => {
    const files = entries(weeklyReportWorkbook(demoWeeklyReport("2026-08-03", 13)));
    expect((files.get("xl/workbook.xml")!.match(/<sheet /g) ?? []).length).toBe(15);
  });
});
