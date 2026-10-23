import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DataQualifications } from "./data-qualifications";

describe("DataQualifications", () => {
  it("makes missing-input subtotal qualification prominent", () => {
    render(<DataQualifications notices={["2 day(s) unresolved", "Ratio unavailable: missing inputs have no validated bound"]} />);
    expect(screen.getByRole("status")).toHaveTextContent("Incomplete — known subtotals only");
    expect(screen.getByText(/Ratio unavailable/)).toBeInTheDocument();
  });
  it("does not mark a known qualification as missing data", () => {
    render(<DataQualifications notices={["Wages only"]} />);
    expect(screen.queryByText(/Incomplete/)).not.toBeInTheDocument();
  });
});
