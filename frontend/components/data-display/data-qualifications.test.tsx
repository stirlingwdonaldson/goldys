import { render, screen, within } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { DataQualifications } from "./data-qualifications";

afterEach(() => localStorage.clear());

it("keeps every qualification visible even with the old hide preference", () => {
  localStorage.setItem("goldys-show-incomplete-notices", "false");
  render(<DataQualifications notices={["2 days unresolved", "Ratio unavailable: missing inputs", "Wages only", "Wages only"]} />);
  const status = screen.getByRole("status");
  expect(status).toHaveTextContent("2 days unresolved");
  expect(status).toHaveTextContent("Ratio unavailable: missing inputs");
  expect(status).toHaveTextContent("Wages only");
  expect(within(status).getAllByRole("listitem")).toHaveLength(3);
});

it("renders no notice for an empty qualification list", () => {
  render(<DataQualifications notices={[]} />);
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
});
