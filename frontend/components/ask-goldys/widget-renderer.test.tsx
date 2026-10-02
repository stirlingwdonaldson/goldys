import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { WidgetRenderer } from "./widget-renderer";
import type { WidgetSpec } from "./types";

describe("WidgetRenderer", () => {
  it("renders a line chart from data", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "line-chart",
      title: "Daily sales",
      description: null,
      data: [{ date: "2026-09-13", grossSales: 27650.66, source: "agreed" }],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByRole("img", { name: /daily sales/i })).toBeInTheDocument();
  });

  it("renders a table", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "table",
      title: "Sales",
      description: null,
      data: [{ date: "2026-09-13", grossSales: 27650.66 }],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText("date")).toBeInTheDocument();
    expect(screen.getByText("grossSales")).toBeInTheDocument();
  });

  it("renders a notice for an unknown type instead of crashing", () => {
    const widget: WidgetSpec = {
      version: 1,
      type: "pie" as WidgetSpec["type"],
      title: "Unknown",
      description: null,
      data: [],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText(/unsupported/i)).toBeInTheDocument();
  });
});
