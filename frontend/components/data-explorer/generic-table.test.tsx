import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { GenericTable } from "./generic-table";

describe("GenericTable", () => {
  it("unions columns across rows so a nullable column absent from the first row is still shown", () => {
    render(
      <GenericTable
        rows={[
          { id: "1", columns: { source_system: "CTB", trading_date: "2026-10-05" } },
          {
            id: "2",
            columns: { source_system: "CTB", trading_date: "2026-10-06", supplier_name: "Acme" },
          },
        ]}
      />,
    );

    expect(screen.getByText("supplier_name")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
  });
});
