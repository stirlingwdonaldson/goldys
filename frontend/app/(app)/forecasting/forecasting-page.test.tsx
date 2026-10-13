import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, within, act } from "@testing-library/react";
import ForecastingPage from "./page";
import type { ForecastComparison, ForecastMetric, ForecastRun } from "@/lib/api/types";

const metrics: ForecastMetric[] = [
  { metric: "sales.gross", name: "Gross sales", unit: "AUD", sourceDomain: "resolved_daily_sales", requiredPermission: "reconciliation.sales" },
  { metric: "reservations.bookings", name: "Bookings", unit: "count", sourceDomain: "resolved_reservation_day", requiredPermission: "reservations.metrics" },
  { metric: "reservations.covers", name: "Covers", unit: "count", sourceDomain: "resolved_reservation_day", requiredPermission: "reservations.metrics" },
  { metric: "labour.scheduled_hours", name: "Scheduled hours", unit: "hours", sourceDomain: "resolved_labour_day", requiredPermission: "labour.hours" },
];

const succeededRun: ForecastRun = {
  id: "fc-2",
  status: "SUCCEEDED",
  targetMetric: "sales.gross",
  covariates: ["reservations.bookings", "reservations.covers", "labour.scheduled_hours"],
  historyFrom: "2025-10-05",
  historyTo: "2026-10-05",
  horizonDays: 7,
  createdAt: "2026-10-06T02:00:00Z",
  series: [
    { date: "2026-10-06", median: 10000, p10: 9400, p90: 10600 },
    { date: "2026-10-07", median: 10500, p10: 9900, p90: 11100 },
  ],
  errorMessage: null,
  modelVersion: "timesfm-1.0",
};

const queuedRun: ForecastRun = {
  id: "fc-1",
  status: "QUEUED",
  targetMetric: "sales.gross",
  covariates: ["reservations.bookings", "reservations.covers", "labour.scheduled_hours"],
  historyFrom: "2025-10-06",
  historyTo: "2026-10-06",
  horizonDays: 14,
  createdAt: "2026-10-06T01:30:00Z",
  series: null,
  errorMessage: null,
  modelVersion: null,
};

const comparison: ForecastComparison = {
  runId: "fc-2",
  mape: 0.0412,
  mae: 512.4,
  points: [
    { date: "2026-10-06", predictedMedian: 10000, actual: 9254.1 },
    { date: "2026-10-07", predictedMedian: 10500, actual: null },
  ],
};

const mocks = vi.hoisted(() => {
  const api = {
    listForecastMetrics: vi.fn(),
    createForecastRun: vi.fn(),
    getForecastRun: vi.fn(),
    listForecastRuns: vi.fn(),
    getForecastComparison: vi.fn(),
  };
  return { api };
});

vi.mock("@/lib/demo-mode", () => ({
  useApi: () => mocks.api,
}));

// The screen renders the fixed revenue target and its three predictors once the metric
// list has loaded; await all of them before driving the run flow.
async function renderLoaded() {
  render(<ForecastingPage />);
  await screen.findByText("Gross sales — resolved_daily_sales · AUD");
  await screen.findByText("Bookings — resolved_reservation_day · count");
  await screen.findByText("Covers — resolved_reservation_day · count");
  await screen.findByText("Scheduled hours — resolved_labour_day · hours");
}

describe("ForecastingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.api.listForecastMetrics.mockResolvedValue(metrics);
    mocks.api.listForecastRuns.mockResolvedValue([succeededRun, queuedRun]);
    mocks.api.createForecastRun.mockResolvedValue({ ...queuedRun, id: "fc-new" });
    mocks.api.getForecastRun.mockResolvedValue(succeededRun);
    mocks.api.getForecastComparison.mockResolvedValue(comparison);
  });

  it("renders the fixed revenue target and predictors with no way to change them", async () => {
    await renderLoaded();

    // The target is locked to revenue and shown read-only.
    expect(screen.getByText("Gross sales — resolved_daily_sales · AUD")).toBeInTheDocument();
    // The three predictors are listed, also read-only.
    expect(screen.getByText("Bookings — resolved_reservation_day · count")).toBeInTheDocument();
    expect(screen.getByText("Covers — resolved_reservation_day · count")).toBeInTheDocument();
    expect(screen.getByText("Scheduled hours — resolved_labour_day · hours")).toBeInTheDocument();

    // There is no target dropdown or covariate checkbox to change.
    expect(screen.queryByRole("combobox")).toBeNull();
    expect(screen.queryByRole("checkbox")).toBeNull();
  });

  it("keeps the horizon changeable", async () => {
    await renderLoaded();

    expect(screen.getByRole("button", { name: "7 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "14 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "30 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "90 days" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "30 days" }));
    expect(screen.getByRole("button", { name: "30 days" })).toHaveAttribute("aria-pressed", "true");
  });

  it("runs a forecast with the fixed configuration and renders the chart plus the runs list", async () => {
    await renderLoaded();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Run forecast" }));
    });

    expect(mocks.api.createForecastRun).toHaveBeenCalledWith(
      expect.objectContaining({
        target: "sales.gross",
        covariates: ["reservations.bookings", "reservations.covers", "labour.scheduled_hours"],
        horizonDays: 7,
      }),
    );

    // The poll resolves to SUCCEEDED and the forecast chart renders.
    expect(await screen.findByTestId("forecast-chart")).toBeInTheDocument();

    const runs = await screen.findByTestId("runs-list");
    expect(within(runs).getByText("SUCCEEDED")).toBeInTheDocument();
    expect(within(runs).getByText("QUEUED")).toBeInTheDocument();
  });

  it("renders the error message when a run fails", async () => {
    mocks.api.getForecastRun.mockResolvedValue({
      ...queuedRun,
      id: "fc-new",
      status: "FAILED",
      errorMessage: "No history for the target metric",
    });

    await renderLoaded();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Run forecast" }));
    });

    expect(await screen.findByText("No history for the target metric")).toBeInTheDocument();
  });

  it("opens a compare-to-actuals view for a succeeded run", async () => {
    await renderLoaded();

    const runs = await screen.findByTestId("runs-list");
    fireEvent.click(within(runs).getByRole("button", { name: "Compare" }));

    expect(await screen.findByText("Compare to actuals")).toBeInTheDocument();
    expect(await screen.findByText("MAPE 4.1%")).toBeInTheDocument();
    expect(screen.getByText("MAE 512.4")).toBeInTheDocument();
    expect(screen.getByText("9,254")).toBeInTheDocument();
  });
});
