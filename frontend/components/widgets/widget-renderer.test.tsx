import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { WidgetRenderer } from "./widget-renderer";
import { TRUST_LABEL } from "@/components/trust/trust-indicator";
import type { TrustSummary } from "@/lib/api/types";
import type { RankedListWidget, StatWidget, TableWidget, WidgetSpec } from "./types";

describe("WidgetRenderer", () => {
  it("renders a stat widget", () => {
    const widget: StatWidget = {
      schemaVersion: 2,
      id: "w1",
      type: "stat",
      title: "Sales",
      value: 10865.72,
      format: "currency",
      hint: "Latest: 2026-10-05",
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText(/865\.72/)).toBeInTheDocument();
    expect(screen.getByText(/Latest/)).toBeInTheDocument();
  });

  it("renders a table widget", () => {
    const widget: TableWidget = {
      schemaVersion: 2,
      id: "w1",
      type: "table",
      title: "Sales",
      columns: [{ key: "date", label: "Date" }],
      rows: [{ date: "2026-09-13" }],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText("Date")).toBeInTheDocument();
    expect(screen.getByText("2026-09-13")).toBeInTheDocument();
  });

  it("renders a ranked list widget with a badge", () => {
    const widget: RankedListWidget = {
      schemaVersion: 2,
      id: "w1",
      type: "ranked-list",
      title: "Top sellers",
      items: [{ label: "garlic aioli", primary: "150", secondary: "$380.88", badge: "unresolved" }],
    };
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText(/garlic aioli/)).toBeInTheDocument();
    expect(screen.getByText("unresolved")).toBeInTheDocument();
  });

  it("renders a notice for an unknown type instead of crashing", () => {
    const widget = { schemaVersion: 2, id: "w1", type: "pie", title: "Unknown" } as unknown as WidgetSpec;
    render(<WidgetRenderer widget={widget} />);
    expect(screen.getByText(/unsupported/i)).toBeInTheDocument();
  });

  it("shows a trust badge when a metric is STALE", () => {
    const widget: StatWidget = {
      schemaVersion: 2,
      id: "w1",
      type: "stat",
      title: "Sales",
      value: 10865.72,
      format: "currency",
    };
    const trust: TrustSummary = {
      state: "SINGLE_SOURCE",
      freshness: "STALE",
      authoritativeSource: null,
      resolvedAt: null,
      lastIngestionAt: null,
      threshold: null,
    };
    render(<WidgetRenderer widget={widget} trust={trust} />);
    expect(screen.getByText(TRUST_LABEL.SINGLE_SOURCE)).toBeInTheDocument();
    expect(screen.getByText("stale")).toBeInTheDocument();
  });
});
