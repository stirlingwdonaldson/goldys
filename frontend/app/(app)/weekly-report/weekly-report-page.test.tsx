import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  it("shows all fields and unavailable states, supports week selection and URL filters", async () => {
    render(<WeeklyReportPage />);
    await screen.findByRole("heading", { name: "2026-10-26 – 2026-11-01" });
    expect(screen.getByText("Food sales ex GST")).toBeInTheDocument();
    expect(screen.getAllByText("Unavailable").length).toBeGreaterThan(30);
    expect(screen.getByText("Food COGS YTD %")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "2026-10-19 – 2026-10-25" }));
    expect(screen.getByRole("heading", { name: "2026-10-19 – 2026-10-25" })).toBeInTheDocument();
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
    expect(await screen.findByText("No reason recorded")).toBeInTheDocument();
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
    await screen.findByRole("heading", { name: "Stock counts" });
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
