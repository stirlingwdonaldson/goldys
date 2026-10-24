import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DataQualifications } from "./data-qualifications";
import { IncompleteNoticesProvider } from "@/lib/incomplete-notices";
import { IncompleteNoticesToggle } from "@/components/settings/incomplete-notices-toggle";

function Example() {
  return <IncompleteNoticesProvider>
    <IncompleteNoticesToggle />
    <DataQualifications notices={["2 day(s) unresolved", "Ratio unavailable: missing inputs have no validated bound"]} />
  </IncompleteNoticesProvider>;
}

afterEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

describe("DataQualifications", () => {
  it("hides incomplete cards by default and persists the Settings choice across remounts", () => {
    const { unmount } = render(<Example />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: "Show incomplete data notices" });
    expect(toggle).toHaveAttribute("aria-checked", "false");
    fireEvent.click(toggle);
    expect(screen.getByRole("status")).toHaveTextContent("Incomplete — known subtotals only");
    expect(screen.getByText(/Ratio unavailable/)).toBeInTheDocument();
    expect(localStorage.getItem("goldys-show-incomplete-notices")).toBe("true");
    unmount();
    render(<Example />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("switch", { name: "Show incomplete data notices" }));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(localStorage.getItem("goldys-show-incomplete-notices")).toBe("false");
  });

  it("keeps the switch usable when browser storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("Unavailable"); });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("Unavailable"); });
    render(<Example />);
    fireEvent.click(screen.getByRole("switch", { name: "Show incomplete data notices" }));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("keeps other qualification cards visible", () => {
    render(<DataQualifications notices={["Wages only"]} />);
    expect(screen.getByRole("status")).toHaveTextContent("Wages only");
    expect(screen.queryByText(/Incomplete/)).not.toBeInTheDocument();
  });
});

it("respects an existing enabled preference and deduplicates mixed notices", () => {
  localStorage.setItem("goldys-show-incomplete-notices", "true");
  render(<IncompleteNoticesProvider><DataQualifications notices={["Partial coverage", "Wages only", "Wages only"]} /></IncompleteNoticesProvider>);
  expect(screen.getByRole("status")).toHaveTextContent("Partial coverage");
  expect(screen.getAllByRole("listitem")).toHaveLength(2);
});

it("renders nothing for empty notices", () => {
  render(<DataQualifications notices={[]} />);
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
});
