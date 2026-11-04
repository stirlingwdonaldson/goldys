import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import WeeklyReportPage from "./page";
import { demoWeeklyReport } from "@/lib/api/weekly-report-demo";
import { ApiError } from "@/lib/api/errors";

const mocks = vi.hoisted(() => ({ getWeeklyReport: vi.fn(), saveWeeklyTargets: vi.fn(), replace: vi.fn(), push: vi.fn(), back: vi.fn(), search: "from=2026-10-19&weeks=2", history: [] as string[], listeners: new Set<() => void>() }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({ getWeeklyReport: mocks.getWeeklyReport, saveWeeklyTargets: mocks.saveWeeklyTargets }), useDemoMode: () => ({ demo: true }) }));
vi.mock("next/navigation", async () => {
  const { useSyncExternalStore } = await import("react");
  return { useSearchParams: () => new URLSearchParams(useSyncExternalStore(listener => { mocks.listeners.add(listener); return () => mocks.listeners.delete(listener); }, () => mocks.search)), useRouter: () => ({ replace: mocks.replace, push: mocks.push, back: mocks.back }) };
});

describe("Weekly report page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.search = "from=2026-10-19&weeks=2";
    mocks.history = [];
    const update = (url: string) => { mocks.search = url.split("?")[1] ?? ""; mocks.listeners.forEach(listener => listener()); };
    mocks.replace.mockImplementation(update);
    mocks.push.mockImplementation((url: string) => { mocks.history.push(mocks.search); update(url); });
    mocks.back.mockImplementation(() => update(`?${mocks.history.pop() ?? "from=2026-10-19&weeks=2"}`));
    mocks.getWeeklyReport.mockResolvedValue(demoWeeklyReport("2026-10-19", 2));
  });

  it("summarises the selected week and keeps every field and source note reachable", async () => {
    render(<WeeklyReportPage />);
    await screen.findByRole("heading", { name: "2026-10-26 – 2026-11-01" });
    const summary = screen.getByRole("region", { name: "Selected week summary" });
    expect(within(summary).getByText("$97,000.00")).toBeVisible();
    expect(within(summary).getByText("4.5 stars")).toBeVisible();
    expect(screen.getByText(/fields unavailable/)).toBeVisible();
    expect(document.querySelector("details")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /All report fields/ }));
    const detail = await screen.findByRole("dialog", { name: "Report detail" });
    expect(mocks.push).toHaveBeenCalledWith(expect.stringContaining("reportPanel=details"), { scroll: false });
    let fields = 0;
    for (const tab of within(detail).getAllByRole("tab").slice(0, 6)) {
      fireEvent.mouseDown(tab, { button: 0, ctrlKey: false });
      fields += within(detail).getAllByRole("rowheader").length;
    }
    expect(fields).toBe(85);
    fireEvent.mouseDown(within(detail).getByRole("tab", { name: /Cost of goods/ }), { button: 0, ctrlKey: false });
    expect(within(detail).getByText("Food COGS YTD %")).toBeVisible();
    expect(within(detail).getByRole("button", { name: "Source notes for Food COGS YTD %, week of 2026-10-26" })).toBeVisible();
    fireEvent.click(within(detail).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Report detail" })).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "2026-10-19 – 2026-10-25" }));
    expect(screen.getByRole("heading", { name: "2026-10-19 – 2026-10-25" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("First week (Monday)"), { target: { value: "2026-10-12" } });
    expect(mocks.replace).toHaveBeenLastCalledWith(expect.stringContaining("from=2026-10-12"));
    fireEvent.change(screen.getByLabelText("Number of weeks"), { target: { value: "4" } });
    expect(mocks.replace).toHaveBeenLastCalledWith(expect.stringContaining("weeks=4"));
  });

  it("reopens a shared target panel for its selected week and closes without changing scope", async () => {
    mocks.search = "from=2026-10-19&weeks=2&week=2026-10-19&reportPanel=targets";
    render(<WeeklyReportPage />);
    const panel = await screen.findByRole("dialog", { name: "Weekly targets" });
    expect(within(panel).getByText(/Effective from 2026-10-19/)).toBeVisible();
    fireEvent.click(within(panel).getByRole("button", { name: "Close" }));
    expect(mocks.replace).toHaveBeenCalledWith("/weekly-report?from=2026-10-19&weeks=2&week=2026-10-19", { scroll: false });
    await waitFor(() => expect(screen.getByRole("button", { name: "Edit weekly targets" })).toHaveFocus());
  });

  it("downloads the loaded snapshot without refetching data", async () => {
    const create = vi.fn(() => "blob:test");
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    render(<WeeklyReportPage />);
    const button = await screen.findByRole("button", { name: "Download Excel" });
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button);
    expect(create).toHaveBeenCalledWith(expect.any(Blob));
    expect(click).toHaveBeenCalledOnce();
    expect(mocks.getWeeklyReport).toHaveBeenCalledOnce();
    click.mockRestore();
    vi.unstubAllGlobals();
  });

  it("shows missing reasons and stock counts, saves percent targets as fractions and refreshes", async () => {
    mocks.saveWeeklyTargets.mockResolvedValue([]);
    render(<WeeklyReportPage />);
    await screen.findByRole("heading", { name: "2026-10-26 – 2026-11-01" });
    fireEvent.click(screen.getByRole("button", { name: /Wastage reasons/ }));
    expect(screen.getByText("No reason recorded")).toBeVisible();
    fireEvent.mouseDown(screen.getByRole("tab", { name: /Stock counts/ }), { button: 0, ctrlKey: false });
    expect(screen.getByRole("heading", { name: "Stock counts" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    fireEvent.click(screen.getByText("Edit weekly targets"));
    fireEvent.change(screen.getByLabelText("Beverage wastage target % (%)"), { target: { value: "0.5" } });
    fireEvent.click(screen.getByRole("button", { name: "Save targets" }));
    await screen.findByText("Targets saved.");
    expect(mocks.saveWeeklyTargets).toHaveBeenCalledWith({ effectiveFrom: "2026-10-26", values: { beverage_wastage_target_percent: 0.005 } });
    expect(mocks.getWeeklyReport).toHaveBeenCalledTimes(2);
  });

  it("shows target write denials without reporting a successful save", async () => {
    mocks.saveWeeklyTargets.mockRejectedValue(new ApiError("NOT_PERMITTED", "Target editing is not permitted"));
    render(<WeeklyReportPage />);
    await screen.findByRole("heading", { name: "2026-10-26 – 2026-11-01" });
    fireEvent.click(screen.getByText("Edit weekly targets"));
    fireEvent.change(screen.getByLabelText("Total sales target ($)"), { target: { value: "130000" } });
    fireEvent.click(screen.getByRole("button", { name: "Save targets" }));
    expect(await screen.findByText("Target editing is not permitted")).toBeInTheDocument();
    expect(screen.queryByText("Targets saved.")).not.toBeInTheDocument();
  });

  it("shows permission denial explicitly and disables download", async () => {
    mocks.getWeeklyReport.mockRejectedValue(new ApiError("NOT_PERMITTED", "Inventory cost access required"));
    render(<WeeklyReportPage />);
    expect(await screen.findByText("You don't have access to the weekly report")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download Excel" })).toBeDisabled();
  });
});
