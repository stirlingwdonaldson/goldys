import type { WeeklyReport } from "./api/types";

type Cell = string | number | null | { percent: number };
const percent = (value: number | null): Cell => value === null ? null : { percent: value };
const encoder = new TextEncoder();

function xml(value: string): string {
  return value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g, "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&apos;");
}

function column(index: number): string {
  let name = "";
  for (let n = index + 1; n; n = Math.floor((n - 1) / 26)) name = String.fromCharCode(65 + (n - 1) % 26) + name;
  return name;
}

function worksheet(rows: Cell[][]): string {
  return `<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="2" width="27" customWidth="1"/><col min="3" max="11" width="18" customWidth="1"/><col min="12" max="13" width="65" customWidth="1"/></cols><sheetData>${rows.map((row, r) => `<row r="${r + 1}">${row.map((v, c) => {
    if (v === null) return "";
    const ref = `${column(c)}${r + 1}`;
    const amount = typeof v === "object" ? v.percent : v;
    if (typeof amount === "number" && Number.isFinite(amount)) return `<c r="${ref}" s="${typeof v === "object" ? 3 : 2}"><v>${amount}</v></c>`;
    return `<c r="${ref}" t="inlineStr" s="${r === 3 ? 1 : 0}"><is><t xml:space="preserve">${xml(String(v))}</t></is></c>`;
  }).join("")}</row>`).join("")}</sheetData></worksheet>`;
}

/** shortcut: OOXML supports numbers and literal text only; use a full writer when formulas/charts are required. */
function zip(files: [string, string][]): Uint8Array<ArrayBuffer> {
  const parts: Uint8Array[] = [];
  const directory: Uint8Array[] = [];
  let offset = 0;
  for (const [name, contents] of files) {
    const filename = encoder.encode(name), data = encoder.encode(contents);
    let crc = 0xffffffff;
    for (const byte of data) {
      crc ^= byte;
      for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
    crc = (crc ^ 0xffffffff) >>> 0;
    const header = new Uint8Array(30 + filename.length);
    const h = new DataView(header.buffer);
    h.setUint32(0, 0x04034b50, true); h.setUint16(4, 20, true); h.setUint16(6, 0x800, true);
    h.setUint16(12, 33, true); h.setUint32(14, crc, true);
    h.setUint32(18, data.length, true); h.setUint32(22, data.length, true); h.setUint16(26, filename.length, true);
    header.set(filename, 30);
    const entry = new Uint8Array(46 + filename.length);
    const e = new DataView(entry.buffer);
    e.setUint32(0, 0x02014b50, true); e.setUint16(4, 20, true); e.setUint16(6, 20, true); e.setUint16(8, 0x800, true);
    e.setUint16(14, 33, true); e.setUint32(16, crc, true); e.setUint32(20, data.length, true);
    e.setUint32(24, data.length, true); e.setUint16(28, filename.length, true); e.setUint32(42, offset, true);
    entry.set(filename, 46); directory.push(entry); parts.push(header, data); offset += header.length + data.length;
  }
  const size = directory.reduce((n, p) => n + p.length, 0);
  const end = new Uint8Array(22), e = new DataView(end.buffer);
  e.setUint32(0, 0x06054b50, true); e.setUint16(8, files.length, true); e.setUint16(10, files.length, true);
  e.setUint32(12, size, true); e.setUint32(16, offset, true);
  const result = new Uint8Array(offset + size + end.length);
  let position = 0;
  for (const part of [...parts, ...directory, end]) { result.set(part, position); position += part.length; }
  return result;
}

/** Exports exactly the loaded snapshot, including nulls and qualifications, without another data read. */
export function weeklyReportWorkbook(report: WeeklyReport, demo = false): Uint8Array<ArrayBuffer> {
  const title = `${demo ? "DEMO — " : ""}Goldy's weekly report`;
  const sheets: { name: string; rows: Cell[][] }[] = [{
    name: "Weekly summary",
    rows: [[title], [report.from, report.to, report.timezone], ["Generated", report.generatedAt, "Missing values are unavailable, not zero. All values remain source-qualified."],
      ["Week starting", ...report.weeks[0].rows.map(r => r.label)],
      ...report.weeks.map(w => [w.start, ...w.rows.map(r => r.unit === "PERCENT" ? percent(r.total) : r.total)])],
  }];
  for (const week of report.weeks) {
    sheets.push({ name: week.start, rows: [[title], [week.start, week.end, report.timezone], ["Generated", report.generatedAt],
      ["Section", "Field", "Unit", ...week.dates, "Weekly value", "Status", "Notes"],
      ...week.rows.map(r => [r.section, r.label, r.unit, ...r.days.map(v => r.unit === "PERCENT" ? percent(v) : v), r.unit === "PERCENT" ? percent(r.total) : r.total, r.status, r.notes.join("\n")])],
    });
  }
  sheets.push({ name: "Field notes", rows: [[title], [report.from, report.to], ["Review statuses apply to this snapshot."],
    ["Week", "Section", "Field", "Unit", "Status", "Notes"],
    ...report.weeks.flatMap(w => w.rows.map(r => [w.start, r.section, r.label, r.unit, r.status, r.notes.join("\n")]))] });
  sheets.push({ name: "Wastage reasons", rows: [[title], [report.from, report.to], ["All recorded reasons included; missing reasons are not inferred. Shares use known recorded value."],
    ["Week", "Reason", "Original labels", "Lines", "Valued lines", "Recorded value", "Share of known value", "Reason coverage by lines", "Reason coverage by value"],
    ...report.weeks.flatMap(w => w.wastage.reasons.length ? w.wastage.reasons.map(r => [w.start, r.reason, r.originalReasons.join(" | "), r.lines, r.valuedLines, r.value, percent(r.shareOfValue), percent(w.wastage.reasonCoverageLines), percent(w.wastage.reasonCoverageValue)]) : [[w.start, "No observations received", "", 0, 0, null, null, null, null]])] });
  sheets.push({ name: "Stock counts", rows: [[title], [report.from, report.to], ["Dated source observations, not stock balances or variances. Tax, valuation and completeness unverified."],
    ["Week", "Count date", "Stocktake", "Lines", "Valued lines", "Recorded value", "Partial", "Locked"],
    ...report.weeks.flatMap(w => w.stockCounts.map(c => [w.start, c.date, c.stocktakeId, c.lines, c.valuedLines, c.value, c.partial === null ? "Unknown" : String(c.partial), c.locked === null ? "Unknown" : String(c.locked)]))] });
  sheets.push({ name: "Targets", rows: [[title], [report.from, report.to], ["Effective-week settings carried forward until changed. Percentage values are fractions; 0.0025 means 0.25%."],
    ["Report week", "Field", "Value", "Effective from", "Source"],
    ...report.weeks.flatMap(w => w.targets.map(t => [w.start, t.id, ["food_target", "beverage_target", "sales_target"].includes(t.id) ? t.value : percent(t.value), t.effectiveFrom, t.source]))] });
  const ns = "http://schemas.openxmlformats.org/";
  const files: [string, string][] = [
    ["[Content_Types].xml", `<Types xmlns="${ns}package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join("")}</Types>`],
    ["_rels/.rels", `<Relationships xmlns="${ns}package/2006/relationships"><Relationship Id="rId1" Type="${ns}officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
    ["xl/workbook.xml", `<workbook xmlns="${ns}spreadsheetml/2006/main" xmlns:r="${ns}officeDocument/2006/relationships"><sheets>${sheets.map((s, i) => `<sheet name="${xml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join("")}</sheets></workbook>`],
    ["xl/_rels/workbook.xml.rels", `<Relationships xmlns="${ns}package/2006/relationships">${sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="${ns}officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join("")}<Relationship Id="styles" Type="${ns}officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`],
    ["xl/styles.xml", `<styleSheet xmlns="${ns}spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="4"><xf fontId="0" fillId="0" borderId="0" xfId="0"/><xf fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`],
    ...sheets.map((s, i): [string, string] => [`xl/worksheets/sheet${i + 1}.xml`, worksheet(s.rows)]),
  ];
  return zip(files);
}
