import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { parseWidgetSpec } from "./parse";
import { WidgetRenderer } from "./widget-renderer";
import type { ChartWidget } from "./types";

const kinds = ["LINE", "AREA", "BAR", "PIE", "DONUT", "RADAR", "RADIAL"] as const;
const fixture: ChartWidget = { schemaVersion: 2, type: "chart", id: "sales", title: "Sales pattern", kind: "LINE", yFormat: "currency", stacked: false, horizontal: false,
  series: [{ key: "sales", label: "Gross sales · 2–4 Nov", points: [{ x: "Mon", y: 100 }, { x: "Tue", y: 0 }, { x: "Wed", y: 200 }] }],
};

describe("supporting chart contracts", () => {
  it.each(kinds)("renders a validated %s with an accessible legend and exact data", kind => {
    const widget = parseWidgetSpec({ ...fixture, kind });
    expect(widget).not.toBeNull();
    render(<WidgetRenderer widget={widget!} />);
    expect(screen.getByRole("img", { name: `Sales pattern — ${kind.toLowerCase()} chart` })).toBeVisible();
    expect(screen.getByRole("list", { name: "Chart legend" })).toBeVisible();
    const table = screen.getByRole("table", { hidden: true });
    expect(table).toHaveTextContent("$100.00");
    expect(table).toHaveTextContent("$0.00");
    expect(table).toHaveTextContent("$200.00");
  });
  it("retains null gaps separately from zero in unstacked charts", () => {
    const widget = parseWidgetSpec({ ...fixture, series: [{ ...fixture.series[0], points: [{ x: "Mon", y: null }, { x: "Tue", y: 0 }] }] });
    expect(widget).not.toBeNull();
    render(<WidgetRenderer widget={widget!} />);
    const table = screen.getByRole("table", { hidden: true });
    expect(within(table).getByText("Unavailable")).toBeInTheDocument();
    expect(within(table).getByText("$0.00")).toBeInTheDocument();
  });
  it.each(["PIE", "DONUT", "RADAR", "RADIAL"])("rejects incomplete or negative %s", kind => {
    for (const y of [null, -1]) expect(parseWidgetSpec({ ...fixture, kind, series: [{ ...fixture.series[0], points: [{ x: "Mon", y }, { x: "Tue", y: 2 }, { x: "Wed", y: 3 }] }] })).toBeNull();
  });
  it("rejects unknown families, duplicate keys/categories, nonfinite values and incompatible options", () => {
    for (const overrides of [
      { kind: "HTML" }, { horizontal: true }, { stacked: true }, { series: [] },
      { series: [...fixture.series, ...fixture.series] },
      { series: [{ ...fixture.series[0], points: [{ x: "Mon", y: 1 }, { x: "Mon", y: 2 }] }] },
      { series: [{ ...fixture.series[0], points: [{ x: "Mon", y: Infinity }, { x: "Tue", y: 2 }] }] },
      { series: [{ ...fixture.series[0], points: [{ x: "Mon", y: null }, { x: "Tue", y: null }] }] },
    ]) expect(parseWidgetSpec({ ...fixture, ...overrides })).toBeNull();
  });
  it("accepts stacked areas and horizontal bars without changing the data", () => {
    expect(parseWidgetSpec({ ...fixture, kind: "AREA", stacked: true })).toMatchObject({ series: fixture.series });
    expect(parseWidgetSpec({ ...fixture, kind: "BAR", horizontal: true })).toMatchObject({ series: fixture.series });
  });
});
