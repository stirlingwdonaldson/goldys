import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it } from "vitest";
import { ChartDataTable } from "./chart-data-table";
import type { TimeSeriesWidget } from "./types";

const widget: TimeSeriesWidget = {
  schemaVersion: 2, id: "chart", type: "time-series", title: "Resolved sales", yFormat: "currency",
  series: [
    { key: "net", label: "Net sales", points: [{ x: "2026-10-01", y: 0 }, { x: "2026-10-02", y: null }] },
    { key: "prior", label: "Prior sales", points: [{ x: "2026-10-01", y: 12.5 }] },
  ],
};

it("exposes full date labels, both series, real zero and unavailable gaps", () => {
  render(<ChartDataTable widget={widget} />);
  fireEvent.click(screen.getByText("View chart data"));
  const table = screen.getByRole("table", { name: "Resolved sales — data" });
  expect(within(table).getByText("2026-10-01")).toBeInTheDocument();
  expect(within(table).getByText("2026-10-02")).toBeInTheDocument();
  expect(within(table).getByText("Net sales")).toBeInTheDocument();
  expect(within(table).getByText("Prior sales")).toBeInTheDocument();
  expect(within(table).getByText("$0.00")).toBeInTheDocument();
  expect(within(table).getByText("$12.50")).toBeInTheDocument();
  expect(within(table).getAllByText("Unavailable")).toHaveLength(2);
});
