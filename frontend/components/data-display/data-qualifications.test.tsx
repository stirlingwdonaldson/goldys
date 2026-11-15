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
    const toggle = screen.getByRole("switch", { name: "Show incomplete data" });
    expect(toggle).toHaveAttribute("aria-checked", "false");
    fireEvent.click(toggle);
    expect(screen.getByRole("status")).toHaveTextContent("Incomplete — known subtotals only");
    expect(screen.getByText(/Ratio unavailable/)).toBeInTheDocument();
    expect(localStorage.getItem("goldys-show-incomplete-notices")).toBe("true");
    unmount();
    render(<Example />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("switch", { name: "Show incomplete data" }));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(localStorage.getItem("goldys-show-incomplete-notices")).toBe("false");
  });

  it("keeps the switch usable when browser storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("Unavailable"); });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("Unavailable"); });
    render(<Example />);
    fireEvent.click(screen.getByRole("switch", { name: "Show incomplete data" }));
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

it("keeps mixed interpretation notices visible while replacing only incomplete content", () => {
  render(<DataQualifications label="Invoice subtotal" scope="INV-1 · 1 November 2026" notices={["Incomplete: missing invoice lines", "Excludes GST", "Identity needs review", "Filtered to CTB"]}><span>$123 partial</span></DataQualifications>);
  expect(screen.queryByText("$123 partial")).not.toBeInTheDocument();
  expect(screen.getByText("Excludes GST")).toBeVisible();
  expect(screen.getByText("Identity needs review")).toBeVisible();
  expect(screen.getByRole("button", { name: /Invoice subtotal: incomplete/ })).toBeVisible();
});

it("opens scoped evidence directly by keyboard-equivalent button activation", async () => {
  render(<DataQualifications label="Invoice subtotal" scope="INV-1 · 1 November 2026" notices={["Incomplete: missing invoice lines"]}><span>$123 partial</span></DataQualifications>);
  fireEvent.click(screen.getByRole("button", { name: /Invoice subtotal: incomplete/ }));
  expect(await screen.findByRole("dialog")).toHaveTextContent("INV-1 · 1 November 2026");
  expect(screen.getByRole("dialog")).toHaveTextContent("$123 partial");
  expect(new URLSearchParams(window.location.search).get("issue")).toBe("Invoice subtotal:INV-1 · 1 November 2026");
});

it("keeps complete zero values and access warnings visible", () => {
  render(<DataQualifications notices={["Permission denied for wages"]}><span>$0.00</span></DataQualifications>);
  expect(screen.getByText("$0.00")).toBeVisible();
  expect(screen.getByText("Permission denied for wages")).toBeVisible();
});

it("classifies metric-prefixed coverage independently of tax and identity evidence", () => {
  render(<DataQualifications label="Labour" notices={["labour.hours.actual: Incomplete subtotal: missing dates", "labour.cost: Wages only", "Supplier relationship unresolved; no number-only attribution applied"]}><span>Partial labour total</span></DataQualifications>);
  expect(screen.queryByText("Partial labour total")).not.toBeInTheDocument();
  expect(screen.getByText("labour.cost: Wages only")).toBeInTheDocument();
  expect(screen.getByText("Supplier relationship unresolved; no number-only attribution applied")).toBeInTheDocument();
});
