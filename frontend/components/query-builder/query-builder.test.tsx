import { describe, it, expect } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { QueryBuilder } from "./query-builder";

describe("QueryBuilder", () => {
  it("selects a dataset, picks a column, runs, and renders a result", async () => {
    render(
      <DemoModeProvider>
        <QueryBuilder />
      </DemoModeProvider>,
    );

    const datasetSelect = await screen.findByLabelText("Dataset");
    fireEvent.change(datasetSelect, { target: { value: "resolved_daily_sales" } });

    const fieldCheckbox = await screen.findByLabelText("Select trading_date");
    fireEvent.click(fieldCheckbox);

    fireEvent.click(screen.getByRole("button", { name: "Run query" }));

    await waitFor(() => expect(screen.getByText("Result")).toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getAllByText("trading_date").length).toBeGreaterThan(0),
    );
  });
});
