import { render, screen, within, fireEvent } from "@testing-library/react";
import { it, expect } from "vitest";
import { FeatureImportanceChart } from "./feature-importance-chart";
it("exposes exact known gains, including zero, through a named table", () => {
  render(<FeatureImportanceChart rows={[{ feature: "weekday", importance: 0.123456789 }, { feature: "holiday", importance: 0 }, { feature: "unknown", importance: null }]} />);
  fireEvent.click(screen.getByText("Feature importance values"));
  const table = screen.getByRole("table", { name: "Feature importance gains" });
  expect(within(table).getByText("0.123456789")).toBeInTheDocument();
  expect(within(table).getByText("0")).toBeInTheDocument();
  expect(within(table).queryByText("unknown")).not.toBeInTheDocument();
});
