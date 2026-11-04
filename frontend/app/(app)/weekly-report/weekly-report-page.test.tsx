import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import WeeklyReportPage from "./page";
import { demoWeeklyReport } from "@/lib/api/weekly-report-demo";
import { ApiError } from "@/lib/api/errors";

const mocks = vi.hoisted(() => ({ getWeeklyReport: vi.fn(), saveWeeklyTargets: vi.fn(), replace: vi.fn() }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({ getWeeklyReport: mocks.getWeeklyReport, saveWeeklyTargets: mocks.saveWeeklyTargets }), useDemoMode: () => ({ demo: true }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams("from=2026-10-19&weeks=2"), useRouter: () => ({ replace: mocks.replace }) }));

describe("Weekly report page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getWeeklyReport.mockResolvedValue(demoWeeklyReport("2026-10-19", 2));
  });

  it("summarises the selected week and keeps every field and source note reachable", async () => {
    render(<WeeklyReportPage />);
    await screen.findByRole("heading", { name: "2026-10-26 – 2026-11-01" });
    const summary = screen.getByRole("region", { name: "Selected week summary" });
    expect(within(summary).getByText("$97,000.00")).toBeVisible();
    expect(within(summary).getByText("4.5 stars")).toBeVisible();
    expect(screen.getByText(/fields unavailable/)).toBeVisible();
    expect(screen.getByRole("heading", { name: "All 85 fields & source notes" })).toBeVisible();
    const catalogue = screen.getByRole("region", { name: "All report fields" });
    for (const disclosure of catalogue.querySelectorAll("summary")) fireEvent.click(disclosure);
    expect(within(catalogue).getAllByRole("rowheader")).toHaveLength(85);
    expect(within(catalogue).getByText("Food COGS YTD %")).toBeVisible();
    fireEvent.click(within(catalogue).getByRole("button", { name: "Source notes for Food COGS YTD %, week of 2026-10-26" }));
    expect(await screen.findByText(/verified costs, classification and cumulative coverage/)).toBeVisible();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "2026-10-19 – 2026-10-25" }));
    expect(screen.getByRole("heading", { name: "2026-10-19 – 2026-10-25" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("First week (Monday)"), { target: { value: "2026-10-12" } });
    expect(mocks.replace).toHaveBeenCalledWith("/weekly-report?from=2026-10-12&weeks=2");
    fireEvent.change(screen.getByLabelText("Number of weeks"), { target: { value: "4" } });
    expect(mocks.replace).toHaveBeenCalledWith("/weekly-report?from=2026-10-19&weeks=4");
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
    fireEvent.click(screen.getByText("Wastage reasons & stock counts"));
    expect(screen.getByText("No reason recorded")).toBeVisible();
    expect(screen.getByRole("heading", { name: "Stock counts" })).toBeInTheDocument();
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
